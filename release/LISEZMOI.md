# APK de test

Ce dossier reçoit l'APK de **debug** compilé par GitHub Actions
(`.github/workflows/build-debug-apk.yml`), pour pouvoir l'installer depuis le
téléphone, sans ordinateur.

| Fichier | Contenu |
|---|---|
| `SuivieMAT-debug.apk` | Dernier APK de debug compilé pour cette branche (arm64 : téléphone uniquement, ne s'installe pas sur l'émulateur) |
| `SuivieMAT-debug.txt` | Commit source, date de compilation et empreinte SHA-256 de l'APK |

## Installer depuis le téléphone

1. Ouvrir `SuivieMAT-debug.apk` sur GitHub, puis « Download » (ou « View raw »).
2. Ouvrir le fichier téléchargé ; autoriser si demandé l'installation d'applications
   depuis le navigateur.
3. Vérifier au besoin l'empreinte SHA-256 avec celle de `SuivieMAT-debug.txt`.

## ⚠️ Première installation

Ces APK sont signés avec la clé de debug **versionnée** du dépôt (`app/debug.keystore`),
en place depuis le 2026-09-25. Une application installée **avant**, depuis Android
Studio, porte une autre signature : Android refuse alors la mise à jour
(« Application non installée »). Il faut la **désinstaller d'abord**, ce qui **efface
ses dossiers**. Exporter ce qui doit être conservé avant.

Les installations suivantes se font ensuite par-dessus, sans rien perdre.

## Ce que ce dossier n'est pas

Une version à distribuer aux magasiniers. La clé de debug est publique dans le dépôt ;
la version distribuée sera signée par la clé de release, à créer juste avant la
distribution (`consignes/plan-suivimat.md` § 0).

## Relancer une compilation

Onglet **Actions** du dépôt › *Build debug APK* › *Run workflow* › choisir la branche.
