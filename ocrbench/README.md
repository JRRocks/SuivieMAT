# Banc de mesure OCR

Outil de mesure, **hors de l'application** : rien de ce dossier n'entre dans l'APK de
SuivieMAT, et une compilation ordinaire ne le voit pas.

Il répond à une question de l'utilisateur (2026-11-09) : *le moteur de lecture OCR
serait-il plus performant s'il était codé en Rust, ou dans un autre langage ?* Les
résultats et la conclusion sont consignés dans `consignes/outils.md` § 2 bis.

## Ce qui est comparé

La lecture OCR de l'application tient en deux étages, et le banc mesure les deux :

| Étage | Aujourd'hui | Mis en face |
|---|---|---|
| **Reconnaissance** : de l'image au texte | ML Kit Text Recognition, bibliothèque native **C++** de Google, embarquée dans l'APK | **ocrs**, moteur OCR entièrement écrit en **Rust** (réseaux de neurones exécutés par RTen) |
| **Traitement** : du texte aux valeurs à enregistrer (formule regex, consensus, anti-rebond) | **Kotlin** (`RegexRuleEngine`, `ScanConsensus`, `ScanDebouncer`) | le même traitement porté en **Rust** ligne à ligne, appelé par JNI |

Les deux versions du traitement doivent rendre exactement les mêmes valeurs, image par
image : le banc le vérifie avant de comparer leurs temps, et échoue sinon.

## Les images

Aucune photo réelle n'existe hors du téléphone. `generate_samples.py` dessine 32
étiquettes dont la valeur est connue, posées sur un fond de carcasse, avec du texte
parasite et un faux code-barres autour, vues comme par la caméra tenue en portrait.
Quatre niveaux — net, moyen, difficile, limite — dégradent la taille du texte, l'angle,
le flou, le bruit, le reflet, le contraste et la compression. Chaque étiquette existe en
540p, 720p (la résolution d'analyse de l'application) et 1080p.

Une lecture n'est comptée **juste** que si la formule isole la valeur attendue et elle
seule ; une autre valeur est une lecture **fausse** (elle serait enregistrée comme une
ligne de trop), l'absence de valeur une image **sans lecture**.

## Lancer le banc

**Sur GitHub** (émulateur x86_64 accéléré, aucun matériel requis) : onglet *Actions* ›
*Banc OCR sur emulateur* › *Run workflow*. Il part aussi tout seul quand un fichier de
ce dossier change. Le rapport s'affiche dans le résumé de l'exécution.

**Sur un téléphone ou un émulateur branché**, depuis Linux ou macOS, avec le SDK, un NDK,
Rust et Python (Pillow 11.1.0) :

```bash
ANDROID_NDK_HOME=<ndk> ocrbench/prepare.sh arm64-v8a      # x86_64 pour un émulateur
./gradlew --init-script ocrbench/bench.init.gradle :app:assembleDebug :app:assembleDebugAndroidTest
ocrbench/run-on-device.sh
```

Le test s'installe à la place de l'application (même identifiant, même clé de debug) :
les dossiers du téléphone sont conservés.

## Les fichiers

| Fichier | Rôle |
|---|---|
| `generate_samples.py` | Dessine les étiquettes (déterministe, polices du dépôt) |
| `prepare.sh` | Images, modèles d'ocrs (empreinte vérifiée), bibliothèque Rust par ABI → `build/` |
| `bench.init.gradle` | Branche le banc sur `:app` sans toucher à son fichier de build |
| `androidTest/` | Le test instrumenté (`OcrBenchmark`) et le pont JNI (`RustBridge`) |
| `rust/` | Portage du traitement (`postprocess.rs`), moteur ocrs (`engine.rs`), pont JNI |
| `run-on-device.sh` | Installe, lance, extrait le rapport dans `build/results/rapport.md` |

`build/` n'est pas versionné.

## Limites à garder en tête

- **Un émulateur x86_64 n'est pas un Galaxy S25** (arm64). Les rapports entre moteurs
  donnent un ordre de grandeur ; les temps absolus ne se transposent pas. Le banc se
  relance tel quel sur le téléphone.
- **Les étiquettes sont dessinées**, pas photographiées : ni gravure, ni salissure, ni
  flou de bougé réel.
- **Le moteur Rust est réglé en sa faveur** : avec les modèles publiés, le fond d'une
  image sort au niveau du seuil de détection d'origine (0,2) et plus rien n'est lu ; le
  seuil est relevé à 0,4, le meilleur des quatre essayés sur ces étiquettes
  (`rust/src/engine.rs`).
- Le mode `debuggable` est retiré pour la mesure, et les deux paquets sont compilés à
  l'avance : le Kotlin est mesuré tel qu'il tourne dans l'APK livré.
