# Workflow — ScanList

> **Fichier de mémoire longue durée.** Architecture technique et suivi d'avancement.
> **Règle absolue :** mettre à jour le tableau d'avancement (§ 6) et le journal (§ 7)
> à chaque évolution majeure, et systématiquement avant/après un `/clear`.
>
> Dernière mise à jour : 2026-10-28 — *Correctif : ouverture sur Nouveau scan (route `new-scan`),
> chemins locaux corrigés, branche SuivieMAT fusionnée dans `main`*

---

## 1. Architecture générale

**MVVM + Repository**, une seule `Activity`, navigation intégralement Compose.

```
┌─────────────────────────────────────────────────────────┐
│  UI  (Jetpack Compose)                                   │
│  MainActivity → NavHost → écrans (Menu, Scan, Détail…)   │
└────────────────────────┬────────────────────────────────┘
                         │ StateFlow<UiState> / events
┌────────────────────────┴────────────────────────────────┐
│  ViewModels  (Hilt)                                      │
│  ScanViewModel · FolderViewModel · CompareViewModel …    │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────┴────────────────────────────────┐
│  Domain / Repository                                     │
│  FolderRepository · ScanRepository · CompareEngine       │
│  RegexRuleRepository · CsvCodec · ExportManager          │
└──────────┬───────────────────────────────┬──────────────┘
           │                               │
┌──────────┴──────────┐        ┌───────────┴──────────────┐
│  Data (Room + DS)   │        │  Scanner (CameraX+MLKit) │
│  DAO · Entities     │        │  BarcodeAnalyzer         │
│  DataStore prefs    │        │  OcrAnalyzer + RegexEng. │
└─────────────────────┘        └──────────────────────────┘
```

### Découpage en packages

> Ce chemin est le *namespace* Kotlin du code source, volontairement distinct de
> l'`applicationId` (`com.suiviemat.app`) depuis le 2026-09-25 — voir décision 23 de
> `contexte.md` et `outils.md` § 3.

```
com.scanlist.app
├── data
│   ├── db          (ScanListDatabase, entity/Entities.kt, dao/, Converters)
│   ├── prefs       (SettingsDataStore, EmailPrefs)
│   ├── csv         (CsvCodec RFC 4180, ScanCsvBuilder, CompareCsvBuilder)  ✅ phase 8
│   └── repository  (Folder / Scan / RegexRule / Compare)
├── domain
│   ├── model       (Enums : ScanMode, FolderKind, MatchState… + ScanValue)
│   ├── compare     (CompareEngine, CompareOutcome ; CompareOverview :
│   │                dossiers et sessions par date — pur, testable)      ✅ 2026-10-27
│   ├── regex       (RegexRuleEngine : validation, extraction ;
│   │                RegexSuggester : formules déduites d'exemples)      ✅ 2026-10-26
│   ├── search      (FolderSearch : filtre casse + accents)              ✅ phase 6
│   ├── folders     (FolderTimeline : période et moment d'activité d'un dossier,
│   │                semaine du lundi — pur, testable)                   ✅ 2026-10-27
│   └── nfc         (NfcValue : choix NDEF/UID, decodage RTD_TEXT — pur, testable)
├── di              (AppModule : Room + DAO)
├── scanner
│   ├── BarcodeAnalyzer       (ImageAnalysis.Analyzer, filtré par mode)  ✅ phase 4
│   ├── ScanDebouncer         (verrou anti-rebond post-validation)       ✅ phase 4, rôle révisé le 2026-08-24
│   ├── ScanConsensus         (validation par lectures identiques + garde d'ambiguïté) ✅ 2026-08-24
│   ├── ScanFeedback          (vibration + bip)                          ✅ phase 4
│   ├── OcrAnalyzer           (ImageAnalysis.Analyzer, texte brut)       ✅ phase 5
│   ├── AnalysisRotation      (orientation physique → rotation d'analyse) ✅ phase 9
│   └── NfcTagDecoder         (pont Tag Android → NfcValue)
├── ui
│   ├── theme       (ScanListTheme [style « Bleu de travail », décision 27 :
│   │                Source Sans 3 / Source Code Pro embarquées], ShellColors
│   │                [en-tête bleu], CompareColors : rouge = à valider,
│   │                vert = validé ; gris réservé aux bandeaux neutres)
│   ├── navigation  (Routes, ScanListNavHost, MainNavigationBar [barre du bas,
│   │                trois onglets, décision 28])
│   ├── common      (ScanListScaffold [en-tête bleu, compteur à droite du titre,
│   │                bande de comparaison dessous], HeaderCounter, MenuCard,
│   │                PrimaryButton, SecondaryButton ; ScanModePicker dans son
│   │                propre fichier, avec les échantillons d'étiquette dessinés ;
│   │                TimelineRow, lignes des listes par date de Dossiers et Comparer)
│   ├── folders     (liste filtrable, actions, détail, export + VM)      ✅ phase 6
│   ├── scan        (CameraPreview [réticule à remplissage progressif],
│   │                CameraPermissionGate, ScanViewModel, ScanScreens,
│   │                OcrPanel [PatternPill + CaptureToggleButton +
│   │                PatternEditorSheet + RegexLibrarySheet, partagée,
│   │                étape 4 de la refonte], RegexGeneratorSheet,
│   │                FormulaSheetParts [en-tête à croix, lignes à cocher,
│   │                bouton fixe : étape 7, décision 31],
│   │                NfcScanSurface)                                     ✅ phases 4-5
│   ├── compare     (onglet [CompareHomeScreen + CompareHomeViewModel],
│   │                session, CompareSections partagées,
│   │                CompareStartViewModel, CompareSessionViewModel)   ✅ phase 8
│   └── settings    (EmailPrefsScreen)
└── export          (ExportManager, ExportFileName)                     ✅ phase 7
```

> `CameraPreview` vit dans `ui/scan` et non dans `scanner/` : c'est un composable Compose,
> alors que `scanner/` ne contient que du code sans dépendance à l'UI.

## 2. Modèle de données (Room)

> **Décision d'implémentation :** les entités Room servent directement de modèles
> métier (`data/db/entity/Entities.kt`). Une couche de mapping entité ↔ modèle
> ajouterait plusieurs centaines de lignes sans bénéfice à cette échelle, la logique
> métier étant déjà isolée dans `domain/` (`CompareEngine`, `RegexRuleEngine`,
> `ScanValue`), testable sans Android.

### `folders`
| Colonne | Type | Note |
|---|---|---|
| `id` | Long PK auto | |
| `name` | String | nom affiché dans la liste déroulante |
| `kind` | enum | `SCAN` ou `COMPARISON` |
| `defaultScanMode` | enum | dernier mode utilisé, pour « Reprendre le scan » |
| `createdAt` / `updatedAt` | Long | epoch ms |

### `scan_items`
| Colonne | Type | Note |
|---|---|---|
| `id` | Long PK auto | |
| `folderId` | Long FK vers folders, `CASCADE` | |
| `value` | String | valeur normalisée (trim) — **clé de comparaison** |
| `rawValue` | String | texte brut avant regex, pour traçabilité OCR |
| `mode` | enum | `OCR`, `QR_CODE`, `BARCODE` ou `NFC` |
| `symbology` | String? | `EAN_13`, `CODE_128`, `QR_CODE`… ; en NFC, `NDEF` ou `UID` |
| `regexRuleId` | Long? | regex ayant produit la valeur (OCR) |
| `scannedAt` | Long | epoch ms |

> **Index unique `(folderId, value)`** : la règle « pas de doublon, par dossier » est
> appliquée par SQLite. L'insertion utilise `OnConflictStrategy.IGNORE` ; un doublon
> renvoie `-1`, ce qui déclenche le feedback « déjà scanné » sans interrompre le flux.

### `regex_rules`
| Colonne | Type | Note |
|---|---|---|
| `id` | Long PK auto | |
| `name` | String unique | nom donné par l'utilisateur |
| `pattern` | String | expression régulière |
| `groupIndex` | Int | 0 = match complet, n = groupe capturant |
| `createdAt` / `lastUsedAt` | Long | tri de la bibliothèque par usage récent |

### `comparison_sessions`
| Colonne | Type | Note |
|---|---|---|
| `id` | Long PK auto | |
| `name` | String | |
| `sourceKind` | enum | `LOCAL_FOLDER` ou `CSV_IMPORT` |
| `sourceRef` | String | id du dossier ou nom du fichier importé |
| `resultFolderId` | Long? | dossier unique où la session est enregistrée |
| `createdAt` | Long | |

### `reference_entries`
| Colonne | Type | Note |
|---|---|---|
| `id` | Long PK auto | |
| `sessionId` | Long FK vers comparison_sessions, `CASCADE` | |
| `value` | String | valeur de référence, **normalisée à l'insertion** |
| `label` | String? | colonne libre du CSV (description) |
| `state` | enum | `MISSING` (défaut, 🔴) ou `FOUND` (🟢) |
| `matchedAt` | Long? | horodatage de la validation |

> **La normalisation se fait à l'insertion, pas à la lecture.** `markReference` retrouve
> sa ligne par égalité stricte sur `value` ; si la base gardait `"BAT  001"` là où le
> moteur renvoie `"BAT 001"`, aucun passage au vert ne serait jamais persisté. C'est le
> genre de défaut qui ne se voit qu'à l'usage — d'où la règle.

### `intruder_entries` — **table dormante**
Plus jamais alimentée depuis la décision 14 : une valeur lue hors référence n'est pas
prise en compte. La table reste déclarée dans le schéma pour ne pas imposer de migration
destructive à l'application déjà installée ; elle disparaîtra au prochain changement de
version de base.

## 3. Pipeline de scan (cœur de l'application)

```
CameraX Preview ──┬──► PreviewView (affichage)
                  └──► ImageAnalysis (STRATEGY_KEEP_ONLY_LATEST)
                          │
                          ▼
                  Analyzer selon le mode choisi
                          │
       ┌──────────────────┴──────────────────┐
       ▼                                     ▼
  BarcodeAnalyzer                       OcrAnalyzer
  BarcodeScannerOptions                 TextRecognition (latin)
  filtré : 2D seul OU 1D seul                │
  (masques de BarcodeFormats)                │
       │                                     ▼
       │                            Regex active appliquée
       │                            sur le texte reconnu (extraction)
       ▼                                     ▼
  N codes => N valeurs           N matches => N valeurs
       └──────────────┬──────────────────────┘
                      ▼
     [isCompare] ────────────────────────────────► ScanDebouncer ─► CompareEngine
                      │                              (verrou 1,5 s, chemin inchangé,
                      │ (scan de dossier)              hors périmètre du consensus)
                      ▼
     Filtre de conformité regex (QR/Code-barres uniquement — correspondance
     totale, formule vide = tout accepté ; en OCR le filtrage est déjà fait
     par l'extraction ci-dessus)
                      ▼
               ScanConsensus  (2026-08-24)
   compte les lectures identiques d'une même valeur ; ne valide qu'au bout de
   X occurrences (3 en QR/code-barres, 5 en OCR) ; une frame sans décodage ne
   casse pas un compteur en cours, un compteur abandonné expire (TTL 800 ms) ;
   garde d'ambiguïté : deux valeurs à un caractère près jamais vues ensemble
   dans la même image réinitialisent leurs deux compteurs
                      ▼
               ScanDebouncer
   devient un VERROU ANTI-REBOND POST-VALIDATION : la valeur vient d'être
   confirmée mais reste sous l'objectif, il évite les enregistrements en
   rafale du même code pendant qu'on ne l'a pas encore éloigné (~1500 ms)
                      ▼
              Insertion Room (IGNORE)
        ┌─────────────┴─────────────┐
        ▼                           ▼
   nouveau : vibration courte   doublon : vibration double
   + son + ligne ajoutée        + toast discret, rien inséré
                      ▼
        ► retour immédiat à l'analyse (jamais de blocage)
```

**Points de vigilance :**
- L'analyseur ne doit **jamais** être suspendu pendant l'écriture en base : insertion sur
  `Dispatchers.IO`, hors du thread d'analyse. `ScanConsensus`, comme `ScanDebouncer`, tourne
  en synchrone sur le thread d'analyse — aucune coroutine par frame.
- Le filtrage par mode se fait dans les `BarcodeScannerOptions`, **pas** en post-traitement :
  un code du mauvais type n'est même pas décodé.
- **Le consensus ne s'applique qu'au scan de dossier**, pas à la fonction Comparer (portée
  validée par l'utilisateur — voir décision 19 de `contexte.md`) ni au mode NFC (pas de
  bruit optique, le débouncer seul suffit).
- La liste des éléments capturés est affichée en surimpression, ordre antéchronologique,
  alimentée par un `Flow` Room.

### 3 bis. Pipeline NFC/RFID (hors CameraX)

Le mode NFC ne participe pas au pipeline ci-dessus : pas de `ImageAnalysis.Analyzer`, pas
de flux camera. `ScanViewModel.analyzer` vaut `null` dans ce mode, et `ScanScreen` affiche
`NfcScanSurface` a la place de `CameraPreview`.

```
NfcAdapter.enableReaderMode (arme sur ON_RESUME, desarme sur ON_PAUSE de l'ecran)
        ▼
NfcAdapter.ReaderCallback : un Tag par detection (thread de service NFC)
        ▼
NfcTagDecoder.decode(tag)
   ├── contenu NDEF texte lisible  → valeur = ce texte           ("NDEF")
   └── pas de NDEF / illisible     → valeur = UID en hexadecimal ("UID")
        ▼
ScanViewModel.onNfcTagRead(DetectedNfcTag)
        ▼
   meme chemin que les codes optiques : ScanDebouncer, puis
   ScanRepository.recordAll OU CompareEngine.submit selon le contexte
```

- Toute la logique de choix NDEF-vs-UID et de decodage du format texte NFC Forum
  (RTD_TEXT) vit dans `domain/nfc/NfcValue.kt`, pure Kotlin, testable sans Android — au
  meme titre que `RegexRuleEngine`. `scanner/NfcTagDecoder.kt` n'est que le pont vers le
  SDK Android (`android.nfc.Tag`, `Ndef`).
- `enableReaderMode`/`disableReaderMode` suivent le cycle de vie de l'ecran et non la
  composition : un retour depuis les reglages systeme (activer le NFC) doit retrouver un
  lecteur arme, ce qu'une seule activation a la composition ne garantirait pas.
- Meme regle d'erreur que partout ailleurs : un tag sans UID, un payload NDEF corrompu ou
  une exception de lecture ne produisent rien — `NfcTagDecoder.decode` renvoie alors
  `null` et le callback ignore silencieusement le tag.

## 4. Moteur de comparaison

> **Sens de la comparaison (décision 14).** La fonction valide la **présence physique**
> des éléments de la liste de référence. Cette liste est le seul objet coloré : elle naît
> **entièrement en rouge** et chaque entrée passe au **vert** quand elle est lue. Un
> numéro inconnu de la liste **n'est pas pris en compte** — aucun enregistrement, aucun
> compteur, aucune ligne.

```
Chargement référence (dossier local OU CSV importé)
        ▼
   reference_entries, toutes en state = MISSING                     🔴 TOUT ROUGE
        ▼
   CompareEngine en mémoire : Map<clé normalisée, valeur affichée>
        ▼
   Pour chaque valeur lue :
        ├── présente et jamais lue  → Matched        → state = FOUND 🟢 VERT
        ├── présente et déjà lue    → AlreadyMatched → rien à écrire
        ├── absente de la référence → OutOfList      → rien du tout ⚪ bandeau fugace
        └── vide ou illisible       → Ignored        → rien
```

- **Aucune écriture hors du passage au vert.** `persistOutcome` n'a qu'un seul cas
  d'écriture ; c'est la garantie que la comparaison ne fabrique jamais de donnée.
- **Aucune action sur erreur** : décodage échoué, valeur vide ou illisible → ignoré
  silencieusement, le scan continue.
- L'écran affiche **deux sections** : « À valider » (rouge) puis « Validés » (vert), avec
  deux compteurs colorés et une barre d'avancement. Les rouges en tête : c'est la seule
  section sur laquelle il reste à agir, elle doit rester visible sans défiler.
- Le hors-liste n'est signalé que par `ScanFeedback.outOfList()` — vibration longue, bip
  grave — et un bandeau gris de 1,4 s. **Gris et non rouge** : le rouge est réservé aux
  entrées de référence à valider ; une lecture hors liste n'est pas une erreur, elle n'est
  simplement pas prise en compte.
- « Enregistrer » crée un dossier unique de type `COMPARISON` contenant **les seuls
  éléments validés**. Les manquants n'ont jamais été lus : ils n'ont pas leur place dans
  `scan_items`, c'est le `.csv` de la session qui les porte.

### Où vit quoi

| Élément | Fichier | Rôle |
|---|---|---|
| Verdict en mémoire | `domain/compare/CompareEngine.kt` | tranche sans requête SQL |
| Persistance | `data/repository/CompareRepository.kt` | crée la session, écrit le vert, construit le résultat |
| Scan branché | `ui/scan/ScanViewModel.kt` (`isCompare`) | même pipeline caméra, autre destination |
| Affichage partagé | `ui/compare/CompareSections.kt` | compteurs, barres, sections et lignes d'un seul état, utilisés par l'écran de scan **et** l'écran de session |
| Écran de session | `ui/compare/CompareSessionScreen` + `CompareSessionViewModel` | résultat, enregistrement, export, suppression |
| Onglet Comparer | `domain/compare/CompareOverview.kt` + `ui/compare/CompareHomeViewModel.kt` | dossiers et imports par date, avancement de la dernière session (`ComparisonDao.observeSessionProgress`) |

## 5. Format CSV

**Export** — UTF-8 avec BOM (compatibilité Excel FR), séparateur `;`, échappement RFC 4180 :

```csv
valeur;mode;symbologie;date_scan
BAT-2024-0001;QR_CODE;QR_CODE;2026-08-20 10:42:13
```

**Export d'une session de comparaison** (`CompareCsvBuilder`) — les manquants d'abord,
comme à l'écran :

```csv
valeur;etat;libelle;date_validation
BAT-0001;MANQUANT;;
BAT-0002;TROUVE;Rack A - travée 2;2026-08-21 14:40:00
```

C'est le **seul document qui porte les manquants**. Le dossier de résultat, lui, ne
contient que les éléments réellement lus.

**Import** — tolérant : détection automatique du séparateur (`;` ou `,`), BOM optionnel,
en-tête optionnel. **Première colonne = valeur de référence**, deuxième colonne si présente
= libellé. Lignes vides ignorées.

## 6. Étapes de développement

| # | Phase | Contenu | État |
|---|---|---|---|
| 0 | **Consignes** | Dossier `consignes/` + 3 fichiers `.md` | ✅ Fait |
| 1 | **Squelette projet** | Gradle KTS, version catalog, manifest, thème M3, `MainActivity`, Hilt | ✅ Fait |
| 2 | **Couche données** | Entities, DAO, `ScanListDatabase`, DataStore, repositories, `CsvCodec`, `RegexRuleEngine`, `CompareEngine`, 42 tests unitaires | ✅ Fait |
| 3 | **Navigation + menus** | `ScanListNavHost` reproduisant la hiérarchie imposée, écrans coquilles | ✅ Fait |
| 4 | **Scan codes** | CameraX + `BarcodeAnalyzer` (QR seul / 1D seul), `ScanDebouncer`, multi-codes, feedback haptique/sonore, torche, écran maintenu allumé | ✅ Fait |
| 5 | **Scan OCR** | `OcrAnalyzer`, éditeur regex à la volée, aperçu live, bibliothèque de formules, capture armée explicitement, messages d'erreur traduits | ✅ Fait |
| 6 | **Dossiers** | Liste filtrable, consultation, reprise de scan, suppression de ligne avec annulation, renommage, suppression de dossier, copie d'une valeur | ✅ Fait |
| 7 | **Export CSV + mail** | `ScanCsvBuilder`, `ExportFileName`, `ExportManager`, écran d'export, `FileProvider` + Intent, purge du cache | ✅ Fait |
| 8 | **Comparer** | Import CSV / dossier local, `CompareEngine` branché au scan, liste de référence rouge → verte, compteurs et barre d'avancement, enregistrement en dossier, export `.csv` à colonne `etat` | ✅ Fait |
| 9 | **Finitions** | Permissions, torche, rotation, écran allumé, gestion mémoire, icône | ✅ Fait (2026-10-27) — non vérifié sur téléphone |
| 10 | **Build & test terrain** | APK debug, installation sur Galaxy S25, test sur 10 batteries | ⬜ À faire |
| 11 | **Refonte de l'interface** (décision 27) | Style uniforme en 7 étapes, 5 variantes par étape soumises à l'utilisateur sur le canevas de maquettes : 1 style global ✅ (C, « Bleu de travail ») · 2 menu principal ✅ (E, barre de navigation, décision 28) · 3 choix du mode ✅ (E, échantillons d'étiquette) · 4 écran de scan ✅ (B, caméra pleine et tiroir, décision 29) · 5 dossiers ✅ (B, liste par date et boutons) · 6 comparer ✅ (G, mélange de B et C : avancement dans la liste, décision 30) · 7 export et fenêtres de formules ✅ (A pour l'export, inchangé ; C pour les formules, décision 31) | ✅ Terminée (7/7) — non vérifiée sur téléphone |

## 7. Journal des évolutions

| Date | Évolution | Fichiers de consignes impactés |
|---|---|---|
| 2026-10-28 | **Interface instantanée (décision 32).** Demandé par l'utilisateur. `ScanListNavHost` : `EnterTransition.None` / `ExitTransition.None` (le fondu par défaut de 700 ms disparaît, retour compris). `ScanScreens` : bandeau affiché par un simple `if` au lieu d'`AnimatedVisibility` ; tiroir déplié / replié dans un contexte `MotionDurationScale` à 0 (`InstantMotion`). `FormulaSheetParts` : `sheetCloser` remplacé par `InstantModalSheet`, une `ModalBottomSheet` dont l'état naît `Expanded` et dont la fenêtre de dialogue reçoit `setWindowAnimations(0)` ; les quatre feuilles (formule, formules enregistrées, aide, Regex Auto) l'utilisent et se ferment par appel direct à `onDismiss`. `themes.xml` : `android:windowAnimationStyle` à `@null`. Vérifié sur l'AVD (display 2) par captures 100 à 200 ms après l'appui : onglets, écran de scan, tiroir et feuille de formule déjà à leur état final, sans mélange ni transparence (avant : feuille encore en fondu à 250 ms). Build et tests unitaires verts, aucun avertissement. Non vérifié sur le S25. | `contexte.md` |
| 2026-10-28 | **Performance : APK de test optimisé (1.10).** Mesure sur l'AVD `SuivieMAT_Test` (démarrage à froid, `dumpsys gfxinfo` sur un parcours d'usage, puis avec 205 dossiers, 2 000 captures et une comparaison de 1 500 lignes injectés) : le code de l'application tient la charge (listes ≤ 5 % d'images en retard, même en debug) ; le frein est l'APK **debuggable**. Nouveau type de build `optimise` (R8 + suppression des ressources, non debuggable, même `applicationId` et même clé de debug, donc installable par-dessus sans perte de données) : démarrage 1,8 s → 0,4 s, frames « Slow UI thread » divisées par 3. **La CI dépose désormais cet APK** dans `release/SuivieMAT-debug.apk` (nom inchangé pour garder le lien). Vérifié écran par écran en optimisé, sans plantage ni règle R8 supplémentaire. Écran de scan : l'avancement du consensus est lu au dessin du réticule (plus de recomposition de tout l'écran à chaque lecture), filtres rouge / vert mémorisés — gain non mesurable sur émulateur, qui ne lit aucun code. Pipeline de scan inchangé : le débit d'analyse (~10 images/s sur émulateur) ne dépend pas de la charge de l'UI. À noter : les écouteurs ML Kit tournent en fait sur le thread principal, et non sur le thread d'analyse comme le disent les commentaires — laissé tel quel, faute de gain mesuré. `versionName` 1.10 (`versionCode` 11). 206 tests, 0 échec. **Non vérifié sur téléphone.** | `outils.md`, `workflow.md`, `plan-suivimat.md`, `CLAUDE.md` |
| 2026-10-28 | **Chemins locaux et `main`.** Le même défaut d'ouverture a été trouvé en parallèle sur émulateur (session locale). Le correctif ci-dessous (`new-scan`) a été retenu. En plus : `CLAUDE.md` et `outils.md` pointaient vers `Desktop\Claude Code\ScanList`, qui contient l'application ScanList indépendante ; ils pointent maintenant vers `Desktop\Claude Code\SuivieMat\repo`, et le miroir Drive part de `SuivieMat`. `main`, restée à l'état ScanList du 2026-09-22, intègre désormais cette branche. Les autres points du rapport de test du 2026-10-27 (casse en Comparer, consensus, double retour, suppression sans annulation, etc.) attendent une décision. | `workflow.md`, `outils.md`, `CLAUDE.md` |
| 2026-10-28 | **Correctif 1.9 : l'application s'ouvrait sur un scan Code 2D sans retour possible.** Signalé par l'utilisateur sur le S25, capture à l'appui (« Nouveau dossier · Code 2D », aucune navigation, même après effacement des données). Cause, vérifiée dans le bytecode de Navigation 2.8.2 : `NavGraph.findNode(route)` retient le **premier** écran dont la route est égale *ou dont le modèle correspond* ; la route d'ouverture `scan/new` (étape 2, décision 28) correspondait au modèle `scan/{mode}?…` de l'écran de scan, ouvert alors avec `mode = "new"`, inconnu, donc repli sur Code 2D. Premier écran de la pile : ni flèche de retour utile, ni barre du bas. Présent depuis l'APK 1.2. Correctif : `Routes.NEW_SCAN_MODE` = `new-scan` ; nouveau `RoutesTest` (2 tests) qui interdit qu'une route en avale une autre — vérifié en échec avec l'ancienne valeur. **Polices** : la capture montrait aussi tous les titres en trait fin. Les fichiers Source Sans 3 et Source Code Pro sont variables ; `Font(res, weight)` ne réglait pas l'axe `wght` et Android dessinait l'instance par défaut (ExtraLight). Chaque graisse passe désormais par `FontVariation.Settings(FontVariation.weight(...))` (API marquée expérimentale en Compose 1.7, `@OptIn`). Aucun émulateur ne tournant en session cloud, ces deux défauts n'avaient été vus sur aucun écran réel avant ce retour. `versionName` 1.9 (`versionCode` 10). **206 tests, 0 échec**, aucun avertissement. | `workflow.md`, `outils.md`, `CLAUDE.md` |
| 2026-10-27 | **Phase 9 : finitions.** Revue des six points du tableau ; trois étaient déjà en place et le restent : **torche** (bouton de l'en-tête de l'écran de scan, masqué en NFC, réappliquée à chaque réouverture de la caméra), **écran allumé** (`FLAG_KEEP_SCREEN_ON` posé par l'écran de scan seul), **gestion mémoire** côté caméra (`STRATEGY_KEEP_ONLY_LATEST`, chaque `ImageProxy` refermé, clients ML Kit fermés dans `onCleared`, caméra déliée à la sortie, exports de plus de 24 h purgés du cache, lecteur NFC armé et désarmé sur `ON_RESUME` / `ON_PAUSE`). Corrigé : **énergie** — en OCR, la reconnaissance de texte tournait sur chaque image même capture désarmée, pour un résultat aussitôt jeté (plus d'aperçu depuis la décision 26) ; `OcrAnalyzer` reçoit `isActive` et referme l'image sans la lire tant que la capture n'est pas armée (moins de chauffe pendant la saisie de la formule ou les déplacements). **Permissions** — après deux refus, Android n'affiche plus sa boîte et « Autoriser la caméra » ne faisait rien : `CameraPermissionGate` détecte ce cas (`shouldShowRequestPermissionRationale` faux après un refus) et propose « Ouvrir les réglages » (fiche de l'application) ; la permission est relue au retour (`LifecycleResumeEffect`), sans quoi l'écran restait bloqué ; pictogramme teinté de la couleur du texte, lisible sur le noir de la caméra. **Rotation** — l'écran reste verrouillé en portrait, mais l'analyse suit désormais la position physique du téléphone (`OrientationEventListener` → `ImageAnalysis.targetRotation`, seuils à 45° de CameraX, extraits dans `scanner/AnalysisRotation.kt`, **5 tests**) : une étiquette lue téléphone couché arrive à l'OCR dans le sens de lecture. **Icône** — le logo fourni par l'utilisateur est inchangé ; son calque `monochrome` (icônes à thème d'Android 13+, One UI) réutilisait l'image couleur, que le lanceur réduit à une silhouette pleine : nouveau `drawable-nodpi/ic_launcher_monochrome.png`, les traits sombres du logo en opaque et les aplats clairs en transparent (dessin au trait). `versionName` 1.8 (`versionCode` 9). **204 tests, 0 échec**, aucun avertissement. **Non vérifié sur téléphone** : rotation à l'OCR, retour des réglages, icône à thème. | `outils.md`, `workflow.md`, `CLAUDE.md` |
| 2026-10-27 | **Refonte de l'interface, étape 7 (dernière) : fenêtres de formules « on coche, puis un bouton » (décision 31).** Cinq présentations sur le canevas (page « 7 · Export et formules ») ; l'utilisateur retient **A pour l'export** (écran et préférences inchangés) et **C pour les formules**. Nouveau fichier `ui/scan/FormulaSheetParts.kt` : `SheetHeader` (titre et croix de fermeture en haut), `SheetActionBar` (bouton fixe au bas de la feuille), `ChoiceList` (carte, groupe sélectionnable pour le lecteur d'écran), `ChoiceRow` (ligne entière cochable, fond teinté une fois cochée), `sheetCloser` (fermeture animée). `RegexGeneratorSheet` : mode d'emploi masqué dès la première frappe, propositions à cocher (la plus stricte d'office, remise en tête à chaque nouvelle saisie), explication et exemple vert / rouge sous la seule proposition cochée, « Utiliser cette formule » en bas. `RegexLibrarySheet` : formule active cochée d'office et repérée « Active » (liseré), « Appliquer » grisé tant que la formule cochée est celle en service, suppression dans le menu ⋮ de la ligne (`RuleMenu`), « Enregistrer la formule actuelle » ouvre `SaveRuleDialog` (formule rappelée, nom) ; aide ouverte par l'icône « ? » de l'en-tête. `RegexHelpSheet` : même en-tête, plus de « Fermer » en bas ; `RegexHelpButton` supprimé. **Défaut corrigé** : l'objet et le message par défaut de l'export portaient encore l'ancien nom (« Export ScanList », « Liste exportee depuis ScanList. », sans accent) ; ils viennent désormais de `strings.xml` (`export_default_subject` / `_body` : « Export SuivieMAT », « Liste exportée depuis SuivieMAT. »), et `SettingsDataStore` traite l'ancien défaut enregistré tel quel comme absent. `versionName` 1.7 (`versionCode` 8). **199 tests, 0 échec**, aucun avertissement. Dépôt passé en **public** le 2026-10-27 : le push relance la CI, qui compile et dépose l'APK dans `release/`. **Non vérifié sur téléphone.** | `contexte.md`, `outils.md`, `workflow.md`, `CLAUDE.md` |
| 2026-10-27 | **Refonte de l'interface, étape 6 : onglet Comparer par date, avec l'avancement des comparaisons (décision 30).** Cinq présentations sur le canevas (page « 6 · Comparer »), puis cinq mélanges de B et C à la demande de l'utilisateur (page « 6 bis ») ; **G retenue**. Constat en préparant les maquettes : une session quittée ne se rouvrait plus (`observeSessions` n'était lu nulle part). Nouvelle requête `ComparisonDao.observeSessionProgress` : une ligne par session, comptes et dernière validation calculés en SQL (`LEFT JOIN` + `GROUP BY`, vérifiée sur une base SQLite d'essai), relue à chaque validation. Nouvelle classe pure `domain/compare/CompareOverview.kt` (`SessionProgress`, `CompareEntry`) : fusionne dossiers et sessions en une liste par date ; un dossier porte sa **dernière** session et remonte à la date de sa dernière validation ; un import `.csv`, ou la session d'un dossier supprimé depuis, devient une ligne à part. **9 tests** (`CompareOverviewTest`). Onglet (`CompareHomeScreen` + `CompareHomeViewModel`, qui remplacent `CompareSourceScreen` et la route `COMPARE_PICK_FOLDER`) : « Importer un fichier .csv », puis les périodes de `FolderTimeline` ; sous une ligne comparée, `SessionProgressStrip` (reste à valider ou « Complète », compte, « Reprendre » / « Ouvrir », barre) ; un appui sur un dossier déjà comparé ouvre « Comparaison déjà commencée » (Reprendre / Nouvelle comparaison). Lignes partagées avec l'onglet Dossiers dans `ui/common/TimelineRows.kt` (`TimelineRow`, `momentLabel`, `FolderPeriod.label`). Session (`CompareSessionScreen`) : `CompareProgressHeader` (reste à valider en grand, barre) dans l'en-tête, sélecteur « À valider / Validés » teint de la couleur des lignes qu'il montre, `compareRows` (lignes d'un seul état), « Lancer le scan » dans le nouvel emplacement `bottomBar` de `ScanListScaffold` (les messages s'affichent au-dessus) ; Enregistrer et Exporter en icônes d'en-tête, « Supprimer la comparaison » au menu (`CompareRepository.deleteSession`, les dossiers ne sont pas touchés). Barres d'avancement sans point ni espace de fin (`gapSize = 0`, `drawStopIndicator` vide). Chaînes `compare_source*` retirées. `versionName` 1.6 (`versionCode` 7). **199 tests, 0 échec**, aucun avertissement ; APK déposé dans `release/`. **Non vérifié sur téléphone.** | `contexte.md`, `outils.md`, `workflow.md`, `CLAUDE.md` |
| 2026-10-27 | **Refonte de l'interface, étape 5 : dossiers rangés par date, fiche centrée sur « Reprendre le scan ».** Cinq organisations présentées sur le canevas (page « 5 · Dossiers », liste et fiche) ; **B retenue** par l'utilisateur. Nouvelle classe pure `domain/folders/FolderTimeline.kt` : `periodOf` range un dossier selon sa dernière activité (`updatedAt`, mis à jour à chaque capture) en **Aujourd'hui / Cette semaine / Plus ancien** — semaine **du lundi**, comme au calendrier français, et non fenêtre glissante qui rangerait le jeudi précédent dans « Cette semaine » le lundi matin ; date future (horloge recalée) gardée en tête ; jour jugé dans le fuseau du téléphone. `momentOf` dit le moment de la façon la plus courte : heure, « hier », jour de la semaine, date, date avec année. `group` conserve l'ordre de la base dans chaque période et omet les périodes vides. **10 tests** (`FolderTimelineTest`). `FolderListScreen` : intitulé de période, puis lignes compactes formant une carte par période (pictogramme du mode — ou de la comparaison —, nom, « mode · moment », nombre d'éléments à droite, annoncé « 47 éléments » au lecteur d'écran). Pictogrammes par mode dans `ui/common/ScanModeIcons.kt`, dont un code-barres dessiné (Material Icons n'en a pas). Fiche (`FolderActionsScreen`) : « Reprendre le scan » en bouton principal, « Consulter » et « Exporter en .csv » côte à côte, puis les cinq dernières valeurs et « Tout voir » (vers Consulter) ; les trois entrées de la spécification restent, seule leur présentation change. Chaînes `folder_action_*_desc`, `folder_item_count`, `folder_kind_comparison` retirées. `versionName` 1.5 (`versionCode` 6). **190 tests, 0 échec**, aucun avertissement ; APK déposé dans `release/`. **Non vérifié sur téléphone.** | `outils.md`, `workflow.md`, `CLAUDE.md` |
| 2026-10-27 | **Refonte de l'interface, étape 4 : écran de scan à caméra pleine hauteur et tiroir (décision 29).** Cinq dispositions présentées sur le canevas (page « 4 · Écran de scan », chacune en Code 2D et en OCR) ; **B retenue** par l'utilisateur. `ScanScreen` : la `Column` caméra / panneau / liste devient un `BottomSheetScaffold` ; la caméra (ou la surface NFC) occupe toute la hauteur jusqu'au bord du tiroir replié, qui montre sa poignée, un en-tête et les deux dernières lignes (`DRAWER_PEEK` 208 dp + barre système) et descend sous la barre de navigation système. L'en-tête du tiroir le déplie ou le replie **d'un appui** (« Tout voir » / « Replier ») : le glisser n'est pas le seul geste, on scanne parfois ganté. Tiroir jamais plus court que sa hauteur repliée (sinon le fond noir apparaît dessous), message « Vise une étiquette… » quand il est vide ; en comparaison, il porte la liste de référence rouge / verte. `OcrControlPanel` et `BarcodeFilterPanel` remplacés par trois pièces (`OcrPanel.kt`) : `PatternPill`, pastille translucide en haut de l'image (formule, « aucun filtre », ou « invalide ») ; `PatternEditorSheet`, feuille d'édition ouverte d'un appui sur la pastille, qui garde le champ, les trois outils dessous (décision 26) et le groupe capturé, clavier ouvert d'office seulement si la formule est vide ; `CaptureToggleButton`, bouton flottant au bas de l'image en OCR, qui ouvre l'édition au lieu de rester grisé tant que la formule n'est pas valide (un bouton grisé ne se lit pas sur l'image sombre). Commandes sur l'image en couleurs fixes (fond sombre translucide, texte clair), quel que soit le thème. Le bandeau de dernière lecture reste à durée 0, comme décidé. `versionName` 1.4 (`versionCode` 5). 180 tests, 0 échec, aucun avertissement ; APK déposé dans `release/`. **Non vérifié sur téléphone** : position du réticule, geste du tiroir, clavier dans la feuille, thème sombre. | `contexte.md`, `outils.md`, `workflow.md`, `CLAUDE.md` |
| 2026-10-27 | **Refonte de l'interface, étape 3 : choix du mode par échantillon d'étiquette.** Cinq présentations du sélecteur de mode sur le canevas (page « 3 · Choix du mode ») ; **E retenue** par l'utilisateur. `ScanModePicker` quitte `Components.kt` pour `ui/common/ScanModePicker.kt` : chaque carte montre, dans une vignette de 96 × 72 dp, un petit dessin de ce que le mode lit — numéro imprimé (OCR), QR Code stylisé sur 25 modules (Code 2D), une rangée de barres (Code-barres), tag sans contact avec antenne et puce (NFC/RFID) — dessinés en `Canvas`, en blanc et encre fixes (une étiquette reste blanche en thème sombre). Le choix explicite du mode, exigence anti-erreur, est inchangé : le dessin le rend seulement plus sûr. Descriptions réécrites en « ce que l'on voit » (`mode_*_desc`) ; pour le Code 2D, « un carré à motif (QR Code, Data Matrix, Aztec) ou un PDF417 », et non « un code en plusieurs étages » qu'utilisait la maquette : c'est le mot de l'utilisateur pour les codes empilés que l'application **ne sait pas** lire (décision 21). Sous-titre de Nouveau scan : « Quelle étiquette lis-tu ? ». Les mêmes cartes servent à Reprendre le scan (dernier mode teint en bleu, vignette éclaircie) et à Lancer le scan d'une comparaison. `versionName` 1.3 (`versionCode` 4). 180 tests, 0 échec, aucun avertissement ; APK déposé dans `release/`. **Non vérifié sur téléphone** : netteté des dessins, thème sombre. | `outils.md`, `workflow.md`, `CLAUDE.md` |
| 2026-10-27 | **Refonte de l'interface, étape 2 : barre de navigation (décision 28).** Cinq organisations du menu principal présentées sur le canevas (page « 2 · Menu principal ») ; **E retenue** par l'utilisateur, qui modifie la hiérarchie imposée (§ 4 de `contexte.md`, note ajoutée). `MainMenuScreen` et la route `MENU` supprimés. Nouveau fichier `ui/navigation/MainNavigationBar.kt` : `TopLevelDestination` (Dossiers · Comparer · Nouveau scan, route, libellé, icône) et la `NavigationBar` Material aux couleurs du style (indicateur `primaryContainer`). `ScanListNavHost` : une `Column` avec le graphe (extrait dans `ScanListGraph`, inchangé) et la barre, **affichée sur les trois écrans racines seulement** ; démarrage sur `NEW_SCAN_MODE` ; navigation entre onglets standard (`popUpTo` du départ avec `saveState`, `launchSingleTop`, `restoreState`) ; quand la barre est là, `consumeWindowInsets(navigationBars)` évite que l'écran compte une seconde fois la barre système. Écrans racines sans flèche de retour, donc titre en grand : `NewScanModeScreen` et `CompareSourceScreen` perdent `onBack` ; `FolderListScreen` le rend optionnel et reçoit un sous-titre (racine : « Consulter, exporter ou reprendre un scan » ; choix de la liste de référence : « Utiliser une liste déjà scannée »). Chaînes `menu_title`, `menu_compare_subtitle`, `menu_new_scan_subtitle` retirées. `versionName` 1.2 (`versionCode` 3). 180 tests, 0 échec, aucun avertissement ; APK déposé dans `release/`. **Non vérifié sur téléphone** : retour système depuis un onglet, barre masquée sur l'écran de scan. | `contexte.md`, `outils.md`, `workflow.md`, `CLAUDE.md` |
| 2026-10-26 | **Refonte de l'interface, étape 1 : style « Bleu de travail » (décision 27).** Demandé par l'utilisateur : revoir toute l'interface avec les guides *impeccable* (`github.com/pbakaus/impeccable`, références `android.md`, `craft-floor.md`, mode « Operate »), style uniforme, cinq variantes à chaque changement, choix par notification. Cinq directions présentées sur un canevas de maquettes (menu, scan Code 2D, comparaison) ; **C retenue**. `Theme.kt` réécrit : palettes claire et sombre, `ShellColors` (bleu de l'en-tête, hors du schéma Material car il reste foncé en thème nuit), `CompareColors.progress` (vert franc de la barre posée sur le bleu), échelle typographique agrandie (`bodySmall` 12 → 14 sp), formes 14/16/20 dp, polices variables **embarquées** `res/font/source_sans_3.ttf` et `source_code_pro.ttf` (licence OFL dans `assets/licences/`, +0,85 Mo). `ScanListScaffold` remplace la `TopAppBar` par un en-tête bleu maison : ligne d'icônes, grand titre, sous-titre, emplacement `headerTrailing` (compteur) et `headerBottom` (compteurs de comparaison) ; barre d'état toujours en icônes claires. Nouveaux composants communs `HeaderCounter`, `PrimaryButton`, `SecondaryButton`, `SectionTitle` ; `MenuCard` gagne `emphasized` (carte teinte du bleu), qui remplace la puce « • » du dernier mode utilisé. Compteurs déplacés dans l'en-tête : captures (scan de dossier), éléments (fiche, détail et reprise d'un dossier), avancement (session et scan de comparaison) ; `CaptureHeader` supprimé. Titres d'écran et consignes (« Choisir le mode de scan », source de la liste…) passés en sous-titre de l'en-tête. Toutes les chasses fixes passent par `ValueFontFamily`. Chaînes : `scan_capture_label` et `folder_item_label` (pluriels), `scan_new_folder_title`, `scan_subtitle_new_folder`, `folder_header_details` ajoutées ; `scan_title`, `scan_captured`, `scan_new_folder`, `folder_created_at`, `folder_last_mode`, `wip_notice` (et `PhaseNotice`, inutilisé) retirées. `versionName` 1.0 → **1.1** (`versionCode` 2). 180 tests, 0 échec, aucun avertissement ; APK compilé en session cloud et déposé dans `release/`. **Écrans non vérifiés sur émulateur ni sur téléphone** (session cloud sans KVM) : à contrôler sur le Galaxy S25, en particulier la hauteur de l'en-tête sur l'écran de scan et le libellé « Regex Auto » (passé en `labelMedium` pour ne pas être tronqué). | `contexte.md`, `outils.md`, `workflow.md`, `CLAUDE.md` |
| 2026-10-26 | **Panneau de formule épuré (décision 26).** Demandé par l'utilisateur sur capture d'écran. `OcrControlPanel` : retrait de l'aperçu en direct, des correspondances et du texte lu ; le champ prend toute la largeur. Nouvelle rangée privée `RegexToolsRow` sous le champ, partagée avec `BarcodeFilterPanel` : trois `FilledTonalButton` de poids égal, icône + libellé (« Info », « Regex Auto », « Enregistré »), marges internes réduites à 4 dp pour que « Regex Auto » tienne sur un S25 (environ 112 dp par bouton). L'ancien bouton rond « ? » ne subsiste que dans l'en-tête de la bibliothèque ; `RegexGeneratorButton` (icône seule) supprimé. `ScanViewModel` : `OcrPreview` et son `StateFlow` supprimés, `onTextRecognized` sort immédiatement tant que la capture est désarmée. `ScanScreens` : le panneau OCR n'a plus de poids (40 % auparavant), il prend la hauteur de son contenu ; caméra et liste reprennent les poids du mode code-barres. Six chaînes devenues inutiles retirées. 180 tests, 0 échec, aucun avertissement. **Écran non vérifié sur émulateur** (session cloud sans KVM). | `contexte.md`, `workflow.md` |
| 2026-10-26 | **Générateur de formules regex à partir d'exemples, sans IA (décision 25).** Demandé par l'utilisateur, après étude d'une IA locale écartée (S25 absent de la liste Gemini Nano, modèle embarqué de plusieurs centaines de Mo et peu fiable sur les regex). Nouvelle classe pure `domain/regex/RegexSuggester.kt` : découpage de l'exemple en blocs (chiffres, majuscules, minuscules, caractères fixes), fusion de plusieurs exemples (longueurs en plage, casse mélangée → `[A-Za-z]`, structures différentes → `Incompatible`), puis cinq variantes dans l'ordre : `STRICT` (`\b\d[A-Z]{2}\d{3}\b` pour `1GH546`), `ANY_CASE`, `SEPARATORS` (`[ -]?` entre les blocs, plus tout séparateur `/ . _` vu dans l'exemple), `FIXED_PREFIX` (tout fixe sauf le dernier bloc, seulement si commun à tous les exemples), `FLEXIBLE` (`+`). `\b` posé uniquement du côté où le format commence ou finit par un chiffre ou une lettre (`\b#` ne reconnaîtrait plus `#123`). Garde-fous : toute proposition qui ne reconnaît pas chaque exemple est écartée, les doublons aussi, et les valeurs d'illustration (« Accepte aussi » / « Refuse ») ne sont montrées que si le moteur confirme ce qu'elles illustrent. Aucun texte dans le moteur : titres, commentaires et description du format (pluriels) sont dans `strings.xml`. Interface : `ui/scan/RegexGeneratorSheet.kt`, ouvert par un bouton (icône baguette) à côté du champ de formule dans `OcrControlPanel` et `BarcodeFilterPanel` ; un appui sur une proposition appelle `setPattern`, comme une saisie manuelle. 27 tests (`RegexSuggesterTest`), 180 au total, 0 échec, aucun avertissement. **Écran non vérifié sur émulateur** : session cloud sans KVM — à contrôler sur le Galaxy S25. | `contexte.md`, `outils.md`, `workflow.md` |
| 2026-10-10 | **Nettoyage automatique du stockage GitHub Actions.** Demandé par l'utilisateur, le compte JRRocks ayant atteint 100 % de ses 0,5 Go de stockage Actions (quota commun à tous ses dépôts, facturé à l'heure). Nouveau workflow `nettoyage-actions.yml` (purge des artefacts plus vieux que N heures, run en cours épargné), appelé après chaque compilation et planifié chaque nuit — la planification ne s'active qu'une fois le fichier sur `main`. L'APK n'est plus publié en artefact quand il est déposé dans `release/` (doublon de ~40 Mo par run pendant 7 jours), rétention des artefacts restants ramenée à 1 jour. Ce dépôt ne portait aucun artefact ce jour-là : le quota est consommé par d'autres dépôts du compte. Script vérifié en local contre un `gh` simulé, workflows validés par `actionlint`. Aucun code de l'application modifié. | `outils.md`, `workflow.md` |
| 2026-09-25 | **SuivieMAT devient une application distincte de ScanList (décision 23).** L'utilisateur a précisé que ScanList est une application indépendante qu'il garde en l'état : sous l'`applicationId` et le nom hérités du projet d'origine, les deux se seraient superposées sur un même téléphone. `applicationId` → `com.suiviemat.app`, nom affiché → « SuivieMAT » (`app_name`), nom du projet Gradle → « SuivieMAT » (cosmétique). Le *namespace* Kotlin du code source reste `com.scanlist.app` : seul l'`applicationId` détermine l'identité Android, le renommer n'aurait rien apporté pour un chantier mécanique sur tout le code. Conséquence assumée : à l'installation, c'est une **application neuve** pour Android, sans lien avec un test antérieur fait sous l'ancien identifiant. 220 tests, 0 échec, `aapt2 dump badging` vérifié (`package='com.suiviemat.app'`, `application-label:'SuivieMAT'`). | `contexte.md`, `outils.md`, `plan-suivimat.md` |
| 2026-09-25 | **APK de test compilé sur GitHub et déposé dans `release/`.** Demandé par l'utilisateur, sans ordinateur sous la main. Le push ne déclenche pas le workflow sur ce dépôt, mais le lancement manuel fonctionne (y compris depuis une session cloud). Le workflow dépose désormais l'APK dans `release/SuivieMAT-debug.apk` de la branche, avec commit source, date et SHA-256 (`release/SuivieMAT-debug.txt`), en arm64 seul (`-Papk.phoneOnly`) pour limiter le poids de l'historique. **Clé de debug versionnée** (`app/debug.keystore`) : sans elle, chaque runner signait avec une clé jetable et chaque APK de test aurait imposé une désinstallation, donc la perte des dossiers du téléphone. Passage unique : l'application installée avant depuis Android Studio doit être désinstallée une fois. Mode d'emploi : `release/LISEZMOI.md`. 205 tests, 0 échec, en local avant le push. | `outils.md`, `plan-suivimat.md` |
| 2026-09-24 à 25 | **Écran « Test du badge » : construit, testé, puis retiré (décisions 22 puis 24).** Demandé par l'utilisateur pour éprouver physiquement une identification par certificat et défi-réponse sur le badge des magasiniers, sans jamais envoyer de code PIN. Le test physique a montré que ce badge est une **carte CIMS** (ministère des Armées), dont l'authenticité ne peut pas être vérifiée par cette application. **Décision du 2026-09-25 : identification arrêtée au niveau 1** (lecture d'UID par le mode NFC déjà existant), **écran et logique associée retirés du code** (`domain/card/`, `ui/cardtest/`, `scanner/CardTestReader.kt` supprimés avec leurs tests). Détail dans `plan-suivimat.md` § 10 ter. | `contexte.md`, `outils.md`, `workflow.md`, `plan-suivimat.md` |
| 2026-09-24 | **Étude de faisabilité SuivieMAT** (suivi de matériel sous contraintes comptables : journal, horodatage, audit ; comptable sur Excel 2016 en intranet restreint). Le plan vit dans un fichier dédié, `consignes/plan-suivimat.md`. Recommandation : **option 1 améliorée** — `.csv` par mail vers une boîte fonctionnelle, format d'échange traité comme un contrat, aucune permission réseau ajoutée. Cinq constats de sécurité sur le code actuel, dont l'**absence de neutralisation de l'injection de formule CSV** (`CsvCodec.escapeField`) et un **import décodé uniquement en UTF-8** (un CSV enregistré par Excel 2016 est en Windows-1252). **Aucune décision du § 5 de `contexte.md` modifiée, aucun code modifié** : douze propositions attendent la validation de l'utilisateur, neuf questions la DSI. | `plan-suivimat.md` (nouveau), `workflow.md` |
| 2026-09-22 | **Formules regex par défaut (HK416, ARVA).** Demandé par l'utilisateur : la bibliothèque « Formules enregistrées » ne doit plus démarrer vide. `RegexRuleRepository.seedDefaults()` insère `HK416` (`\b\d{2}-\d{6}\b`, numéro de série d'arme) et `ARVA` (`\b\d{5}\b`, identifiant d'appareil de recherche de victimes d'avalanche). Insertion idempotente par nom (index unique sur `regex_rules.name`), mais ce n'est **pas** ce qui empêche une réapparition après suppression : c'est `ScanListApplication.onCreate()` qui ne déclenche l'appel qu'une seule fois, jamais deux, via un indicateur booléen persistant dans `SettingsDataStore` (`regex_defaults_seeded`) — supprimer les deux formules ne les fait donc pas revenir au lancement suivant. Fonctionne aussi bien sur une installation neuve que sur l'installation existante du Galaxy S25 (sa bibliothèque était vide, l'indicateur y est encore à `false`). 3 tests avec un faux DAO en mémoire, premier test du genre dans ce dépôt (jusque-là uniquement des tests de logique pure) : le décodage n'étant pas en cause ici, la logique se prête entièrement au test JVM. | `workflow.md` |
| 2026-09-15 | **Mode Code 2D : le scan des codes empilés (décision 21).** Déclencheur : une étiquette de terrain portant un code-barres « en plusieurs étages » que l'application ignorait. Analyse préalable : le symbole photographié a **4 rangées séparées par des barres horizontales dans un cadre complet**, signature de Codablock F / Code 16K / Code 49 — et **non** de PDF417 dont les rangées sont contiguës ; tentative de décodage avec ZXing (PDF417, Data Matrix, Aztec, tout le 1D) restée sans résultat. **Ce code précis restera donc illisible** (voir la limite technique du § 8), l'OCR étant le contournement immédiat puisque l'étiquette porte son numéro en clair. Ce qui a été implémenté, choisi par l'utilisateur entre trois options : **élargir le mode « QR Code » en « Code 2D »** (QR + PDF417 + Data Matrix + Aztec) plutôt que créer un cinquième mode, **« Code-barres » restant strictement 1D** — la frontière 1D/2D est ce qui empêche une étiquette portant les deux d'ouvrir deux lignes pour un objet. `ScanMode.QR_CODE` **garde son nom de constante** (persisté en base par `Converters` : le renommer imposerait une migration aux dossiers existants), seul son libellé change. La table des formats quitte `BarcodeAnalyzer` pour `scanner/BarcodeFormats.kt`, pur et testable en JVM — **8 tests** y verrouillent les masques et surtout leur **disjonction**, seul filet possible puisque le décodage réel n'est exerçable ni sur émulateur ni en CI. Résolution d'analyse portée à **1080p pour ce seul mode** (un PDF417 tombe sous ~2 px/module en 720p) ; le 1D garde 720p et sa cadence. Garde d'ambiguïté toujours désactivée sur ce mode : les quatre formats portent une correction Reed-Solomon. Le workflow CI lance désormais `testDebugUnitTest` avant `assembleDebug`. **Non vérifié sur matériel réel** : à confirmer sur le Galaxy S25 avec un vrai PDF417 et un vrai Data Matrix (phase 10). | `contexte.md`, `outils.md`, `workflow.md` |
| 2026-08-24 | **Correctif visuel — champ `BarcodeFilterPanel` illisible.** Signalé par l'utilisateur : contrairement au champ de formule OCR, le texte saisi dans le filtre regex de QR Code / code-barres ne s'affichait pas correctement. Cause : `ScanScreen` (`ScanScreens.kt`) donnait au panneau un `Modifier.weight(0.12f)` dans la `Column` de l'écran, ce qui impose une hauteur exacte trop faible pour que l'`OutlinedTextField` (label + texte + texte d'aide, ~80-90dp) se rende sans être comprimé. Le panneau OCR ne subit pas ce défaut car son `weight(0.40f)` est généreux et sa `Column` interne est scrollable. Correctif : suppression du `weight` forcé, le panneau se dimensionne désormais à sa hauteur naturelle, comme `CaptureHeader` le fait déjà dans le même `Column` — Compose redistribue l'espace restant entre caméra et liste sans effet de bord. `assembleDebug` sans avertissement. **Non vérifié sur émulateur** à la demande de l'utilisateur — à confirmer visuellement à la prochaine session sur cet écran. | `workflow.md` |
| 2026-08-24 | **Vérification par consensus contre les erreurs optiques (OCR et code-barres).** Demandé par l'utilisateur : angle, reflet et étiquette abîmée font se tromper l'optique. Nouvelle classe pure `scanner/ScanConsensus.kt`, synchrone sur le thread d'analyse comme `ScanDebouncer` : une valeur n'est retenue qu'apres avoir ete lue identique **3 fois** (QR/code-barres) ou **5 fois** (OCR), avec verrouillage + purge du compteur des validation. Une frame sans decodage ne casse pas un compteur en cours (une main qui tremble ne doit pas repousser indefiniment la validation) ; un compteur abandonne expire au bout de 800 ms. **Garde d'ambiguite** (discutee et validee avec l'utilisateur, qui a d'abord questionne sa necessite face au filtre regex) : deux valeurs a un caractere pres qui n'ont **jamais coexiste dans la meme image** repartent toutes les deux a zero — protege contre les confusions *intra-classe* (`8`↔`6`, `C`↔`G`…) qu'aucun format regex ne peut detecter, sans jamais annuler deux numeros de serie sequentiels lus ensemble. Desactivee en QR Code (correction Reed-Solomon). `ScanDebouncer` (1,5 s) change de role : il n'est plus le pre-filtre anti-rafale mais un verrou anti-rebond pose **apres** la validation par consensus, sans quoi il aurait ecarte les lectures 2 et 3 avant que le consensus ne les compte. **Portee limitee au scan de dossier** (OCR, QR Code, code-barres) sur decision explicite de l'utilisateur : la fonction Comparer garde son verdict sur une seule lecture, le mode NFC n'est pas concerne. Le filtre regex, jusque-la reserve a l'OCR, est etendu au QR Code et au code-barres comme filtre de conformite optionnel (`RegexRuleEngine.conforms`, correspondance totale — pas d'extraction, pas de groupe capturant) via un nouveau panneau compact `BarcodeFilterPanel`, necessaire car `CODE_39` sans cle et `ITF` n'ont aucun checksum ML Kit contrairement a EAN/UPC/Code 128. Le reticule de `CameraPreview` se remplit progressivement avec l'avancement du consensus (`Viewfinder(progress)`), sans aucun son avant le seuil atteint — a 0 (Comparer, NFC, rien en cours), l'apparence est identique a avant cet ajout. 18 tests ajoutes (141 au total, 0 echec), `assembleDebug` sans avertissement. **Non verifiable sur emulateur** (memes limites materielles que le decodage lui-meme) : seuils et TTL a confirmer sur le Galaxy S25 en phase 10. | `contexte.md`, `outils.md`, `workflow.md` |
| 2026-08-21 | **Ajout hors-plan : quatrième mode de scan NFC/RFID.** Demandé par l'utilisateur apres la phase 8. `ScanMode.NFC` rejoint OCR/QR/Code-barres dans les trois sous-menus qui les proposaient deja (`ScanModePicker` etant partage, un seul ajout suffit). Lecture pilotee par `NfcAdapter.enableReaderMode`, armee/desarmee sur le cycle de vie de l'ecran (`NfcScanSurface`) ; valeur retenue = contenu NDEF texte si present, sinon UID materiel en hexadecimal (`NfcValue`, pur Kotlin, teste independamment d'Android). `ScanViewModel.analyzer` devient nullable — null en NFC, ou l'ecran affiche `NfcScanSurface` a la place de la camera. Meme chemin de capture ensuite : debounce, dossier ou moteur de comparaison, feedback partage. 10 tests ajoutes (123 au total, 0 echec). **Verifie sur emulateur** : la carte NFC/RFID apparait dans le selecteur, l'ecran affiche correctement "Cet appareil ne dispose pas de puce NFC" (l'AVD n'a pas de materiel NFC — c'est le chemin de repli qui etait a verifier), aucun crash, aucune trace en logcat, torche masquee en mode NFC. Le decodage reel d'un tag reste a valider sur le Galaxy S25. | `contexte.md`, `outils.md`, `workflow.md` |
| 2026-08-21 | **Phase 8 — Comparer.** Le scan est branché sur le moteur de comparaison (`ScanViewModel.isCompare`), la liste de référence part **entièrement en rouge** et passe au vert à la lecture, avec deux compteurs colorés et une barre d'avancement partagés entre l'écran de scan et l'écran de session (`CompareSections.kt`). Enregistrement en dossier unique, export `.csv` à colonne `etat` (`CompareCsvBuilder`), `ExportManager` refactorisé pour recevoir un CSV déjà sérialisé. **Précision utilisateur en cours de phase** : seule la liste de base change de couleur, un numéro inconnu n'est pas du tout pris en compte → l'état « intrus » est supprimé du modèle (décisions 14-16). Défaut corrigé au passage : les valeurs de référence n'étaient pas normalisées à l'insertion, aucun passage au vert n'aurait été persisté. 113 tests, 0 échec. **Vérifié sur émulateur de bout en bout** : liste tout en rouge à la création, état mixte rouge/vert, écran de scan comparatif, enregistrement en dossier (3 lignes validées, `intruder_entries` vide), `.csv` produit et relu octet par octet. | `contexte.md`, `outils.md`, `workflow.md` |
| 2026-08-20 | Initialisation : cadrage validé, 8 décisions arrêtées, stack choisie, architecture définie | `contexte.md`, `outils.md`, `workflow.md` |
| 2026-08-20 | **Phases 1 à 3.** Projet Gradle complet, couche Room + DataStore + repositories, moteurs `CsvCodec` / `RegexRuleEngine` / `CompareEngine` avec 42 tests unitaires (0 échec), navigation Compose reproduisant la hiérarchie de menus. APK debug compilé (39 Mo, arm64). Décisions ajoutées : entités = modèles, comparaison insensible à la casse, filtre ABI arm64. | `contexte.md`, `outils.md`, `workflow.md` |
| 2026-08-20 | **Phase 7.** Export CSV et envoi : `ScanCsvBuilder` (colonnes, ordre chronologique), `ExportFileName` (assainissement + nom proposé horodaté), `ExportManager` (écriture en cache, `FileProvider`, Intent `ACTION_SEND`, purge à 24 h), écran d'export complet. 97 tests, 0 échec. **Vérifié sur émulateur jusqu'au fichier produit** : BOM UTF-8, séparateur `;`, CRLF, dates ISO, accents préservés dans le nom, sélecteur d'application ouvert avec la pièce jointe. | `contexte.md`, `outils.md`, `workflow.md` |
| 2026-08-20 | **Phase 6.** Dossiers finalisés : recherche insensible casse/accents (`FolderSearch`), métadonnées du dossier, états vides distincts, suppression de ligne **annulable**, copie d'une valeur, renommage et suppression de dossier avec confirmation. 75 tests, 0 échec. **Validé sur émulateur avec données réelles** injectées en base. Deux régressions visuelles corrigées : `bodyLarge` en chasse fixe contaminait tous les champs de saisie, et les couleurs inverses du thème n'étaient pas définies (snackbar violet hors charte). | `contexte.md`, `outils.md`, `workflow.md` |
| 2026-08-20 | **Phase 5.** OCR complet : `OcrAnalyzer`, éditeur de formule regex à la volée avec validation live, aperçu des correspondances, texte brut consultable, sélecteur de groupe capturant, bibliothèque de formules persistée, rechargement de la dernière formule, capture armée explicitement. 66 tests, 0 échec. **Validé sur émulateur.** Deux défauts de finition française corrigés : messages `PatternSyntaxException` traduits, et accents rétablis dans tout `strings.xml`. | `contexte.md`, `outils.md`, `workflow.md` |
| 2026-08-20 | **Phase 4.** Pipeline caméra complet : `BarcodeAnalyzer` (filtrage au décodeur), `ScanDebouncer` (11 tests), `ScanFeedback`, `CameraPreview` CameraX, `CameraPermissionGate`, `ScanViewModel`, écran de scan à deux zones. 53 tests, 0 échec. **Validé sur émulateur** : lancement, navigation, caméra liée, analyseur actif, aucune erreur. Bug de débordement de `PreviewView` détecté et corrigé (`clipToBounds`). ABI debug élargi à x86_64 pour permettre le test émulateur. | `contexte.md`, `outils.md`, `workflow.md` |

## 8. État du code au terme de la phase 8 (+ ajout NFC/RFID + vérification par consensus)

Ce qui est **réellement fonctionnel** dès maintenant :

- **Scan à la chaîne dans les quatre modes** (OCR, QR Code, code-barres, NFC/RFID) :
  décodage filtré par mode, multi-codes par image (un seul tag par lecture en NFC),
  déduplication par dossier, création paresseuse du dossier à la première capture, retour
  haptique et sonore, torche (masquée en NFC, sans objet hors caméra), écran maintenu
  allumé, suppression de ligne.
- **Vérification par consensus** *(2026-08-24, décision 19)*, en scan de dossier (OCR, QR
  Code, code-barres — pas la fonction Comparer, pas le NFC) : une valeur n'est enregistrée
  qu'après avoir été lue identique **3 fois** (QR/code-barres) ou **5 fois** (OCR).
  `ScanConsensus` tourne en synchrone sur le thread d'analyse, purge son compteur dès la
  validation, expire un compteur abandonné après 800 ms, et réinitialise deux compteurs
  quand deux valeurs à un caractère près n'ont jamais coexisté dans une même image (garde
  d'ambiguïté, désactivée en QR Code). `ScanDebouncer` (1,5 s) devient un verrou anti-rebond
  posé **après** cette validation, plutôt qu'un pré-filtre. Le réticule de `CameraPreview`
  se remplit progressivement avec l'avancement du consensus, sans aucun son avant le seuil.
- **Filtre de conformité regex étendu au QR Code et au code-barres** *(2026-08-24, décision
  20)* : même bibliothèque de formules que l'OCR, mais correspondance totale de la valeur
  (`BarcodeFilterPanel`), pas d'extraction de fragment. Optionnel — formule vide accepte
  tout, comme avant cet ajout.
- **Mode OCR** : formule regex écrite à la volée avec validation à chaque frappe, choix du
  groupe capturant, bibliothèque de formules nommées, rechargement automatique de la
  dernière formule utilisée. La capture est **armée explicitement** — voir ci-dessous.
  L'aperçu en direct et le texte brut lu ont été **retirés le 2026-10-26** (décision 26) ;
  sous le champ, trois boutons « Info », « Regex Auto », « Enregistré », communs avec le
  filtre de conformité.
- **Générateur de formules** *(2026-10-26, décision 25)* : à partir d'un ou plusieurs
  exemples de valeur, jusqu'à cinq formules commentées et vérifiées, sans IA
  (`RegexSuggester` + `RegexGeneratorSheet`), en OCR comme dans le filtre de conformité.
- **Mode NFC/RFID** : lecture évènementielle via `NfcAdapter.enableReaderMode`, armée et
  désarmée sur le cycle de vie de l'écran, contenu NDEF texte préféré à l'UID matériel
  quand il existe, message de repli si l'appareil n'a pas de puce NFC ou si elle est
  désactivée (avec raccourci vers les réglages).
- Navigation complète des trois branches de menus, du menu principal aux écrans terminaux.
- **Dossiers** : liste filtrable (recherche insensible casse/accents), métadonnées,
  consultation ligne à ligne, copie d'une valeur, suppression de ligne **annulable**,
  renommage et suppression de dossier avec confirmation, reprise de scan avec le mode
  précédent mis en avant.
- **Comparer, de bout en bout** : référence chargée depuis un dossier local ou un `.csv`
  importé (Storage Access Framework), liste affichée **entièrement en rouge**, scan dans
  les quatre modes confronté au moteur en mémoire, passage au vert immédiat avec
  compteurs et barre d'avancement, reprise de la session à l'aller-retour scan ⇄ résultat,
  enregistrement dans un dossier unique, export `.csv` à colonne `etat`.
- **Export .csv** : nom de fichier proposé puis modifiable, assainissement montré en
  direct, génération dans le cache, envoi par Intent avec pièce jointe et destinataire
  pré-rempli, purge automatique des exports de plus de 24 h.
- Écran de préférences d'envoi (destinataire, objet, message) persisté en DataStore.
- `CsvCodec`, `RegexRuleEngine`, `RegexSuggester`, `CompareEngine`, `CompareCsvBuilder`,
  `ScanCsvBuilder`, `ScanDebouncer`, `ScanConsensus`, `FolderSearch`, `ExportFileName` :
  **180 tests unitaires, 0 échec** (2026-10-26), `assembleDebug` sans avertissement.

> **Plus aucune coquille dans l'application.** Tous les écrans de la hiérarchie imposée
> sont fonctionnels.

### Limite technique connue — les codes empilés que ML Kit ne sait pas lire

Le mode **Code 2D** (décision 21) couvre QR Code, **PDF417**, Data Matrix et Aztec.
**PDF417 est le seul code empilé multi-rangées que ML Kit sache décoder.**

Les autres familles empilées restent **hors de portée, et le resteront** tant que
l'application s'appuie sur ML Kit :

| Famille | Aspect | Lisible par |
|---|---|---|
| **PDF417** | rangées contiguës, **sans** séparateur horizontal | ✅ ML Kit |
| **Codablock F** | Code 128 empilé, rangées **séparées par une barre horizontale** | ❌ ni ML Kit ni ZXing |
| **Code 16K** | idem, base Code 128, cadre complet | ❌ ni ML Kit ni ZXing |
| **Code 49** | idem, tables propres | ❌ ni ML Kit ni ZXing |
| **MicroPDF417** | PDF417 compact | ❌ ML Kit |

**Comment les distinguer d'un coup d'œil :** des **lignes noires horizontales pleines
qui séparent les rangées** ⇒ ce n'est pas du PDF417, donc ce n'est pas lisible. Un
PDF417 a ses rangées collées les unes aux autres.

L'étiquette qui a motivé la décision 21 (boîtier `BJ-568A/00090`, photo du 2026-09-15)
est précisément dans ce cas : **4 rangées séparées par des barres horizontales, dans un
cadre complet** — Codablock F, Code 16K ou Code 49. Vérifié en essayant de la décoder
avec ZXing (qui couvre PDF417, Data Matrix, Aztec et tout le 1D) : aucun résultat.

**Contournement pour ces étiquettes-là :** le **mode OCR**, qui lit le texte humainement
lisible imprimé au-dessus du code. Une formule du type `[A-Z]{2}-\d{3}[A-Z]/\d{5}`
capture `BJ-568A/00090` sans rien développer de plus.

Y répondre autrement supposerait soit un SDK commercial sous licence (incompatible avec
une application hors-ligne sans abonnement), soit l'écriture d'un décodeur maison
— localisation, redressement, segmentation des rangées puis décodage — ce qui est un
projet en soi, et fragile sur le terrain. **À ne pas entreprendre sans validation
explicite de l'utilisateur.**

### Limite fonctionnelle connue — reprise d'une comparaison interrompue

Une session de comparaison **n'est joignable que depuis sa création**. Si l'application
est fermée en cours de contrôle, la session reste en base avec son état, mais aucune
entrée de menu ne permet d'y revenir : « Comparer » recrée systématiquement une session
neuve. `CompareEngine.restoreFound` et `CompareRepository.loadEngine` couvrent l'aller-
retour entre l'écran de scan et l'écran de résultat, pas la fermeture de l'application.

Ce n'est pas un écart à la spécification — la hiérarchie de menus imposée ne prévoit pas
de liste des sessions — mais c'est une gêne réelle sur le terrain (contrôle de 60 racks
interrompu par une mise en veille). Deux pistes, **à arbitrer par l'utilisateur** :
1. reprendre automatiquement la session non enregistrée de la même source, au lieu d'en
   créer une nouvelle (aucune modification des menus) ;
2. accepter la perte et repartir de zéro (comportement actuel).

### Pourquoi la capture OCR est armée explicitement

En QR et code-barres, un code décodé est forcément un code : la capture démarre armée.
En OCR non — une formule en cours de frappe (`\d` par exemple) enregistrerait des dizaines
de valeurs parasites avant même d'être terminée. L'écran OCR reste donc en **réglage** :
rien n'est écrit, et le bouton « Démarrer la capture » n'est actif que sur une formule
valide. Toute modification de la formule **désarme** la capture. Depuis le retrait de
l'aperçu (décision 26), aucune extraction n'est faite tant que la capture est désarmée.

### Vérification sur émulateur — méthode

AVD `Medium_Phone_API_36.1`.

> ⚠️ **Le display de l'application change d'un démarrage à l'autre.** Cet émulateur expose
> deux écrans (`EMU_display_0` et un display virtuel), et l'app se retrouve tantôt sur
> `displayId=0`, tantôt sur `displayId=2`. Les identifiants SurfaceFlinger changent aussi
> à chaque boot. **Toujours les redécouvrir**, ne jamais les coder en dur :

```bash
adb shell dumpsys activity activities | grep -A1 "com.scanlist.app/.MainActivity" | grep -oE "displayId=[0-9]+"
adb shell dumpsys SurfaceFlinger --display-id
adb shell input -d <displayId> tap X Y
adb shell screencap -d <idSurfaceFlinger> -p /sdcard/v.png
```

Un tap envoyé sur le mauvais display est silencieusement ignoré — symptôme : « rien ne se
passe » alors que l'application fonctionne parfaitement.

Résultat phases 4 à 6 : lancement sans crash, navigation, caméra liée par CameraX,
analyseur actif, validation de formule en direct, bibliothèque opérationnelle, écrans
Dossiers vérifiés avec de vraies données, aucune erreur en logcat.

Le **décodage réel d'un code ou d'une étiquette reste à valider sur le Galaxy S25.**
La scène virtuelle de l'émulateur ne contient ni code ni texte lisible, et les tentatives
d'y injecter une image ont échoué : `-virtualscene-poster wall=…` / `table=…` place bien
l'affiche, mais hors du champ de la caméra à sa pose initiale, et rien ne permet de faire
pivoter cette caméra en ligne de commande (`adb emu physics` ne sait qu'enregistrer, pas
imposer une pose).

> **La même limite s'applique aux seuils du consensus (3/5, TTL 800 ms).** Sans code ni
> texte réel devant la caméra de l'émulateur, `ScanConsensus` n'a été vérifié qu'en test
> unitaire (temps simulés, valeurs synthétiques) — la logique de comptage, l'expiration et
> la garde d'ambiguïté sont couvertes, mais **pas le temps de tenue caméra réel ni le bruit
> optique effectif du S25.** Les seuils sont donc à confirmer, et au besoin ajuster, lors
> du test terrain de la phase 10, avant toute mise en service.

### Injecter des données de test dans la base

`sqlite3.exe` est fourni avec `platform-tools`. L'application étant *debuggable*,
`run-as` donne accès à son bac à sable :

```bash
adb shell am force-stop com.scanlist.app
adb exec-out run-as com.scanlist.app cat databases/scanlist.db > scanlist.db
sqlite3 scanlist.db < seed.sql          # terminer par PRAGMA wal_checkpoint(TRUNCATE);
adb push scanlist.db /data/local/tmp/scanlist.db
adb shell run-as com.scanlist.app cp /data/local/tmp/scanlist.db databases/scanlist.db
adb shell run-as com.scanlist.app rm -f databases/scanlist.db-wal
adb shell run-as com.scanlist.app rm -f databases/scanlist.db-shm
```

> `adb exec-out` et non `adb shell` : le second corrompt les binaires.
> Supprimer `-wal` et `-shm` après la copie, sinon Room rejoue l'ancien journal.

### Limite connue de l'émulateur

L'IME ne s'attache pas au display secondaire : `adb shell input text` n'atteint aucun
champ quand l'application tourne sur `displayId=2`. Les taps, eux, fonctionnent. La
logique de saisie doit donc être couverte par des tests unitaires — c'est la raison
d'être de `FolderSearch`, extrait du ViewModel pour rester testable sans Android.

## 9. Protocole de reprise après `/clear`

1. Lire `consignes/contexte.md` → périmètre et décisions validées.
2. Lire `consignes/outils.md` → stack et commandes de build.
3. Lire ce fichier → architecture + tableau § 6 pour identifier la prochaine phase ⬜.
4. Vérifier l'état réel du code (`ls app/src/main/java/com/scanlist/app`) avant de conclure.
5. **Ne jamais** changer une décision du § 5 de `contexte.md` sans validation explicite
   de l'utilisateur.
