//! Lit sur le poste les images generees par generate_samples.py avec le moteur Rust, et
//! compte les lectures justes comme le fera le banc sur l'emulateur.
//!
//! Sert a verifier le moteur et son integration avant de les envoyer sur l'emulateur ;
//! les temps affiches sont ceux du poste, pas ceux d'un telephone.
//!
//! cargo run --release --example host_ocr -- <detection> <reconnaissance> <dossier des images> [seuil]

use std::path::Path;
use std::time::Instant;

use ocrbench::engine::{Engine, DETECTION_THRESHOLD};
use ocrbench::postprocess::extract;
use regex::Regex;

/// Lecture minimale du manifest.json : le format est fixe, ecrit par generate_samples.py.
fn field<'a>(block: &'a str, name: &str) -> &'a str {
    let start = block.find(&format!("\"{name}\": \"")).expect("champ absent") + name.len() + 5;
    let end = start + block[start..].find("\",").or_else(|| block[start..].find("\"\n")).expect("fin de champ");
    &block[start..end]
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args: Vec<String> = std::env::args().skip(1).collect();
    if args.len() < 3 {
        return Err("usage : host_ocr <detection> <reconnaissance> <dossier> [seuil]".into());
    }
    let threshold = args.get(3).map(|t| t.parse()).transpose()?.unwrap_or(DETECTION_THRESHOLD);
    let engine = Engine::load_with_threshold(Path::new(&args[0]), Path::new(&args[1]), threshold)?;
    let folder = Path::new(&args[2]);
    let manifest = std::fs::read_to_string(folder.join("manifest.json"))?;

    let (mut exact, mut wrong, mut nothing, mut total_ms, mut count) = (0, 0, 0, 0.0, 0);
    for block in manifest.split("\"id\":").skip(1) {
        let pattern = field(block, "pattern").replace("\\\\", "\\");
        let expected = field(block, "expected");
        let file = field(block, "720");
        let image = image::open(folder.join(file))?.into_luma8();
        let (width, height) = image.dimensions();
        if count == 0 {
            let _ = engine.read_luma(image.as_raw(), width, height)?; // echauffement
        }
        let start = Instant::now();
        let text = engine.read_luma(image.as_raw(), width, height)?;
        let elapsed = start.elapsed().as_secs_f64() * 1000.0;
        let values = extract(&text, &Regex::new(&pattern)?, 0);
        let verdict = if values.iter().any(|v| v == expected) && values.len() == 1 {
            exact += 1;
            "juste"
        } else if values.is_empty() {
            nothing += 1;
            "rien"
        } else {
            wrong += 1;
            "FAUX"
        };
        total_ms += elapsed;
        count += 1;
        println!("{file} {:>9} attendu {expected:<14} lu {values:?} ({elapsed:.0} ms) {verdict}", field(block, "tier"));
    }
    println!(
        "seuil {threshold} : {exact} justes, {wrong} faux, {nothing} sans lecture sur {count} ; {:.0} ms en moyenne",
        total_ms / count as f64
    );
    Ok(())
}
