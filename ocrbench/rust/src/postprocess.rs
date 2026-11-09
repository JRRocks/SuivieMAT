//! Portage du traitement applique par l'application a chaque texte reconnu.
//!
//! Chaque fonction suit sa jumelle Kotlin ligne a ligne (`ScanValue`,
//! `RegexRuleEngine.extract`, `ScanConsensus`, `ScanDebouncer`) : le banc verifie que
//! les deux versions rendent exactement les memes valeurs avant de comparer leurs temps.

use regex::Regex;

/// `RegexRuleEngine.MAX_MATCHES_PER_FRAME`.
const MAX_MATCHES_PER_FRAME: usize = 50;
/// `ScanConsensus.DEFAULT_TTL_MS`.
const CONSENSUS_TTL_MS: i64 = 800;
/// `ScanDebouncer.DEFAULT_WINDOW_MS`.
const DEBOUNCE_WINDOW_MS: i64 = 1_500;
/// `ScanDebouncer.PRUNE_THRESHOLD`.
const DEBOUNCE_PRUNE_THRESHOLD: usize = 64;

/// `ScanValue.normalize` : espaces de bord retires, espaces internes reduits a un seul.
pub fn normalize(raw: &str) -> String {
    let mut out = String::with_capacity(raw.len());
    for part in raw.split_whitespace() {
        if !out.is_empty() {
            out.push(' ');
        }
        out.push_str(part);
    }
    out
}

/// `ScanValue.key` : la valeur normalisee, casse ignoree.
pub fn key(raw: &str) -> String {
    normalize(raw).to_uppercase()
}

/// `RegexRuleEngine.extract`.
pub fn extract(text: &str, regex: &Regex, group_index: usize) -> Vec<String> {
    if text.trim().is_empty() {
        return Vec::new();
    }
    // Ensemble ordonne, comme le LinkedHashSet de la version Kotlin : quelques valeurs
    // par image, un parcours lineaire coute moins qu'un hachage.
    let mut results: Vec<String> = Vec::new();
    let mut count = 0;
    for captures in regex.captures_iter(text) {
        count += 1;
        if count > MAX_MATCHES_PER_FRAME {
            return Vec::new();
        }
        let Some(found) = captures.get(group_index) else { continue };
        let value = normalize(found.as_str());
        if !value.is_empty() && !results.contains(&value) {
            results.push(value);
        }
    }
    results
}

struct Counter {
    count: u32,
    last_seen: i64,
    first_raw_value: String,
    coexisted_with: Vec<String>,
}

/// `ScanConsensus` : une valeur n'est validee qu'apres `threshold` lectures identiques.
pub struct Consensus {
    threshold: u32,
    ambiguity_guard: bool,
    /// Ordre d'insertion conserve, comme le LinkedHashMap d'origine : il decide quelle
    /// valeur voisine est trouvee en premier par la garde d'ambiguite.
    counters: Vec<(String, Counter)>,
}

impl Consensus {
    pub fn new(threshold: u32, ambiguity_guard: bool) -> Self {
        Self { threshold, ambiguity_guard, counters: Vec::new() }
    }

    fn position(&self, key: &str) -> Option<usize> {
        self.counters.iter().position(|(k, _)| k == key)
    }

    pub fn submit(&mut self, candidates: &[String], now: i64) -> Vec<String> {
        self.counters.retain(|(_, c)| now - c.last_seen < CONSENSUS_TTL_MS);

        let mut frame_keys: Vec<String> = Vec::with_capacity(candidates.len());
        let mut newly_created: Vec<String> = Vec::new();
        for candidate in candidates {
            let key = key(candidate);
            if key.is_empty() {
                continue;
            }
            match self.position(&key) {
                None => {
                    self.counters.push((
                        key.clone(),
                        Counter {
                            count: 1,
                            last_seen: now,
                            first_raw_value: candidate.clone(),
                            coexisted_with: Vec::new(),
                        },
                    ));
                    newly_created.push(key.clone());
                }
                Some(index) => {
                    let counter = &mut self.counters[index].1;
                    counter.count += 1;
                    counter.last_seen = now;
                }
            }
            if !frame_keys.contains(&key) {
                frame_keys.push(key);
            }
        }

        if self.ambiguity_guard {
            self.mark_coexistence(&frame_keys);
            self.resolve_ambiguity(&newly_created);
        }

        let mut validated = Vec::new();
        for key in &frame_keys {
            let Some(index) = self.position(key) else { continue };
            if self.counters[index].1.count >= self.threshold {
                validated.push(self.counters.remove(index).1.first_raw_value);
            }
        }
        validated
    }

    pub fn peak_progress(&self) -> f32 {
        let Some(best) = self.counters.iter().map(|(_, c)| c.count).max() else { return 0.0 };
        (best as f32 / self.threshold as f32).clamp(0.0, 1.0)
    }

    pub fn reset(&mut self) {
        self.counters.clear();
    }

    fn mark_coexistence(&mut self, frame_keys: &[String]) {
        if frame_keys.len() < 2 {
            return;
        }
        for i in 0..frame_keys.len() {
            for j in (i + 1)..frame_keys.len() {
                self.note_coexistence(&frame_keys[i], &frame_keys[j]);
                self.note_coexistence(&frame_keys[j], &frame_keys[i]);
            }
        }
    }

    fn note_coexistence(&mut self, of: &str, with: &str) {
        if let Some(index) = self.position(of) {
            let seen = &mut self.counters[index].1.coexisted_with;
            if !seen.iter().any(|k| k == with) {
                seen.push(with.to_string());
            }
        }
    }

    fn resolve_ambiguity(&mut self, newly_created: &[String]) {
        for key in newly_created {
            let Some(index) = self.position(key) else { continue };
            let conflict = self.counters.iter().position(|(other, counter)| {
                other != key
                    && within_edit_distance_one(key, other)
                    && !self.counters[index].1.coexisted_with.contains(other)
                    && !counter.coexisted_with.contains(key)
            });
            if let Some(conflict) = conflict {
                // Le plus grand indice d'abord : retirer l'un ne decale pas l'autre.
                let (first, second) = if conflict > index { (conflict, index) } else { (index, conflict) };
                self.counters.remove(first);
                self.counters.remove(second);
            }
        }
    }
}

/// `ScanConsensus.withinEditDistanceOne` : une substitution, insertion ou suppression.
pub fn within_edit_distance_one(a: &str, b: &str) -> bool {
    if a == b {
        return false;
    }
    let a: Vec<char> = a.chars().collect();
    let b: Vec<char> = b.chars().collect();
    let length_diff = a.len() as i64 - b.len() as i64;
    if !(-1..=1).contains(&length_diff) {
        return false;
    }
    if a.len() == b.len() {
        return a.iter().zip(&b).filter(|(x, y)| x != y).count() == 1;
    }
    let (shorter, longer) = if a.len() < b.len() { (&a, &b) } else { (&b, &a) };
    let (mut i, mut j, mut skipped) = (0, 0, false);
    while i < shorter.len() && j < longer.len() {
        if shorter[i] == longer[j] {
            i += 1;
            j += 1;
        } else {
            if skipped {
                return false;
            }
            skipped = true;
            j += 1;
        }
    }
    true
}

/// `ScanDebouncer` : une valeur validee n'est pas reprise avant la fin de la fenetre.
#[derive(Default)]
pub struct Debouncer {
    last_seen: Vec<(String, i64)>,
}

impl Debouncer {
    pub fn accept(&mut self, raw: &str, now: i64) -> bool {
        let key = key(raw);
        if key.is_empty() {
            return false;
        }
        if self.last_seen.len() >= DEBOUNCE_PRUNE_THRESHOLD {
            self.last_seen.retain(|(_, seen)| now - seen < DEBOUNCE_WINDOW_MS);
        }
        match self.last_seen.iter_mut().find(|(k, _)| *k == key) {
            Some((_, seen)) => {
                if now - *seen < DEBOUNCE_WINDOW_MS {
                    return false;
                }
                *seen = now;
            }
            None => self.last_seen.push((key, now)),
        }
        true
    }

    pub fn reset(&mut self) {
        self.last_seen.clear();
    }
}

/// Le chemin de `ScanViewModel.onTextRecognized`, en scan de dossier : formule,
/// consensus, puis anti-rebond.
pub struct Pipeline {
    regex: Regex,
    group_index: usize,
    consensus: Consensus,
    debouncer: Debouncer,
}

impl Pipeline {
    pub fn new(pattern: &str, group_index: usize, threshold: u32, ambiguity_guard: bool) -> Result<Self, regex::Error> {
        Ok(Self {
            regex: Regex::new(pattern)?,
            group_index,
            consensus: Consensus::new(threshold, ambiguity_guard),
            debouncer: Debouncer::default(),
        })
    }

    /// Renvoie les valeurs a enregistrer pour cette image, et l'avancement du consensus.
    pub fn process(&mut self, text: &str, now: i64) -> (Vec<String>, f32) {
        let matches = extract(text, &self.regex, self.group_index);
        if matches.is_empty() {
            // L'application sort avant le consensus : son avancement n'est pas relu.
            return (Vec::new(), -1.0);
        }
        let confirmed = self.consensus.submit(&matches, now);
        let progress = self.consensus.peak_progress();
        let fresh = confirmed.into_iter().filter(|v| self.debouncer.accept(v, now)).collect();
        (fresh, progress)
    }

    pub fn reset(&mut self) {
        self.consensus.reset();
        self.debouncer.reset();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalise_comme_scan_value() {
        assert_eq!(normalize("  BAT   001 \n"), "BAT 001");
        assert_eq!(key(" bat  001"), "BAT 001");
        assert_eq!(normalize("   "), "");
    }

    #[test]
    fn extrait_chaque_correspondance_une_seule_fois() {
        let regex = Regex::new(r"\b\d{2}-\d{6}\b").unwrap();
        let text = "S/N 12-345678\nMODEL A5 12-345678 et 98-765432";
        assert_eq!(extract(text, &regex, 0), vec!["12-345678", "98-765432"]);
        assert!(extract("   ", &regex, 0).is_empty());
    }

    #[test]
    fn extrait_le_groupe_demande() {
        let regex = Regex::new(r"SN[: ]+(\d{5})").unwrap();
        assert_eq!(extract("SN: 40718 SN 40719", &regex, 1), vec!["40718", "40719"]);
        assert!(extract("SN: 40718", &regex, 2).is_empty());
    }

    #[test]
    fn valide_apres_le_seuil_puis_verrouille() {
        let mut pipeline = Pipeline::new(r"\b\d{5}\b", 0, 5, true).unwrap();
        let mut validated = Vec::new();
        for frame in 0..8 {
            validated.extend(pipeline.process("TYPE DVA 40718", frame * 100).0);
        }
        // Cinq lectures valident ; les trois suivantes relancent un compteur, encore
        // sous le seuil.
        assert_eq!(validated, vec!["40718"]);
    }

    #[test]
    fn deux_lectures_a_une_lettre_pres_s_annulent() {
        let mut consensus = Consensus::new(3, true);
        assert!(consensus.submit(&["40718".to_string()], 0).is_empty());
        assert!(consensus.submit(&["40716".to_string()], 100).is_empty());
        assert_eq!(consensus.peak_progress(), 0.0);
    }

    #[test]
    fn deux_valeurs_vues_ensemble_ne_s_annulent_pas() {
        let mut consensus = Consensus::new(2, true);
        let frame = vec!["40718".to_string(), "40719".to_string()];
        assert!(consensus.submit(&frame, 0).is_empty());
        assert_eq!(consensus.submit(&frame, 100), frame);
    }

    #[test]
    fn distance_d_edition_d_un_caractere() {
        assert!(within_edit_distance_one("ABC", "ABD"));
        assert!(within_edit_distance_one("ABC", "ABCD"));
        assert!(within_edit_distance_one("ABCD", "ACD"));
        assert!(!within_edit_distance_one("ABC", "ABC"));
        assert!(!within_edit_distance_one("ABC", "AXY"));
    }

    #[test]
    fn l_anti_rebond_laisse_repasser_apres_la_fenetre() {
        let mut debouncer = Debouncer::default();
        assert!(debouncer.accept("40718", 0));
        assert!(!debouncer.accept("40718", 1_499));
        assert!(debouncer.accept("40718", 1_500));
    }
}
