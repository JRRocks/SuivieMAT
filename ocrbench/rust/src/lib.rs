//! Banc de mesure OCR de SuivieMAT, cote Rust.
//!
//! Deux choses y sont portees, pour repondre a la question "serait-ce plus rapide en
//! Rust ?" par une mesure et non par une opinion :
//!
//! - [`postprocess`] : le traitement que l'application fait en Kotlin sur chaque texte
//!   reconnu (extraction par la formule, consensus, anti-rebond), a l'identique ;
//! - [`engine`] : un moteur de lecture entierement ecrit en Rust (ocrs), a comparer a
//!   ML Kit, le moteur actuel, lui-meme ecrit en C++.
//!
//! Rien de ce code n'entre dans l'application : il n'est charge que par le test
//! instrumente du banc (voir ocrbench/README.md).

pub mod engine;
pub mod postprocess;

mod jni_bridge;
