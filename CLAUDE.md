# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Application Android native de **scan à la chaîne** (QR, code-barres, OCR, NFC/RFID) pour
l'inventaire et le contrôle de matériel. Kotlin + Jetpack Compose, 100 % hors-ligne,
interface en **français uniquement**.

> **Identité de l'application** : `applicationId` = `com.suiviemat.app`, nom affiché
> « SuivieMAT ». **ScanList est une application indépendante**, sans rapport avec ce
> dépôt — le *namespace* Kotlin du code source, lui, reste `com.scanlist.app`
> (héritage du projet d'origine, sans effet sur l'identité Android). Voir décision 23
> de `consignes/contexte.md`.

---

## Règle absolue du projet

> Le dossier **`consignes/`** est la mémoire longue durée de ce projet. Il doit être
> **mis à jour à chaque évolution majeure**, et systématiquement avant et après un `/clear`.

| Fichier | Contenu | Quand le mettre à jour |
|---|---|---|
| `consignes/contexte.md` | But de l'application, périmètre, hiérarchie de menus imposée, **décisions validées** | dès qu'une décision fonctionnelle change |
| `consignes/workflow.md` | Architecture, modèle de données, tableau d'avancement (§ 6), journal (§ 7) | à chaque phase terminée |
| `consignes/outils.md` | Stack, versions figées, permissions, commandes, état de la chaîne de build | à chaque ajout ou montée de version d'une dépendance |
| `consignes/plan-suivimat.md` | Évolution SuivieMAT (suivi matériel sous contraintes comptables) : faisabilité, questions ouvertes, décisions **à valider**, feuille de route S0–S9 | à chaque réponse, décision tranchée ou étape terminée |

**Ne jamais modifier une décision du § 5 de `contexte.md` sans validation explicite de
l'utilisateur.** Ces décisions viennent de lui ; certaines ont déjà été révisées par lui
(la n° 3 par la n° 14), ce qui se fait en ajoutant une ligne, pas en réécrivant l'histoire.

### Reprise après `/clear`

1. `consignes/contexte.md` → périmètre et décisions.
2. `consignes/outils.md` → stack et commandes de build.
3. `consignes/workflow.md` → architecture + tableau § 6 pour la prochaine phase ⬜.
4. `consignes/plan-suivimat.md` → évolution SuivieMAT en cours d'étude (§ 11 à § 14).
5. Vérifier l'état **réel** du code avant de conclure quoi que ce soit.

---

## Environnement de build

Le développement principal se fait **localement sur Windows** (Android Studio, JDK fourni
par l'IDE). Voir `consignes/outils.md` § 1 pour le détail vérifié sur ce poste.

Dans une **session cloud (claude.ai/code, web ou mobile)**, il n'y a ni SDK Android ni
`ANDROID_HOME`, et `gradlew` n'a pas le bit exécutable après un clone frais
(`chmod +x gradlew` avant tout `./gradlew`). Un build complet (`assembleDebug`,
`testDebugUnitTest`) n'y est donc **pas exécutable d'emblée** — vérifier l'environnement
avant de promettre un résultat de build, et le dire explicitement si ce n'est pas possible
plutôt que de simuler un résultat. Si la politique réseau le permet, le SDK s'installe
dans le scratchpad et Maven Central se contourne par un miroir en cas de `429` : méthode
vérifiée le 2026-09-24 dans `consignes/outils.md` § 6 ter.

**L'utilisateur n'a pas toujours d'ordinateur** : l'APK de test se produit alors sur
GitHub. Le push déclenche le workflow depuis le 2026-10-10 (il ne le faisait pas
avant) ; s'il ne part pas, le lancer à la main (`actions_run_trigger`, `run_workflow`,
`ref` = la branche). Le 2026-10-26, GitHub avait refusé les exécutions faute de
minutes ; le dépôt est **public depuis le 2026-10-27** et la CI compile de nouveau
(exécution n° 13 réussie, voir `outils.md` § 6 bis). Il dépose l'APK dans
`release/` de la branche, signé par la clé de debug versionnée — voir `outils.md` § 6 bis.
Depuis la 1.10 (2026-10-28), c'est l'APK **optimisé** (`assembleOptimise`, R8, non
debuggable), sous le même nom `SuivieMAT-debug.apk`.

**Règle de l'utilisateur (2026-10-28) : à la fin de chaque commit poussé, donner le lien
vers la page GitHub de l'APK** — la page du fichier, pas le lien de téléchargement direct :
<https://github.com/JRRocks/SuivieMAT/blob/claude/remove-scanlist-history-a5bqzh/release/SuivieMAT-debug.apk>
(adapter le nom de branche si le travail se fait ailleurs). Préciser si l'APK de cette page
correspond déjà au dernier commit ou si la CI est encore en train de le compiler.

### Miroir Google Drive (règles du dossier `2 - Jean/Claude Code/`)

Le Drive de l'utilisateur est une **seconde sauvegarde**, à sens unique : local → Drive,
jamais d'édition directe sur le Drive. Son propre `CLAUDE.md` (à la racine de
`2 - Jean/Claude Code/`) fait foi. Pour ce projet :

| Dossier Drive | Source locale |
|---|---|
| `2 - Jean/Claude Code/SuivieMAT/` | `C:\Users\Taiepii\Desktop\Claude Code\SuivieMat` (clone GitHub dans `repo\`) |

> ⚠️ `C:\Users\Taiepii\Desktop\Claude Code\ScanList` est l'application **ScanList
> indépendante** (`JRRocks/ScanList`) : ne jamais en faire la source de ce miroir, le
> `/MIR` écraserait SuivieMAT sur le Drive.

Resynchroniser **après chaque changement livré** (nouveau commit, nouvelle version), depuis
le poste Windows, Drive monté en `G:` (`Mon Drive` ou `My Drive` selon le PC :
`Test-Path "G:\My Drive"`) :

```powershell
robocopy "C:\Users\Taiepii\Desktop\Claude Code\SuivieMat" "G:\<Drive root>\2 - Jean\Claude Code\SuivieMAT" /MIR /XD .git .gradle .kotlin build .idea /XF local.properties *.apk *.log /R:1 /W:1
Copy-Item "app\build\outputs\apk\debug\app-debug.apk" "G:\<Drive root>\2 - Jean\Claude Code\APK release\SuivieMAT.<versionName>.apk"
```

`/MIR` supprime côté Drive ce qui n'existe plus en local : vérifier la destination.
Codes de sortie robocopy 0 à 7 = succès. **Monter `versionName`** à chaque APK livré,
pour qu'un APK n'en écrase jamais un autre dans `APK release/` (ni de copie dans le
dossier miroir : les APK vont dans `APK release/`).

**Depuis une session cloud**, ni robocopy ni montage `G:` : le connecteur Google Drive ne
sait ni envoyer un APK (≈ 40 Mo), ni remplacer un fichier existant. La session pousse
sur GitHub et dépose l'APK dans `release/` ; la copie vers le Drive se fait à la
prochaine session sur le PC.

### Commandes (poste local Windows avec Android Studio)

Compiler l'APK de debug :

```bash
cd "C:/Users/Taiepii/Desktop/Claude Code/SuivieMat/repo" && JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```

Lancer tous les tests unitaires :

```bash
cd "C:/Users/Taiepii/Desktop/Claude Code/SuivieMat/repo" && JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest
```

Lancer une seule classe de test (ex. `ScanConsensusTest`) :

```bash
cd "C:/Users/Taiepii/Desktop/Claude Code/SuivieMat/repo" && JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.scanlist.app.ScanConsensusTest"
```

Installer sur un appareil branché :

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r "C:/Users/Taiepii/Desktop/Claude Code/SuivieMat/repo/app/build/outputs/apk/debug/app-debug.apk"
```

Compiler l'APK optimisé (R8, non debuggable, même clé : remplace le debug sans perte de
données, ~4× plus rapide au démarrage — mesures dans `consignes/outils.md` § 7) :

```bash
cd "C:/Users/Taiepii/Desktop/Claude Code/SuivieMat/repo" && JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleOptimise
```

> `java` n'est **pas** dans le PATH : sans `JAVA_HOME`, `gradlew` échoue immédiatement.

---

## Architecture en une page

**MVVM + Repository**, une seule `Activity`, navigation intégralement Compose.

```
ui/          Compose : un écran = un composable + un ViewModel Hilt
domain/      logique pure, testable sans Android (CompareEngine, RegexRuleEngine,
             RegexSuggester, ScanValue, FolderSearch, FolderTimeline, CompareOverview,
             NfcValue) — c'est là que vivent les tests
data/        Room (entités = modèles métier), DataStore, CsvCodec, repositories
scanner/     CameraX + ML Kit + NFC, aucune dépendance à l'UI
export/      écriture du .csv en cache + Intent de partage
```

Le détail complet (arbre des packages, schéma des tables, pipeline de scan) est dans
`consignes/workflow.md`. Ce fichier-ci ne le duplique pas.

---

## Ce qui compte vraiment dans ce code

### Le chemin critique du scan ne doit jamais être suspendu

C'est la raison d'être de l'application : relever dix marquages en quelques secondes sans
reposer la main sur l'écran. Concrètement :

- le filtrage par mode est fait dans les `BarcodeScannerOptions`, **pas** en
  post-traitement : un code du mauvais type n'est même pas décodé ;
- `ScanConsensus` puis `ScanDebouncer` tournent **en synchrone sur le thread d'analyse** —
  une coroutine par frame coûterait plus cher que le décodage ;
- l'écriture en base part sur `viewModelScope`, jamais depuis l'analyseur ;
- `CompareEngine` tranche **en mémoire**, sans requête SQL, pour la même raison.

Toute modification qui ajoute une suspension entre la détection et le retour à l'analyse
est une régression, même si les tests passent.

### Les règles métier sont appliquées par le moteur SQL, pas par du code

L'index unique `(folderId, value)` + `OnConflictStrategy.IGNORE` **est** la règle « pas de
doublon par dossier ». Ne pas la réimplémenter par un `select` préalable : deux frames
analysées coup sur coup produiraient une course.

### La normalisation est faite à l'écriture

`ScanValue.normalize` (trim + espaces internes réduits) s'applique **à l'insertion**, pour
`scan_items` comme pour `reference_entries`. `markReference` retrouve sa ligne par égalité
stricte : une valeur de référence non normalisée en base ne passerait jamais au vert.

### Le code couleur de « Comparer » est imposé par l'utilisateur

**Seule la liste de référence change de couleur.** Elle naît **entièrement en rouge**
(présence à valider) et chaque entrée passe au **vert** quand elle est lue. Une valeur lue
qui ne figure pas dans la liste **n'est pas prise en compte** : rien n'est enregistré, aucun
compteur ne bouge, aucune ligne n'apparaît. Elle est seulement signalée sur le moment (bip
grave + bandeau gris fugace).

Le gris n'est donc **pas un état** : il n'habille que des bandeaux neutres et transitoires.

### La vérification par consensus protège du bruit optique, pas la fonction Comparer

En scan de dossier (OCR, QR Code, code-barres), une valeur n'est enregistrée qu'après avoir
été lue identique **3 fois** (QR/code-barres) ou **5 fois** (OCR) — `ScanConsensus`. Cette
portée est **volontairement limitée** : la fonction Comparer garde son verdict sur une seule
lecture, et le mode NFC n'est pas concerné (pas de bruit optique). Ne pas étendre ce
comportement sans validation explicite de l'utilisateur (décision 19 de `contexte.md`).

### Les cas d'erreur : ne rien faire

Décodage échoué, valeur vide, texte illisible → aucun message, aucun enregistrement, le
scan continue. Cette règle vient de l'utilisateur et remonte jusque dans le moteur
(`CompareOutcome.Ignored`, `ScanRecordResult.Ignored`).

---

## Conventions d'écriture

- **Interface intégralement en français, avec les accents**, et **toujours** via
  `strings.xml`. Un ViewModel porte un `@StringRes`, jamais un texte. Les messages de la
  JVM qui ne sont pas localisables (`PatternSyntaxException`) sont traduits à la main dans
  `RegexRuleEngine`.
- **Code et commentaires sans accents**, pour éviter tout aléa d'encodage entre les outils.
  L'accentuation est réservée aux ressources et aux fichiers `.md`.
- **Les commentaires expliquent le pourquoi**, jamais le quoi. Un commentaire qui
  paraphrase la ligne suivante est du bruit ; un commentaire qui dit « `clipToBounds`
  obligatoire, sinon `PreviewView` déborde par-dessus la liste » sauve une heure.
- **Les tests sont nommés en français**, en phrase, entre backticks — ils se lisent comme
  une spécification : ``fun `un element jamais lu reste rouge`()``.
- La logique testable est **extraite dans `domain/`**. C'est ce qui a motivé l'existence de
  `FolderSearch` et `NfcValue` : l'IME de l'émulateur ne permettant pas de tester la saisie,
  et l'émulateur n'ayant pas de puce NFC, cette logique est sortie du ViewModel / du SDK
  Android pour rester testable en JVM pur.

---

## Vérification : les tests ne suffisent pas

Chaque phase est validée **sur émulateur**, écran par écran, jusqu'au fichier produit
lorsqu'il y en a un. Plusieurs défauts réels n'ont été trouvés que comme ça : le
débordement de `PreviewView`, la chasse fixe qui contaminait tous les champs de saisie, le
snackbar en violet Material hors charte.

`consignes/workflow.md` § 8 donne la méthode complète (découverte du display, injection de
données en base par `run-as` + `sqlite3`). Trois pièges à connaître :

| Piège | Symptôme | Remède |
|---|---|---|
| Display secondaire | les taps « ne font rien » | redécouvrir le `displayId` à chaque boot, `adb shell input -d <id> tap` |
| Git Bash réécrit les chemins distants | `secure_mkdirs() failed` sur `/data/local/tmp` | préfixer par `MSYS_NO_PATHCONV=1` |
| Journal WAL | la base rapatriée ne contient pas les écritures récentes | rapatrier aussi `-wal` et `-shm` |

**Le décodage réel d'un code, d'une étiquette ou d'un tag NFC n'a jamais pu être exercé** :
la scène virtuelle de l'émulateur ne contient ni code ni texte lisible, et les AVD
standards n'émulent pas de puce NFC — `NfcAdapter.getDefaultAdapter` y renvoie `null`, ce
qui n'exerce que le chemin de repli (`NfcScanSurface`), pas la lecture elle-même. De même,
les seuils du consensus (3/5, TTL 800 ms) ne sont couverts qu'en test unitaire (temps
simulés). Cette validation ne peut se faire que sur le Galaxy S25.

---

## État d'avancement

Phases 0 à 9 terminées : scan des quatre modes (OCR, QR Code, code-barres, NFC/RFID
ajouté après la phase 8), formules regex OCR à la volée (étendues au filtre de conformité
QR/code-barres, avec un générateur de formules à partir d'un exemple, sans IA — décision
25), vérification par consensus contre les erreurs optiques, dossiers, export `.csv` par
mail, fonction Comparer complète, finitions de la phase 9 (permission caméra bloquée,
rotation suivie par l'analyse, OCR au repos hors capture, icône à thème). **206 tests,
0 échec, aucun avertissement de
compilation** (vérifié le 2026-10-28). **Piège de navigation** : une route fixe ne doit
jamais correspondre au modèle d'une autre (`scan/new` ouvrait l'écran `scan/{mode}`,
correctif 1.9) — `RoutesTest` le vérifie. Aucune coquille ne subsiste dans l'application.

**Refonte de l'interface terminée** (décision 27, phase 11 de `workflow.md`, non vérifiée
sur téléphone) : style
« Bleu de travail » appliqué à tous les écrans (étape 1/7), puis le menu principal
remplacé par une **barre de navigation** en bas (Dossiers · Comparer · Nouveau scan),
l'application s'ouvrant sur Nouveau scan (étape 2/7, décision 28), et le choix du
mode présenté par échantillon d'étiquette dessiné (étape 3/7), et l'écran de scan à
caméra pleine hauteur avec tiroir des résultats et formule en pastille (étape 4/7,
décision 29), puis les dossiers rangés par date avec une fiche centrée sur « Reprendre
le scan » (étape 5/7), et l'onglet Comparer en liste par date portant l'avancement des
comparaisons, enfin reprenables (étape 6/7, décision 30), enfin les fenêtres de
formules où l'on coche avant de valider par un seul bouton (étape 7/7, décision 31).
Chaque étape a proposé cinq variantes à l'utilisateur sur le canevas de maquettes.
Conventions du
style : en-tête bleu via `ScanListScaffold` (jamais de `TopAppBar`), compteur dans
l'en-tête (`headerTrailing` / `headerBottom`), boutons via `PrimaryButton` /
`SecondaryButton`, valeurs en `ScanValueTextStyle`, formules en `PatternTextStyle`,
feuilles via `SheetHeader` / `SheetActionBar` / `ChoiceRow` (`ui/scan/FormulaSheetParts.kt`).

Reste la phase 10 (test terrain sur le S25) — voir le tableau § 6 de
`consignes/workflow.md`, qui fait foi.

> **Avant toute distribution aux magasiniers**, dérouler la liste du § 0 de
> `consignes/plan-suivimat.md` — en tête : **créer la clé de signature de release**,
> reportée volontairement par l'utilisateur jusqu'à ce moment-là.
