# Workflow — ScanList

> **Fichier de mémoire longue durée.** Architecture technique et suivi d'avancement.
> **Règle absolue :** mettre à jour le tableau d'avancement (§ 6) et le journal (§ 7)
> à chaque évolution majeure, et systématiquement avant/après un `/clear`.
>
> Dernière mise à jour : 2026-09-24 — *Écran « Test du badge » (décision 22) ; étude SuivieMAT dans
> `plan-suivimat.md`*

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

```
com.scanlist.app
├── data
│   ├── db          (ScanListDatabase, entity/Entities.kt, dao/, Converters)
│   ├── prefs       (SettingsDataStore, EmailPrefs)
│   ├── csv         (CsvCodec RFC 4180, ScanCsvBuilder, CompareCsvBuilder)  ✅ phase 8
│   └── repository  (Folder / Scan / RegexRule / Compare)
├── domain
│   ├── model       (Enums : ScanMode, FolderKind, MatchState… + ScanValue)
│   ├── compare     (CompareEngine, CompareOutcome)
│   ├── regex       (RegexRuleEngine : validation, extraction)
│   ├── search      (FolderSearch : filtre casse + accents)              ✅ phase 6
│   ├── nfc         (NfcValue : choix NDEF/UID, decodage RTD_TEXT — pur, testable)
│   └── card        (test du badge, décision 22 : Apdu + CardChannel [chaînage,
│                    GET RESPONSE], BerTlv, CardDirectory [EF.DIR], Piv,
│                    CardCertificate, CardChallenge, CardDiagnostic — pur, testé
│                    contre une carte PIV simulée)                        ✅ 2026-09-24
├── di              (AppModule : Room + DAO)
├── scanner
│   ├── BarcodeAnalyzer       (ImageAnalysis.Analyzer, filtré par mode)  ✅ phase 4
│   ├── ScanDebouncer         (verrou anti-rebond post-validation)       ✅ phase 4, rôle révisé le 2026-08-24
│   ├── ScanConsensus         (validation par lectures identiques + garde d'ambiguïté) ✅ 2026-08-24
│   ├── ScanFeedback          (vibration + bip)                          ✅ phase 4
│   ├── OcrAnalyzer           (ImageAnalysis.Analyzer, texte brut)       ✅ phase 5
│   ├── NfcTagDecoder         (pont Tag Android → NfcValue)
│   └── CardTestReader        (pont IsoDep → CardDiagnostic, décision 22)
├── ui
│   ├── theme       (ScanListTheme, CompareColors : rouge = à valider,
│   │                vert = validé ; gris réservé aux bandeaux neutres)
│   ├── navigation  (Routes, ScanListNavHost)
│   ├── common      (ScanListScaffold, MenuCard, ScanModePicker…)
│   ├── menu        (MainMenuScreen)
│   ├── folders     (liste filtrable, actions, détail, export + VM)      ✅ phase 6
│   ├── scan        (CameraPreview [réticule à remplissage progressif],
│   │                CameraPermissionGate, ScanViewModel, ScanScreens,
│   │                OcrPanel [OcrControlPanel + BarcodeFilterPanel +
│   │                RegexLibrarySheet, partagée], NfcScanSurface)       ✅ phases 4-5
│   ├── compare     (source, session, CompareSections partagées,
│   │                CompareStartViewModel, CompareSessionViewModel)   ✅ phase 8
│   ├── settings    (EmailPrefsScreen)
│   └── cardtest    (CardTestScreen, CardTestViewModel, CardReportFormatter —
│                    APK de debug uniquement, décision 22)
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
| Affichage partagé | `ui/compare/CompareSections.kt` | compteurs + sections, utilisés par l'écran de scan **et** l'écran de session |
| Écran de session | `ui/compare/CompareSessionScreen` + `CompareSessionViewModel` | résultat, enregistrement, export |

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
| 9 | **Finitions** | Permissions, torche, rotation, écran allumé, gestion mémoire, icône | ⬜ À faire |
| 10 | **Build & test terrain** | APK debug, installation sur Galaxy S25, test sur 10 batteries | ⬜ À faire |

## 7. Journal des évolutions

| Date | Évolution | Fichiers de consignes impactés |
|---|---|---|
| 2026-09-25 | **APK de test compilé sur GitHub et déposé dans `release/`.** Demandé par l'utilisateur, sans ordinateur sous la main. Le push ne déclenche pas le workflow sur ce dépôt, mais le lancement manuel fonctionne (y compris depuis une session cloud). Le workflow dépose désormais l'APK dans `release/SuivieMAT-debug.apk` de la branche, avec commit source, date et SHA-256 (`release/SuivieMAT-debug.txt`), en arm64 seul (`-Papk.phoneOnly`) pour limiter le poids de l'historique. **Clé de debug versionnée** (`app/debug.keystore`) : sans elle, chaque runner signait avec une clé jetable et chaque APK de test aurait imposé une désinstallation, donc la perte des dossiers du téléphone. Passage unique : l'application installée avant depuis Android Studio doit être désinstallée une fois. Mode d'emploi : `release/LISEZMOI.md`. 205 tests, 0 échec, en local avant le push. | `outils.md`, `plan-suivimat.md` |
| 2026-09-24 | **Écran « Test du badge » (décision 22).** Demandé par l'utilisateur pour éprouver physiquement l'identification par badge professionnel sans contact (étude SuivieMAT, `plan-suivimat.md` § 10 ter). Entrée au menu principal réservée aux APK de debug (`BuildConfig.DEBUG`, `buildConfig` activé). L'écran lit le répertoire EF.DIR, sonde les applications PIV et PKCS#15, puis, sur une carte PIV : **niveau 2** = lecture du certificat de la carte (`5FC101`, gzip géré), **niveau 3** = défi-réponse sur la clé `9E` (nonce aléatoire de 32 octets, bloc PKCS#1 v1.5 construit côté téléphone pour RSA, empreinte pour ECC, signature vérifiée avec la clé du certificat). **Aucune commande de vérification de PIN n'existe dans le code** : le test ne peut pas bloquer le badge (couvert par un test). Rapport copiable pour transmission. Toute la logique dans `domain/card/`, pure et testée contre une carte PIV simulée qui reproduit le chaînage de commandes et les réponses longues (`61xx`) ; `scanner/CardTestReader` n'est que le pont `IsoDep`. 52 tests ajoutés (**205 au total, 0 échec**), `assembleDebug` sans avertissement — **compilés en session cloud** pour la première fois (SDK installé dans le scratchpad, miroir Maven, `outils.md` § 6 ter). **Non vérifié sur émulateur** (aucun émulateur en session cloud, pas de NFC sur les AVD) : c'est le test physique de l'utilisateur qui validera l'écran. La CI se déclenche désormais aussi sur la branche de l'étude. | `contexte.md`, `outils.md`, `workflow.md`, `plan-suivimat.md` |
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
- **Mode OCR** : formule regex écrite à la volée avec validation à chaque frappe, aperçu
  en direct des correspondances sur le flux caméra, texte brut lu consultable, choix du
  groupe capturant, bibliothèque de formules nommées, rechargement automatique de la
  dernière formule utilisée. La capture est **armée explicitement** — voir ci-dessous.
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
- `CsvCodec`, `RegexRuleEngine`, `CompareEngine`, `CompareCsvBuilder`, `ScanCsvBuilder`,
  `ScanDebouncer`, `ScanConsensus`, `FolderSearch`, `ExportFileName` : **141 tests
  unitaires, 0 échec**, `assembleDebug` sans avertissement.

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
l'aperçu tourne en continu, rien n'est écrit, et le bouton « Démarrer la capture » n'est
actif que sur une formule valide. Toute modification de la formule **désarme** la capture.

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
