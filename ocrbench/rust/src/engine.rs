//! Moteur de lecture entierement en Rust : ocrs (detection puis reconnaissance par deux
//! reseaux de neurones, executes par RTen).

use std::path::Path;

use ocrs::{ImageSource, OcrEngine, OcrEngineParams, OcrInput};
use rten::Model;
use rten_imageproc::{find_contours, min_area_rect, simplify_polygon, RetrievalMode, RotatedRect};
use rten_tensor::prelude::*;

/// Seuil au-dela duquel un pixel de la carte de detection compte comme du texte.
///
/// Le moteur est livre avec 0,2. Avec les modeles publies, le fond d'une image sort a
/// environ 0,20 (mesure aussi avec ONNX Runtime, donc propre au modele) : au seuil
/// d'origine les trois quarts de l'image sont pris pour du texte et plus rien n'est lu.
/// Le texte, lui, depasse 0,5. Parmi 0,25, 0,3, 0,4 et 0,5 essayes sur les etiquettes du
/// banc, 0,4 lit le plus de valeurs justes : ce reglage est donc en faveur du moteur,
/// c'est la condition pour que la comparaison ait un sens.
pub const DETECTION_THRESHOLD: f32 = 0.4;

/// Parametres de `ocrs::detection`, non exposes par la bibliotheque.
const EXPAND_DISTANCE: f32 = 3.0;
const MIN_WORD_AREA: f32 = 100.0;

pub struct Engine {
    inner: OcrEngine,
    threshold: f32,
}

impl Engine {
    pub fn load(detection_model: &Path, recognition_model: &Path) -> Result<Self, String> {
        Self::load_with_threshold(detection_model, recognition_model, DETECTION_THRESHOLD)
    }

    pub fn load_with_threshold(
        detection_model: &Path,
        recognition_model: &Path,
        threshold: f32,
    ) -> Result<Self, String> {
        let detection = Model::load_file(detection_model).map_err(|e| format!("detection : {e}"))?;
        let recognition = Model::load_file(recognition_model).map_err(|e| format!("reconnaissance : {e}"))?;
        let inner = OcrEngine::new(OcrEngineParams {
            detection_model: Some(detection),
            recognition_model: Some(recognition),
            ..Default::default()
        })
        .map_err(|e| format!("moteur : {e}"))?;
        Ok(Self { inner, threshold })
    }

    /// Lit une image en niveaux de gris, un octet par pixel : le plan Y d'une image de
    /// camera, sans aucune conversion, soit le cas le plus favorable a ce moteur.
    pub fn read_luma(&self, luma: &[u8], width: u32, height: u32) -> Result<String, String> {
        let source = ImageSource::from_bytes(luma, (width, height)).map_err(|e| e.to_string())?;
        let input = self.inner.prepare_input(source).map_err(|e| e.to_string())?;
        let words = self.detect_words(&input)?;
        let lines = self.inner.find_text_lines(&input, &words);
        let texts = self.inner.recognize_text(&input, &lines).map_err(|e| e.to_string())?;
        Ok(texts.into_iter().flatten().map(|line| line.to_string()).collect::<Vec<_>>().join("\n"))
    }

    /// `OcrEngine::detect_words`, au seuil pres : la bibliotheque ne laisse pas regler le
    /// sien, mais expose la carte de detection et les fonctions qui la decoupent.
    fn detect_words(&self, input: &OcrInput) -> Result<Vec<RotatedRect>, String> {
        let mask = self.inner.detect_text_pixels(input).map_err(|e| e.to_string())?;
        let binary = mask.map(|probability| *probability > self.threshold);
        Ok(find_contours(binary.view(), RetrievalMode::External)
            .iter()
            .filter_map(|polygon| {
                let points: Vec<_> = polygon.iter().map(|point| point.to_f32()).collect();
                let simplified = simplify_polygon(&points, 2.0);
                min_area_rect(&simplified).map(|mut rect| {
                    rect.resize(rect.width() + 2.0 * EXPAND_DISTANCE, rect.height() + 2.0 * EXPAND_DISTANCE);
                    rect
                })
            })
            .filter(|rect| rect.area() >= MIN_WORD_AREA)
            .collect())
    }
}
