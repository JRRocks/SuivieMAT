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
GitHub. Le push ne déclenche pas le workflow sur ce dépôt ; le lancer à la main
(`actions_run_trigger`, `run_workflow`, `ref` = la branche). Il dépose l'APK dans
`release/` de la branche, signé par la clé de debug versionnée — voir `outils.md` § 6 bis.

### Commandes (poste local Windows avec Android Studio)

Compiler l'APK de debug :

```bash
cd "C:/Users/Taiepii/Desktop/Claude Code/ScanList" && JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```

Lancer tous les tests unitaires :

```bash
cd "C:/Users/Taiepii/Desktop/Claude Code/ScanList" && JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest
```

Lancer une seule classe de test (ex. `ScanConsensusTest`) :

```bash
cd "C:/Users/Taiepii/Desktop/Claude Code/ScanList" && JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest --tests "com.scanlist.app.ScanConsensusTest"
```

Installer sur un appareil branché :

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r "C:/Users/Taiepii/Desktop/Claude Code/ScanList/app/build/outputs/apk/debug/app-debug.apk"
```

> `java` n'est **pas** dans le PATH : sans `JAVA_HOME`, `gradlew` échoue immédiatement.

---

## Architecture en une page

**MVVM + Repository**, une seule `Activity`, navigation intégralement Compose.

```
ui/          Compose : un écran = un composable + un ViewModel Hilt
domain/      logique pure, testable sans Android (CompareEngine, RegexRuleEngine,
             ScanValue, FolderSearch, NfcValue) — c'est là que vivent les tests
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

Phases 0 à 8 terminées : scan des quatre modes (OCR, QR Code, code-barres, NFC/RFID
ajouté après la phase 8), formules regex OCR à la volée (étendues au filtre de conformité
QR/code-barres), vérification par consensus contre les erreurs optiques, dossiers, export
`.csv` par mail, fonction Comparer complète. **153 tests, 0 échec, aucun avertissement de
compilation** (vérifié le 2026-09-25). Aucune coquille ne subsiste dans l'application.

Restent les phases 9 (finitions) et 10 (test terrain sur le S25) — voir le tableau § 6 de
`consignes/workflow.md`, qui fait foi.

> **Avant toute distribution aux magasiniers**, dérouler la liste du § 0 de
> `consignes/plan-suivimat.md` — en tête : **créer la clé de signature de release**,
> reportée volontairement par l'utilisateur jusqu'à ce moment-là.
