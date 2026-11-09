//! Pont JNI vers `com.scanlist.app.ocrbench.RustBridge`.
//!
//! Le pipeline est appele une fois par image, texte reconnu en argument : c'est le
//! decoupage qu'aurait une vraie integration, ML Kit rendant son texte cote Java. Le
//! cout du passage Java -> Rust (copie et conversion de la chaine) fait donc partie de
//! ce qui est mesure. `pipelineLoop` mesure a l'inverse le calcul seul, sans aucun
//! passage par image : la borne basse, inatteignable tant que le texte vient de Java.

use std::panic::{catch_unwind, AssertUnwindSafe};
use std::path::Path;
use std::sync::Mutex;
use std::time::Instant;

use jni::objects::{JByteArray, JClass, JFloatArray, JIntArray, JLongArray, JObject, JObjectArray, JString};
use jni::sys::{jboolean, jint, jlong, jlongArray, jobjectArray, jstring};
use jni::JNIEnv;

use crate::engine::Engine;
use crate::postprocess::Pipeline;

static LAST_ERROR: Mutex<String> = Mutex::new(String::new());

fn remember_error(message: String) {
    if let Ok(mut slot) = LAST_ERROR.lock() {
        *slot = message;
    }
}

fn java_string(env: &mut JNIEnv, value: &JString) -> Option<String> {
    env.get_string(value).ok().map(Into::into)
}

fn string_array(env: &mut JNIEnv, values: &[String]) -> jobjectArray {
    let Ok(array) = env.new_object_array(values.len() as i32, "java/lang/String", JObject::null()) else {
        return std::ptr::null_mut();
    };
    for (index, value) in values.iter().enumerate() {
        let Ok(item) = env.new_string(value) else { return std::ptr::null_mut() };
        if env.set_object_array_element(&array, index as i32, &item).is_err() {
            return std::ptr::null_mut();
        }
        // Reference locale liberee tout de suite : la table en compte peu.
        let _ = env.delete_local_ref(item);
    }
    array.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_scanlist_app_ocrbench_RustBridge_pipelineCreate<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    pattern: JString<'l>,
    group_index: jint,
    threshold: jint,
    ambiguity_guard: jboolean,
) -> jlong {
    let Some(pattern) = java_string(&mut env, &pattern) else { return 0 };
    match Pipeline::new(&pattern, group_index as usize, threshold as u32, ambiguity_guard != 0) {
        Ok(pipeline) => Box::into_raw(Box::new(pipeline)) as jlong,
        Err(error) => {
            remember_error(error.to_string());
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_scanlist_app_ocrbench_RustBridge_pipelineProcess<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    handle: jlong,
    text: JString<'l>,
    now_ms: jlong,
    progress_out: JFloatArray<'l>,
) -> jobjectArray {
    // SAFETY : `handle` vient de pipelineCreate et n'a pas ete detruit ; le banc
    // n'appelle un pipeline que depuis un seul fil.
    let pipeline = unsafe { &mut *(handle as *mut Pipeline) };
    let Some(text) = java_string(&mut env, &text) else { return std::ptr::null_mut() };
    let (fresh, progress) = pipeline.process(&text, now_ms);
    if progress >= 0.0 {
        let _ = env.set_float_array_region(&progress_out, 0, &[progress]);
    }
    if fresh.is_empty() {
        return std::ptr::null_mut();
    }
    string_array(&mut env, &fresh)
}

#[no_mangle]
pub extern "system" fn Java_com_scanlist_app_ocrbench_RustBridge_pipelineReset<'l>(
    _env: JNIEnv<'l>,
    _class: JClass<'l>,
    handle: jlong,
) {
    // SAFETY : voir pipelineProcess.
    unsafe { &mut *(handle as *mut Pipeline) }.reset();
}

#[no_mangle]
pub extern "system" fn Java_com_scanlist_app_ocrbench_RustBridge_pipelineDestroy<'l>(
    _env: JNIEnv<'l>,
    _class: JClass<'l>,
    handle: jlong,
) {
    if handle != 0 {
        // SAFETY : le handle n'est plus utilise apres cet appel.
        drop(unsafe { Box::from_raw(handle as *mut Pipeline) });
    }
}

/// Rejoue `repetitions` fois la suite d'images decrite par les trois tableaux, sans
/// repasser par Java, et renvoie [duree en nanosecondes, nombre de valeurs validees].
#[no_mangle]
pub extern "system" fn Java_com_scanlist_app_ocrbench_RustBridge_pipelineLoop<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    handles: JLongArray<'l>,
    frame_pipeline: JIntArray<'l>,
    frame_texts: JObjectArray<'l>,
    frame_times: JLongArray<'l>,
    repetitions: jint,
    period_ms: jlong,
) -> jlongArray {
    let null = std::ptr::null_mut();
    let Ok(handle_count) = env.get_array_length(&handles) else { return null };
    let Ok(frame_count) = env.get_array_length(&frame_texts) else { return null };
    let mut raw_handles = vec![0i64; handle_count as usize];
    let mut pipeline_of = vec![0i32; frame_count as usize];
    let mut times = vec![0i64; frame_count as usize];
    if env.get_long_array_region(&handles, 0, &mut raw_handles).is_err()
        || env.get_int_array_region(&frame_pipeline, 0, &mut pipeline_of).is_err()
        || env.get_long_array_region(&frame_times, 0, &mut times).is_err()
    {
        return null;
    }
    let mut texts = Vec::with_capacity(frame_count as usize);
    for index in 0..frame_count {
        let Ok(item) = env.get_object_array_element(&frame_texts, index) else { return null };
        let item = JString::from(item);
        let Some(text) = java_string(&mut env, &item) else { return null };
        // Une reference locale par image : liberee au fur et a mesure.
        let _ = env.delete_local_ref(item);
        texts.push(text);
    }

    // SAFETY : handles crees par pipelineCreate, tous distincts, utilises par ce seul fil.
    let mut pipelines: Vec<&mut Pipeline> =
        raw_handles.iter().map(|&h| unsafe { &mut *(h as *mut Pipeline) }).collect();
    for pipeline in pipelines.iter_mut() {
        pipeline.reset();
    }

    let mut validated: i64 = 0;
    let start = Instant::now();
    for repetition in 0..repetitions as i64 {
        let offset = repetition * period_ms;
        for frame in 0..frame_count as usize {
            let (fresh, _) = pipelines[pipeline_of[frame] as usize].process(&texts[frame], offset + times[frame]);
            validated += fresh.len() as i64;
        }
    }
    let elapsed = start.elapsed().as_nanos() as i64;

    let Ok(result) = env.new_long_array(2) else { return null };
    if env.set_long_array_region(&result, 0, &[elapsed, validated]).is_err() {
        return null;
    }
    result.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_scanlist_app_ocrbench_RustBridge_engineCreate<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    detection_model: JString<'l>,
    recognition_model: JString<'l>,
) -> jlong {
    let (Some(detection), Some(recognition)) =
        (java_string(&mut env, &detection_model), java_string(&mut env, &recognition_model))
    else {
        return 0;
    };
    // Un modele illisible ne doit pas emporter le processus du banc avec lui.
    let loaded = catch_unwind(|| Engine::load(Path::new(&detection), Path::new(&recognition)));
    match loaded {
        Ok(Ok(engine)) => Box::into_raw(Box::new(engine)) as jlong,
        Ok(Err(message)) => {
            remember_error(message);
            0
        }
        Err(_) => {
            remember_error("panique au chargement du moteur".to_string());
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_scanlist_app_ocrbench_RustBridge_lastError<'l>(
    env: JNIEnv<'l>,
    _class: JClass<'l>,
) -> jstring {
    let message = LAST_ERROR.lock().map(|m| m.clone()).unwrap_or_default();
    env.new_string(message).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_scanlist_app_ocrbench_RustBridge_engineRead<'l>(
    env: JNIEnv<'l>,
    _class: JClass<'l>,
    handle: jlong,
    luma: JByteArray<'l>,
    width: jint,
    height: jint,
) -> jstring {
    let null = std::ptr::null_mut();
    // SAFETY : `handle` vient de engineCreate et n'a pas ete detruit.
    let engine = unsafe { &*(handle as *const Engine) };
    let Ok(pixels) = env.convert_byte_array(&luma) else { return null };
    let read = catch_unwind(AssertUnwindSafe(|| engine.read_luma(&pixels, width as u32, height as u32)));
    match read {
        Ok(Ok(text)) => env.new_string(text).map(|s| s.into_raw()).unwrap_or(null),
        Ok(Err(message)) => {
            remember_error(message);
            null
        }
        Err(_) => {
            remember_error("panique pendant la lecture".to_string());
            null
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_scanlist_app_ocrbench_RustBridge_engineDestroy<'l>(
    _env: JNIEnv<'l>,
    _class: JClass<'l>,
    handle: jlong,
) {
    if handle != 0 {
        // SAFETY : le handle n'est plus utilise apres cet appel.
        drop(unsafe { Box::from_raw(handle as *mut Engine) });
    }
}
