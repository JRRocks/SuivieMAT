# Vérification de la mise à jour intégrée

Outil de vérification, **hors de l'application** : rien de ce dossier n'entre dans l'APK.

La mise à jour depuis les releases GitHub (décision 34 de `consignes/contexte.md`) ne se
vérifie pas par des tests unitaires : elle dépend de la vraie réponse de GitHub, d'une
redirection de téléchargement, du `FileProvider` et de l'installateur d'Android. Ce
dossier la déroule de bout en bout sur un émulateur, comme le veut la règle du projet
(« les tests ne suffisent pas »).

## Le principe

`ancienne-version.init.gradle` compile l'application telle qu'elle est, mais sous le
numéro **0.1** (`versionCode` 1). Installée ainsi, elle voit la dernière release du dépôt
comme une mise à jour. `verifier.py` suit alors le parcours d'un utilisateur, en lisant
l'écran par `uiautomator` :

1. au lancement, la fenêtre « Mise à jour disponible » apparaît et cite la version ;
2. « Plus tard » la referme ; « Rechercher une mise à jour », au pied de l'onglet
   Nouveau scan, la repropose ;
3. « Installer » télécharge l'APK, le contrôle, puis ouvre la confirmation d'Android ;
4. après confirmation, la version installée est celle de la release ;
5. relancée, elle affiche son numéro au pied de l'onglet et répond « à jour ».

## Lancer la vérification

**Sur GitHub** : onglet *Actions* › *Verification de la mise a jour sur emulateur* ›
*Run workflow*. Elle part aussi quand un fichier de ce dossier change. Il faut qu'une
release existe déjà : la lancer **après** la compilation qui l'a publiée.

**Sur un émulateur local** (anglais, API 30 ou plus) :

```bash
./gradlew --init-script verif-maj/ancienne-version.init.gradle :app:assembleOptimise
python3 verif-maj/verifier.py app/build/outputs/apk/optimise/app-optimise.apk
```

L'APK ainsi compilé porte un faux numéro : ne jamais l'installer sur le téléphone.

## Ce que cela ne couvre pas

- L'écran où Android demande d'autoriser SuivieMAT à installer des applications :
  l'autorisation est accordée d'avance (`appops`), cet écran appartient au système.
- L'habillage de Samsung (One UI) : l'émulateur montre l'installateur d'Android nu.
- Une coupure réseau en cours de téléchargement.
