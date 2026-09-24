# Outils — ScanList

> **Fichier de mémoire longue durée.** Inventaire des technologies et librairies du projet.
> À mettre à jour **à chaque ajout, suppression ou montée de version d'une dépendance**.
>
> Dernière mise à jour : 2026-09-24 — *`buildConfig` activé (écran de test du badge,
> décision 22), CI étendue à la branche SuivieMAT, compilation possible en session
> cloud* — aucune dépendance ajoutée, voir § 3, § 6 bis et § 6 ter

---

## 1. Environnement de développement (vérifié sur le poste)

| Élément | Valeur détectée | Chemin |
|---|---|---|
| OS | Windows 11 Home (10.0.26200) | — |
| IDE | Android Studio | `C:\Program Files\Android\Android Studio` |
| JDK | OpenJDK 21.0.8 (JetBrains Runtime) | `C:\Program Files\Android\Android Studio\jbr` |
| Android SDK | `platforms/android-36` | `%LOCALAPPDATA%\Android\Sdk` |
| Build-tools | 35.0.0 et 36.1.0 | idem |
| Émulateur | présent (`emulator`, `system-images`) | idem |
| Gradle | **absent du PATH** → utiliser le *Gradle Wrapper* (`gradlew.bat`) | généré par le projet |

> ⚠️ `java` n'est pas dans le PATH système. Les builds en ligne de commande doivent
> définir `JAVA_HOME` sur le JBR d'Android Studio, ou passer par `local.properties` /
> l'IDE. Voir `workflow.md` § Build.

## 2. Choix de stack — décision et justification

| Besoin | Techno retenue | Pourquoi ce choix |
|---|---|---|
| Langage | **Kotlin** | Standard Android, coroutines natives pour le pipeline caméra |
| UI | **Jetpack Compose + Material 3** | Écrans à état réactif ; l'aperçu regex en direct et le passage rouge → vert de la liste de référence se recomposent naturellement |
| Caméra | **CameraX** | Gère les spécificités constructeur (Samsung), `ImageAnalysis` avec back-pressure = indispensable au scan à la chaîne |
| Codes 1D/2D | **ML Kit Barcode Scanning** (modèle *bundled*) | Multi-codes par frame en une passe, filtrage strict par format = base du mode exclusif QR / Code-barres. Bundled ⇒ **fonctionne hors-ligne dès l'installation** |
| OCR | **ML Kit Text Recognition v2** (script latin, *bundled*) | Meilleur compromis vitesse/précision sur flux vidéo, hors-ligne |
| Base de données | **Room** (SQLite) | Contrainte d'unicité SQL `(folderId, value)` ⇒ la déduplication par dossier est garantie par le moteur, pas par du code applicatif. `Flow` pour l'affichage temps réel de la liste pendant le scan |
| Préférences | **DataStore (Preferences)** | Destinataire mail par défaut, objet, dernier mode de scan, dernière regex utilisée |
| Navigation | **Navigation Compose** | Traduction directe de la hiérarchie de menus imposée |
| Injection | **Hilt** | Câblage ViewModels / DAO / analyseurs caméra |
| Asynchrone | **Coroutines + Flow** | — |
| Partage de fichier | **FileProvider** + `Intent.ACTION_SEND` | Export mail sans stocker d'identifiants |
| CSV | **Implémentation interne** (`CsvCodec`) | Le besoin (RFC 4180 : guillemets, virgules, retours ligne, BOM UTF-8 pour Excel FR) est trop simple pour justifier une dépendance externe |

### Alternatives écartées

- **ZXing** au lieu de ML Kit → moins bon en basse lumière et pas d'OCR ; ML Kit couvre les deux besoins avec la même API caméra.
- **Flutter / React Native** → l'utilisateur demande explicitement du **natif**, et le pipeline `ImageAnalysis` temps réel est plus fiable en natif.
- **ML Kit *unbundled*** (via Google Play Services) → APK plus léger (~4 Mo gagnés) mais téléchargement du modèle au premier lancement : incompatible avec l'exigence hors-ligne.
- **DataStore seul, sans Room** → pas de contrainte d'unicité ni de requêtes ; rejeté.
- **Apache Commons CSV** → dépendance lourde pour un encodage trivial.

## 3. Configuration du projet

| Paramètre | Valeur |
|---|---|
| `applicationId` | `com.scanlist.app` |
| `minSdk` | 26 (Android 8.0) |
| `targetSdk` / `compileSdk` | 36 |
| Build system | Gradle Kotlin DSL + `libs.versions.toml` (version catalog) |
| Processeur d'annotations | KSP (Room, Hilt) |
| Une seule Activity | `MainActivity` — navigation 100 % Compose |
| `abiFilters` | release : `arm64-v8a` · debug : `arm64-v8a` + `x86_64` — voir décision n° 10 de `contexte.md` |
| Java / Kotlin JVM target | 17 |
| `buildFeatures.buildConfig` | `true` depuis le 2026-09-24 : `BuildConfig.DEBUG` réserve l'écran « Test du badge » aux APK de debug (décision 22 de `contexte.md`) |

## 4. Dépendances

> Les numéros de version sont **centralisés dans `gradle/libs.versions.toml`**.
> **Versions figées et validées par un build réussi le 2026-08-20 :**
> Gradle 8.13 · AGP 8.13.2 · Kotlin 2.0.21 · KSP 2.0.21-1.0.28 · Hilt 2.52 · Room 2.6.1 ·
> Compose BOM 2024.09.00 · Navigation Compose 2.8.2 · CameraX 1.3.4 ·
> ML Kit barcode-scanning 17.3.0 · ML Kit text-recognition 16.0.1 · DataStore 1.1.1 ·
> Lifecycle 2.8.6 · Coroutines 1.8.1.
>
> Le wrapper Gradle a été repris du projet `AndroidStudioProjects/AstreTracker`,
> la distribution 8.13 étant déjà en cache local.

| Groupe | Artefact | Rôle |
|---|---|---|
| Compose | `androidx.compose:compose-bom` | Alignement des versions Compose |
| Compose | `androidx.compose.material3:material3` | Composants Material 3 |
| Compose | `androidx.compose.material:material-icons-extended` | Icônes des menus |
| Compose | `androidx.activity:activity-compose` | Hôte Compose + permissions |
| Compose | `androidx.lifecycle:lifecycle-viewmodel-compose` | ViewModels |
| Compose | `androidx.lifecycle:lifecycle-runtime-compose` | `collectAsStateWithLifecycle` |
| Navigation | `androidx.navigation:navigation-compose` | Arborescence de menus |
| CameraX | `androidx.camera:camera-core` | Cœur |
| CameraX | `androidx.camera:camera-camera2` | Implémentation Camera2 |
| CameraX | `androidx.camera:camera-lifecycle` | Liaison au cycle de vie |
| CameraX | `androidx.camera:camera-view` | `PreviewView` |
| ML Kit | `com.google.mlkit:barcode-scanning` | QR + codes-barres (bundled) |
| ML Kit | `com.google.mlkit:text-recognition` | OCR latin (bundled) |
| Room | `androidx.room:room-runtime` | Base SQLite |
| Room | `androidx.room:room-ktx` | Coroutines / Flow |
| Room | `androidx.room:room-compiler` (KSP) | Génération des DAO |
| DataStore | `androidx.datastore:datastore-preferences` | Préférences d'envoi |
| Hilt | `com.google.dagger:hilt-android` | Injection |
| Hilt | `com.google.dagger:hilt-compiler` (KSP) | Génération |
| Hilt | `androidx.hilt:hilt-navigation-compose` | ViewModels par destination |
| Coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | Concurrence |
| Test | `junit:junit` | Tests unitaires (regex, CSV, déduplication, comparaison) |
| Test | `org.jetbrains.kotlinx:kotlinx-coroutines-test` | Tests de flux |
| Test | `androidx.room:room-testing` | Migrations |

## 5. Permissions Android

| Permission | Usage | Obligatoire |
|---|---|---|
| `android.permission.CAMERA` | Scan QR / code-barres / OCR | ✅ Oui — demandée à l'exécution |
| `android.permission.VIBRATE` | Retour haptique du scan à la chaîne | ✅ Oui — normale, pas de prompt |
| `android.permission.NFC` | Scan NFC/RFID (`NfcAdapter.enableReaderMode`) | ✅ Oui — normale, pas de prompt |
| Aucune permission stockage | Import CSV via `ACTION_OPEN_DOCUMENT`, export via `FileProvider` (Storage Access Framework) | — |

> `<uses-feature android:name="android.hardware.nfc" android:required="false" />` :
> un appareil sans puce NFC installe quand même l'application ; le mode NFC affiche
> alors un message au lieu de planter (voir `NfcScanSurface`).

> **Aucune permission réseau** n'est déclarée : l'application est intégralement hors-ligne.
> L'envoi mail est délégué à une application tierce via Intent.

## 6. Dépôt et contrôle de version

Le projet est versionné avec Git et hébergé sur GitHub depuis le 2026-09-03 :
**https://github.com/JRRocks/ScanList** (branche `main`).

- Identité git configurée **localement au dépôt** (pas globalement) : `JRRocks` /
  `jean.rox.unio@gmail.com`.
- `.gitignore` corrigé au passage : le motif `/build` hérité n'était ancré qu'à la racine
  et ne couvrait pas `app/build` (sorties de compilation, caches Kotlin, APK) — remplacé
  par `build/` pour couvrir tous les modules.
- Aucun secret dans le dépôt : `local.properties` (chemin du SDK) reste ignoré, aucun
  keystore ni fichier `google-services.json` dans le projet.
- Poussée initiale : 87 fichiers, commit unique reprenant l'état des phases 0 à 8.

Pour continuer le travail depuis une session Claude Code cloud (claude.ai/code, web ou
mobile) plutôt qu'en local : connecter le compte GitHub `JRRocks` dans l'interface
claude.ai/code, puis sélectionner le dépôt `ScanList`. Les changements faits en local
doivent être poussés (`git push`) pour rester synchronisés avec le cloud, et
inversement.

## 6 bis. Intégration continue — compiler sans poste Windows

Workflow **`.github/workflows/build-debug-apk.yml`**, ajouté le 2026-09-05 et complété
le 2026-09-15. Branches déclenchantes : `claude/init-xyw7gc` et, depuis le 2026-09-24,
`claude/remove-scanlist-history-a5bqzh` (branche de l'étude SuivieMAT, pour produire
l'APK du test du badge).

> ⚠️ **Constaté le 2026-09-24 sur le dépôt `JRRocks/SuivieMAT` : aucune exécution ne
> s'est jamais déclenchée**, même après un push sur une branche déclenchante (le
> workflow est pourtant enregistré et « active »). GitHub Actions est vraisemblablement
> désactivé dans les réglages du dépôt (*Settings › Actions › General*). Tant qu'il ne
> l'est pas, l'APK se compile depuis Android Studio, ou en session cloud (§ 6 ter).
> L'APK de debug (54,5 Mio, et encore 37,7 Mio en arm64 seul) dépasse par ailleurs la
> limite de 30 Mio d'envoi de fichier d'une session cloud. Il lance `testDebugUnitTest` **puis** `assembleDebug` sur un runner
`ubuntu-latest`, et publie l'APK en artefact (téléchargeable 7 jours depuis la page du
run). Déclenchement : à chaque push sur la branche de travail, ou manuellement
(`workflow_dispatch`, utilisable une fois le fichier présent sur la branche par défaut).

C'est le seul moyen de produire un APK depuis une session cloud claude.ai/code, qui n'a
ni SDK Android ni `ANDROID_HOME` (voir `CLAUDE.md`).

Deux pièges déjà rencontrés, à ne pas redécouvrir :

| Piège | Symptôme | Remède retenu |
|---|---|---|
| `sdkmanager` absent du PATH | `sdkmanager: command not found` | Le SDK **est** présent sur le runner : le localiser via `$ANDROID_SDK_ROOT/cmdline-tools/*/bin/sdkmanager` |
| `android-actions/setup-android` | `Failed to find package 'tools'` puis exit 1 (depuis le 2026-09) | Action retirée : elle installe le paquet `tools`, supprimé du dépôt SDK. Remplacée par la localisation manuelle ci-dessus |

> **Les tests ne tournaient nulle part avant le 2026-09-15** : le workflow ne lançait
> qu'`assembleDebug`, et l'émulateur ne les exécute pas davantage. Le premier passage de
> `testDebugUnitTest` en CI a immédiatement attrapé une régression réelle
> (`ScanCsvBuilderTest`, colonne `mode` du `.csv`). Ne pas retirer cette étape.

## 6 ter. Compiler et tester dans une session cloud (vérifié le 2026-09-24)

Contrairement à ce que laissait penser l'absence de SDK, une session cloud **peut**
compiler et lancer les tests, si la politique réseau de l'environnement laisse passer
`dl.google.com` et `maven.google.com` (c'était le cas le 2026-09-24). Tout s'installe
dans le **scratchpad** de la session, rien dans le dépôt :

```bash
SP=<scratchpad>
mkdir -p $SP/sdk/cmdline-tools && cd $SP
curl -sS -o cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q cmdtools.zip -d sdk/cmdline-tools && mv sdk/cmdline-tools/cmdline-tools sdk/cmdline-tools/latest
yes | sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=$SP/sdk --licenses
sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=$SP/sdk "platforms;android-36" "build-tools;35.0.0" "platform-tools"
export ANDROID_HOME=$SP/sdk
chmod +x gradlew && ./gradlew --init-script $SP/mirror.init.gradle testDebugUnitTest assembleDebug
```

**Piège : Maven Central répond `429 Too Many Requests`** depuis la session (limite de
débit de Maven Central, pas une règle du réseau). Parade : un script d'init Gradle,
**hors du dépôt**, qui place en tête le miroir de Maven Central hébergé par Google :

```groovy
// mirror.init.gradle
def mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
def configure = { handler ->
    handler.maven {
        url mirror
        content {
            excludeGroupByRegex("androidx.*")
            excludeGroupByRegex("com\\.android.*")
        }
    }
}
beforeSettings { settings ->
    configure(settings.pluginManagement.repositories)
    configure(settings.dependencyResolutionManagement.repositories)
}
```

Premier passage : quelques minutes de téléchargement ; ensuite, `clean testDebugUnitTest
assembleDebug` en moins de deux minutes. Le scratchpad disparaît avec la session : tout
est à refaire dans une nouvelle. Pas d'émulateur pour autant : la vérification écran
par écran reste à faire sur le poste Windows ou sur le téléphone.

## 7. Commandes utiles

Compiler l'APK de debug :

```bash
cd "C:/Users/Taiepii/Desktop/Claude Code/ScanList" && JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```

Lancer les tests unitaires :

```bash
cd "C:/Users/Taiepii/Desktop/Claude Code/ScanList" && JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest
```

Installer sur le Galaxy S25 branché en USB (débogage USB activé) :

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r "C:/Users/Taiepii/Desktop/Claude Code/ScanList/app/build/outputs/apk/debug/app-debug.apk"
```

## 8. État de la chaîne de build

| Vérification | Résultat | Date |
|---|---|---|
| `testDebugUnitTest` (session cloud, SDK installé dans le scratchpad, § 6 ter) | ✅ **205 tests, 0 échec** — 52 nouveaux pour le test du badge : `ApduTest` 10, `BerTlvTest` 8, `CardDirectoryTest` 5, `PivTest` 13, `CardChallengeTest` 6, `CardDiagnosticTest` 10 (carte PIV simulée, RSA 2048 et ECC P-256) | 2026-09-24 |
| `clean assembleDebug` (session cloud) | ✅ BUILD SUCCESSFUL, **aucun avertissement Kotlin** ; `app-debug.apk` 57 Mo, libellé « Test du badge » présent dans les ressources | 2026-09-24 |
| Écran « Test du badge » sur matériel | ⬜ **Non vérifiable en session cloud ni sur émulateur** (pas d'émulateur ici, et les AVD n'ont pas de puce NFC) — test physique par l'utilisateur, avec son badge | 2026-09-24 |
| `testDebugUnitTest` | ✅ **149 tests, 0 échec** (`BarcodeFormatsTest` : 8 tests sur les masques de symbologies et leur disjonction) | 2026-09-15 |
| `assembleDebug` | ✅ BUILD SUCCESSFUL | 2026-09-15 |
| APK debug produit | `app-debug.apk` — 55 Mo (arm64 + x86_64) ; contrôlé après téléchargement : libellé « Code 2D » présent dans `resources.arsc`, `libbarhopper_v3.so` et `libmlkit_google_ocr_pipeline.so` embarqués pour les deux ABI | 2026-09-15 |
| Mode Code 2D (PDF417, Data Matrix, Aztec) | ⬜ **Non vérifiable en CI ni sur émulateur** — aucune étiquette réelle à présenter à la caméra. Les masques de formats sont couverts par 8 tests unitaires, mais **le décodage réel d'un PDF417 et d'un Data Matrix, ainsi que la résolution d'analyse 1080p, restent à valider sur le Galaxy S25** (phase 10) | 2026-09-15 |
| `assembleDebug` | ✅ BUILD SUCCESSFUL, aucun avertissement | 2026-08-24 |
| `testDebugUnitTest` | ✅ **141 tests, 0 échec** (`ScanConsensusTest` + extension de `RegexRuleEngineTest`) | 2026-08-24 |
| APK debug produit | `app/build/outputs/apk/debug/app-debug.apk` — 55 Mo (arm64 + x86_64), variation négligeable, aucune dépendance ajoutée | 2026-08-24 |
| Vérification par consensus | ⬜ **Non vérifiable sur émulateur** (même limite matérielle que le décodage) — logique couverte par 18 tests unitaires (temps simulés), seuils/TTL à confirmer sur le Galaxy S25 en phase 10 | 2026-08-24 |
| `assembleDebug` | ✅ BUILD SUCCESSFUL, aucun avertissement | 2026-08-21 |
| `testDebugUnitTest` | ✅ **123 tests, 0 échec** | 2026-08-21 |
| APK debug produit | `app/build/outputs/apk/debug/app-debug.apk` — 57 Mo (arm64 + x86_64) | 2026-08-21 |
| Mode NFC/RFID sur émulateur | ✅ carte visible dans les 3 sélecteurs, message de repli correct (AVD sans matériel NFC), aucun crash, torche masquée | 2026-08-21 |
| Fonction Comparer sur émulateur | ✅ liste tout en rouge à la création, passage au vert, compteurs, enregistrement en dossier, `.csv` produit et relu | 2026-08-21 |
| Lancement sur émulateur | ✅ AVD `Medium_Phone_API_36.1`, aucun crash, caméra liée | 2026-08-20 |
| Modèles ML Kit embarqués | ✅ `libbarhopper_v3.so` (codes) et `libmlkit_google_ocr_pipeline.so` (OCR) présents dans l'APK | 2026-08-20 |

> **Piège Git Bash sur Windows :** `adb push` et `adb shell run-as … /data/local/tmp/…`
> voient leurs chemins *distants* réécrits en chemins Windows (`C:/Program Files/Git/data/…`).
> Préfixer la commande par `MSYS_NO_PATHCONV=1`, et donner le chemin *source* en notation
> Windows (`C:\…\fichier.db`).

> **Piège WAL :** `adb exec-out run-as … cat databases/scanlist.db` seul renvoie une base
> **sans les écritures récentes**, qui vivent dans `scanlist.db-wal`. Toujours rapatrier
> les trois fichiers (`.db`, `-wal`, `-shm`) avant d'interroger la base hors ligne.
