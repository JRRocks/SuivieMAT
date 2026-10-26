# APK de test

Ce dossier reçoit l'APK de **debug** compilé par GitHub Actions
(`.github/workflows/build-debug-apk.yml`), pour pouvoir l'installer depuis le
téléphone, sans ordinateur. Faute de minutes GitHub Actions (jusqu'au 1er novembre
2026), il est aussi déposé directement par une session cloud, compilé de la même façon
(arm64, même clé de debug).

| Fichier | Contenu |
|---|---|
| `SuivieMAT-debug.apk` | Dernier APK de debug compilé pour cette branche (arm64 : téléphone uniquement, ne s'installe pas sur l'émulateur) |
| `SuivieMAT-debug.txt` | Commit source, date de compilation et empreinte SHA-256 de l'APK |

## Installer depuis le téléphone

1. Ouvrir `SuivieMAT-debug.apk` sur GitHub, puis « Download » (ou « View raw »).
2. Ouvrir le fichier téléchargé ; autoriser si demandé l'installation d'applications
   depuis le navigateur.
3. Vérifier au besoin l'empreinte SHA-256 avec celle de `SuivieMAT-debug.txt`.

## ⚠️ Changement d'identité le 2026-09-25 : nouvelle installation

Jusqu'au 2026-09-25, cette application portait `applicationId = com.scanlist.app` et
s'appelait « ScanList », hérités du projet d'origine — au risque de se superposer avec
**ScanList, une application réellement indépendante** que l'utilisateur garde par
ailleurs. Elle porte désormais `applicationId = com.suiviemat.app` et s'appelle
**« SuivieMAT »** (décision 23 de `consignes/contexte.md`).

**Conséquence : pour Android, c'est une application neuve.** Si un APK de test antérieur
au 2026-09-25 est déjà installé (sous l'ancien nom « ScanList », ancien identifiant), il
reste sur le téléphone comme une **application séparée** — sans rien perdre, elle
n'est ni écrasée ni mise à jour. Elle peut être désinstallée à part si elle n'a plus
d'usage.

Ces APK sont signés avec la clé de debug **versionnée** du dépôt (`app/debug.keystore`).
Elle ne change pas : les installations suivantes de « SuivieMAT » (sous le nouvel
identifiant) se font par-dessus les précédentes, sans rien perdre.

## Ce que ce dossier n'est pas

Une version à distribuer aux magasiniers. La clé de debug est publique dans le dépôt ;
la version distribuée sera signée par la clé de release, à créer juste avant la
distribution (`consignes/plan-suivimat.md` § 0).

## Relancer une compilation

Onglet **Actions** du dépôt › *Build debug APK* › *Run workflow* › choisir la branche.
