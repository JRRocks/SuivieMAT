# Contexte — ScanList

> **Fichier de mémoire longue durée.** À relire en priorité après un `/clear` ou au démarrage
> d'une nouvelle session. À mettre à jour à chaque évolution majeure du périmètre fonctionnel.
>
> Dernière mise à jour : 2026-10-27 — *Le menu principal devient une barre de
> navigation, l'application s'ouvre sur Nouveau scan (décision 28)*

---

## 1. But de l'application

**ScanList** est une application Android native de **scan à la chaîne** destinée à l'inventaire
et au contrôle de matériel physique.

L'utilisateur pointe la caméra sur un marquage (QR code, code-barres, ou texte imprimé) ;
l'application **enregistre automatiquement la valeur détectée en mémoire et repasse
instantanément en analyse**, sans aucune validation manuelle. Objectif de terrain : relever
le marquage de 10 batteries matérielles en quelques secondes, sans jamais reposer la main
sur l'écran.

**Cas d'usage principal :** inventaire de parc matériel (batteries, équipements, racks),
puis contrôle de conformité par comparaison avec une liste de référence.

## 2. Utilisateur et matériel cible

| Élément | Valeur |
|---|---|
| Appareil de référence | Samsung Galaxy S25 |
| Plateforme | Android natif (pas de cross-platform) |
| Langue de l'interface | **Français uniquement** |
| Contexte d'usage | Terrain, potentiellement hors-ligne, une seule main |

L'application doit fonctionner **100 % hors-ligne** : aucune dépendance réseau pour le scan,
l'OCR, le stockage ou la comparaison. Le réseau n'est sollicité que par le client mail tiers
au moment de l'export.

## 3. Périmètre fonctionnel

### 3.1 Modes de scan (mutuellement exclusifs)

Le mode est **choisi explicitement avant chaque scan**, jamais deviné. C'est une exigence
anti-erreur : en mode « QR Code », un code-barres présent dans le champ est ignoré, et
réciproquement.

- **OCR** — reconnaissance de texte, filtrée par une formule regex
- **Code 2D** — QR Code, **PDF417** (empilé, multi-rangées), **Data Matrix**, **Aztec**
  *(élargi le 2026-09-15, voir décision 21 ; s'appelait « QR Code » auparavant)*
- **Code-barres** — 1D uniquement, une seule rangée (EAN, UPC, Code 128, Code 39, ITF…)
- **NFC/RFID** — puce ou étiquette sans contact *(ajouté le 2026-08-21, voir décision 17)*

> La frontière **1D / 2D** entre les deux modes optiques est ce qui fait tenir l'exigence
> anti-erreur : une étiquette industrielle porte souvent un code linéaire **et** un code
> 2D qui n'encodent pas la même chose. Un mode qui décoderait les deux créerait deux
> lignes pour un seul objet.

### 3.2 Règles de capture

- **Scan à la chaîne** : écriture automatique en base + retour immédiat à l'analyse.
  Aucune boîte de dialogue bloquante. Retour utilisateur par vibration + son court.
- **Multi-codes sur une image** : si plusieurs codes sont détectés dans la même frame,
  chacun crée **sa propre ligne**.
- **Pas de doublon** : la portée est **le dossier courant**. Une valeur déjà présente dans
  le dossier est refusée (feedback distinct : vibration double). La même valeur peut
  exister dans un autre dossier.
- **Vérification par consensus contre les erreurs optiques** *(décision 19)* : en scan de
  dossier, une valeur n'est enregistrée qu'après avoir été lue **identique X fois** avant
  d'être validée (3 en QR Code / Code-barres, 5 en OCR). Ne s'applique **pas** à la
  fonction Comparer, dont le verdict reste rendu sur une seule lecture.

### 3.3 Module OCR — formules regex

L'OCR brut renvoie trop de bruit (étiquettes, mentions légales, numéros parasites). Le
filtrage se fait donc par **expressions régulières écrites à la volée** :

- L'utilisateur saisit une regex directement depuis l'écran de scan OCR.
- ~~Un **aperçu en direct** montre ce que la regex capture sur le flux caméra avant de valider.~~
  → **retiré le 2026-10-26** à la demande de l'utilisateur, avec le texte lu : voir décision 26.
- La regex est **enregistrée avec un nom** dans une bibliothèque locale, réutilisable pour
  tout scan futur.
- Chaque correspondance (`match`) dans le texte reconnu produit **une ligne distincte**.
- Une regex invalide n'est jamais appliquée : l'utilisateur est averti à la saisie.
- **Depuis la décision 25**, un **générateur** propose plusieurs formules commentées à
  partir d'un exemple de valeur (`1GH546`), sans IA : l'utilisateur n'a plus à écrire la
  regex lui-même.

> **Depuis la décision 20**, la même bibliothèque de formules sert aussi de **filtre de
> conformité optionnel** en QR Code et Code-barres. Différence avec l'OCR : la formule
> doit correspondre à la valeur **en totalité** (validation), pas en isoler un fragment
> (extraction) — pas de choix de groupe capturant, pas d'armement de la capture. Une
> formule vide n'est pas un filtre : tout est accepté, comme avant cet ajout.

### 3.4 Fonctionnalité « Comparer »

1. L'utilisateur charge une **liste de référence** en mémoire, depuis un dossier existant
   de l'application **ou** depuis un fichier `.csv` importé.
2. Il lance ensuite le scan physique du matériel (mode OCR / QR / Code-barres / NFC-RFID
   au choix).
3. Chaque élément scanné est confronté à la liste de référence, en temps réel.

**Principe (précisé par l'utilisateur le 2026-08-21) :** la fonction Comparer sert à
**valider la présence physique** des éléments d'une liste de référence. **Seule la liste
de base change de couleur.** Elle naît entièrement en rouge et chaque entrée passe au vert
quand elle est lue.

| Couleur | État | Signification |
|---|---|---|
| 🔴 **Rouge** | Manquant | État initial de **toute** la référence : présence non encore validée |
| 🟢 **Vert** | Trouvé | Présence physique confirmée par une lecture |

**Un numéro physique inconnu de la liste n'est pas pris en compte** : ni compté, ni affiché,
ni enregistré. Il n'existe donc pas de troisième état. L'application le signale seulement
sur le moment — bip grave, vibration longue et bandeau gris fugace — pour que l'opérateur
sache qu'il vient de viser autre chose que ce qu'il contrôle. Cette signalisation ne laisse
aucune trace.

- **Cas d'erreur** (lecture illisible, valeur vide, échec de décodage) : **ne rien faire**.
  Pas de message d'erreur, pas d'enregistrement, le scan continue.
- Les résultats d'une session de comparaison sont **enregistrables dans un dossier unique**,
  distinct des dossiers de scan simple. Ce dossier ne reçoit que les éléments validés ; les
  manquants ne sont portés que par le `.csv` de la session.

### 3.5 Persistance et reprise

- Les éléments scannés sont regroupés en **dossiers** consultables.
- Un scan antérieur peut être **repris** pour incrémenter une liste existante.

### 3.6 Export

- Export de n'importe quel dossier au format **`.csv`**.
- Nom du fichier d'export choisi par l'utilisateur.
- Envoi par **email via Intent Android** : l'application génère le `.csv` et ouvre le client
  mail du téléphone avec la pièce jointe, le destinataire et l'objet pré-remplis depuis les
  préférences d'envoi. **Aucun identifiant mail n'est stocké dans l'application.**

## 4. Architecture des menus (spécification stricte)

Cette hiérarchie est **imposée** et doit être respectée scrupuleusement.

```
MENU PRINCIPAL
│
├── DOSSIERS  (liste déroulante des dossiers existants)
│   ├── Consulter                     → détail de la liste
│   │   └── Lancer la fonction Comparer
│   ├── Exporter en .csv
│   │   ├── Choisir le nom du fichier d'export
│   │   └── Configurer les préférences d'envoi
│   └── Reprendre le scan
│       ├── Scan OCR
│       ├── Scan Code 2D
│       ├── Scan Code-barres
│       └── Scan NFC/RFID
│
├── COMPARER
│   ├── Depuis un Dossier local
│   │   └── Lancer le Scan  (OCR | Code 2D | Code-barres | NFC/RFID)
│   └── Depuis un Import .csv
│       └── Lancer le Scan  (OCR | Code 2D | Code-barres | NFC/RFID)
│
└── NOUVEAU SCAN
    ├── OCR
    ├── Code 2D
    ├── Code-barres
    └── NFC/RFID
```

> L'entrée « QR Code » est devenue « Code 2D » le 2026-09-15 (décision 21). Le nombre
> d'entrées et la structure sont inchangés : c'est un élargissement de périmètre, pas
> une nouvelle branche.

> Le NFC/RFID est un quatrième mode, ajouté après la spécification initiale (décision 17).
> Il s'insère dans les trois mêmes sous-menus que les trois modes d'origine, sans créer
> de nouvelle branche : le composant `ScanModePicker` étant partagé par les trois, un seul
> ajout suffit à le proposer partout où « OCR | QR Code | Code-barres » apparaissait.

> L'entrée « Test du badge » de la décision 22 a été **retirée le 2026-09-25**
> (décision 24) : elle n'existe plus, nulle part dans le menu.

> **Depuis le 2026-10-27 (décision 28), le MENU PRINCIPAL n'est plus un écran.** Ses
> trois branches sont les onglets d'une **barre de navigation en bas de l'écran**
> (Dossiers · Comparer · Nouveau scan), visible sur les trois écrans racines seulement,
> et l'application **s'ouvre sur NOUVEAU SCAN** (choix du mode). Tout ce qui est sous
> les trois branches est inchangé. Choix de l'utilisateur parmi cinq variantes, en
> connaissance de cause : c'est une modification de la hiérarchie imposée ci-dessus.

## 5. Décisions validées avec l'utilisateur

| # | Question | Décision retenue | Date |
|---|---|---|---|
| 1 | Envoi de l'export CSV | **Intent Android** vers le client mail, préférences pré-remplies | 2026-08-20 |
| 2 | Portée de la déduplication | **Par dossier** | 2026-08-20 |
| 3 | Élément scanné hors référence | ~~Enregistré et affiché en gris (intrus)~~ → **révisé le 2026-08-21** : voir décision 14 | 2026-08-20 |
| 4 | Langue de l'interface | **Français uniquement** | 2026-08-20 |
| 5 | Stack technique | Laissée au choix du développeur → voir `outils.md` | 2026-08-20 |
| 6 | Stockage local | Laissé au choix du développeur → voir `outils.md` | 2026-08-20 |
| 7 | Gestion des cas d'erreur | **Ne rien faire** | 2026-08-20 |
| 8 | Format du texte OCR | Regex **écrites à la volée** puis enregistrées pour réutilisation | 2026-08-20 |
| 9 | Comparaison des valeurs | **Insensible à la casse**, espaces de bord retirés et espaces internes réduits. Un relevé OCR ne restitue pas toujours la casse d'origine ; sans cette normalisation, `bat-001` produirait un faux « manquant ». Modifiable via `CompareEngine(caseSensitive = true)`. | 2026-08-20 |
| 10 | Architecture cible de l'APK | **release : arm64-v8a** (Galaxy S25) — divise la taille par ~4, les modèles ML Kit embarqués étant livrés pour quatre ABI. **debug : arm64-v8a + x86_64** — un APK arm64 seul refuse de s'installer sur l'émulateur x86_64 (`INSTALL_FAILED_NO_MATCHING_ABIS`). | 2026-08-20 |
| 11 | Modèles de données | Les **entités Room servent directement de modèles métier**, la logique étant isolée dans `domain/`. Évite plusieurs centaines de lignes de mapping sans bénéfice. | 2026-08-20 |
| 12 | Armement de la capture OCR | En OCR, la capture doit être **démarrée explicitement** ; toute modification de la formule la désarme. Sans cela, une formule en cours de frappe enregistrerait des dizaines de valeurs parasites. QR et code-barres restent immédiats. | 2026-08-20 |
| 13 | Messages d'erreur de formule | Les messages de `PatternSyntaxException` sont **traduits en français** dans `RegexRuleEngine`. La JVM ne les localise pas et afficherait « Missing closing bracket… » dans une interface entièrement française. La position de la faute est conservée. | 2026-08-20 |
| 14 | **Sens de la comparaison** *(remplace la décision 3)* | **Seule la liste de base change de couleur.** Elle part **entièrement en rouge** (présence non validée) et chaque entrée passe au **vert** quand elle est lue. Un numéro physique inconnu **n'est pas du tout pris en compte** : aucun enregistrement, aucun compteur, aucune ligne. Il n'est signalé que sur le moment (bip grave + bandeau gris fugace), sans laisser de trace. L'état « intrus » disparaît donc du modèle. | 2026-08-21 |
| 15 | Table `intruder_entries` | **Conservée dans le schéma mais plus jamais alimentée.** La supprimer changerait l'empreinte du schéma Room et imposerait une migration destructive à l'application déjà installée sur le S25. Elle disparaîtra au prochain changement de version de base. | 2026-08-21 |
| 16 | Export d'une session de comparaison | Un bouton **« Exporter »** sur l'écran de session produit un `.csv` à colonne `etat` (`MANQUANT` puis `TROUVE`). C'est le **seul document qui porte les manquants** : le dossier de résultat, lui, ne contient que les éléments réellement lus. | 2026-08-21 |
| 17 | **Ajout d'un quatrième mode : NFC/RFID** | Demandé par l'utilisateur après la phase 8. Rejoint les trois modes existants dans les trois mêmes sous-menus (Reprendre le scan, Lancer le scan depuis Comparer, Nouveau scan), sans nouvelle branche de menu. Valeur retenue : contenu **NDEF texte si présent, sinon UID matériel en hexadécimal** — la plupart des tags d'inventaire bon marché n'ont pas de contenu NDEF. Lecture pilotée par `NfcAdapter.enableReaderMode`, armée/désarmée sur `ON_RESUME`/`ON_PAUSE` de l'écran. Choix technique du développeur, à confirmer si le terrain révèle un besoin différent (ex. lire un identifiant applicatif précis plutôt que l'UID brut). | 2026-08-21 |
| 18 | Feedback NFC | Même vocabulaire sonore/haptique que les autres modes (`ScanFeedback.success/duplicate`) — pas de signal dédié. Un tag hors liste de référence en mode Comparer déclenche le même bandeau gris fugace « hors liste » que pour les codes optiques (décision 14) : aucune règle spécifique au NFC. | 2026-08-21 |
| 19 | **Vérification par consensus contre les erreurs optiques** | Demandé par l'utilisateur : l'OCR et le décodage code-barres se trompent à cause de l'optique (angle, reflet, étiquette abîmée). Une valeur n'est enregistrée qu'après avoir été lue **identique X fois** avant validation — **3** en QR Code / Code-barres (~100 ms de tenue caméra à la cadence capteur), **5** en OCR (bruit par frame plus élevé, coût par lecture plus faible). Seuils figés en dur pour cette première version, à ajuster lors du test terrain S25 (phase 10) si besoin. Le débouncer de 1,5 s (décision antérieure, non renumérotée) change de rôle : il n'est plus un pré-filtre anti-rafale mais un **verrou anti-rebond posé après validation**, sans quoi il écarterait les lectures 2 et 3 avant que le consensus ne les compte. **Portée limitée au scan de dossier** : la fonction Comparer garde son verdict sur une seule lecture (choix explicite de l'utilisateur, la fausse alarme « hors liste » sur une frame isolée y subsiste), et le mode NFC n'est pas concerné (pas de bruit optique, débouncer seul). **Garde d'ambiguïté additionnelle**, proposée par le développeur et validée par l'utilisateur après discussion : le filtre regex ne couvre pas les confusions *à l'intérieur* d'une même classe de caractères (`8`↔`6`, `C`↔`G`…), qu'un format bien formé laisse passer sans broncher. Si une valeur nouvellement vue est à une lettre près (substitution, insertion ou suppression) d'une valeur en cours de comptage, et que les deux n'ont **jamais figuré ensemble dans une même image**, les deux compteurs repartent à zéro plutôt que de laisser le hasard trancher laquelle atteint le seuil en premier. La condition « jamais coexisté » est essentielle : deux numéros de série séquentiels lus ensemble dans la même image (cas fréquent en inventaire de rack) sont deux objets réels distincts et ne doivent jamais s'annuler. Désactivée en QR Code, dont la correction d'erreur Reed-Solomon rend une mauvaise lecture pratiquement impossible. | 2026-08-24 |
| 20 | **Extension du filtre regex au QR Code et au Code-barres** | Jusque-là réservé à l'OCR (décision 8). Nécessaire car deux symbologies décodées par l'application (`CODE_39` sans clé, `ITF`) n'ont aucun contrôle d'intégrité côté ML Kit, à la différence d'EAN/UPC/Code 128 qui embarquent leur propre checksum — sans filtre, une lecture erronée mais bien formée n'est arrêtée que par le consensus (décision 19), jamais par la regex. Même bibliothèque de formules que l'OCR, mais rôle différent : **correspondance totale** de la valeur (validation), pas extraction d'un fragment dans du texte libre — pas de choix de groupe capturant, pas d'armement de la capture (un code décodé est déjà une valeur complète). Formule vide = aucun filtre, comportement inchangé par rapport à avant cette décision. | 2026-08-24 |

| 21 | **Élargissement du mode QR Code à toute la famille 2D** | Demandé par l'utilisateur après avoir rencontré sur le terrain une étiquette portant un code-barres **en plusieurs étages** que l'application ne savait pas lire. Choix retenu par l'utilisateur après analyse : **élargir le mode « QR Code » existant** plutôt que créer un cinquième mode ou élargir « Code-barres ». Le mode est renommé **« Code 2D »** et décode QR Code, **PDF417**, **Data Matrix** et **Aztec** ; **« Code-barres » reste strictement linéaire**. Trois conséquences techniques : la constante `ScanMode.QR_CODE` **garde son nom** (elle est persistée telle quelle en base, la renommer imposerait une migration aux dossiers déjà scannés) ; la résolution d'analyse passe à **1080p pour ce seul mode**, les modules d'un PDF417 étant bien plus fins que ceux d'un QR de même encombrement ; la garde d'ambiguïté du consensus **reste désactivée** sur ce mode, les quatre formats portant tous une correction Reed-Solomon. **Limite assumée :** les autres familles de codes empilés (Codablock F, Code 16K, Code 49, MicroPDF417) restent illisibles — voir § 8 de `workflow.md`. | 2026-09-15 |
| 22 | **Écran de test du badge sans contact** | ~~Demandé par l'utilisateur pour tester physiquement une identification par certificat et défi-réponse sur le badge des magasiniers.~~ → **retiré le 2026-09-25** : voir décision 24 | 2026-09-24 |
| 23 | **SuivieMAT est une application distincte de ScanList** | ScanList est une **application indépendante**, sans rapport avec ce dépôt, que l'utilisateur garde en l'état sur son téléphone. Or ce dépôt utilisait encore `applicationId = com.scanlist.app` et le nom affiché « ScanList », hérités du projet d'origine — les deux applications se seraient donc superposées (même identifiant Android = conflit d'installation, même nom affiché = confusion). **`applicationId` renommé en `com.suiviemat.app`, nom affiché en « SuivieMAT »** ; c'était déjà signalé, sans être tranché, à la proposition D10 de `plan-suivimat.md`. Le *namespace* Kotlin du code source reste `com.scanlist.app` (renommer le `package` de tous les fichiers n'aurait aucun effet sur la coexistence des deux applications, pour un chantier mécanique important) — Android distingue explicitement les deux : seul l'`applicationId` compte pour l'installation. **Conséquence assumée : nouvelle application du point de vue d'Android**, sans lien avec un éventuel test antérieur fait sous l'ancien identifiant — cohérent avec le fait qu'aucune donnée réelle n'existe encore (phases 9-10 non commencées). | 2026-09-25 |
| 24 | **Retrait de l'écran de test du badge, identification arrêtée au niveau 1** | Le badge testé par l'utilisateur s'est révélé être une **carte CIMS** (ministère des Armées), dont l'authenticité ne peut pas être vérifiée par cette application (autorité de certification interne au ministère). **L'écran « Test du badge » et toute sa logique (`domain/card/`, `ui/cardtest/`, `scanner/CardTestReader.kt`) sont retirés du code**, avec leurs tests. **Décision de l'utilisateur : l'identification par carte se limite au niveau 1** — lecture de l'identifiant (UID) de la carte via le mode NFC déjà existant (`ScanMode.NFC`), sans matériel de test à fabriquer. Détail dans `plan-suivimat.md` § 10 ter. | 2026-09-25 |
| 25 | **Générateur de formules regex à partir d'exemples, sans IA** | Demandé par l'utilisateur : obtenir plusieurs formules commentées à partir d'un exemple de valeur (`1GH546`), depuis la sélection de formule de chaque champ. Une IA embarquée a été étudiée puis écartée : Gemini Nano (ML Kit Prompt API) ne cite pas le Galaxy S25 parmi les appareils compatibles (liste officielle consultée le 2026-10-26), un modèle embarqué (Gemma 3 1B, environ 500 Mo) multiplierait la taille de l'APK pour un résultat peu fiable sur les regex. **Choix validé par l'utilisateur : générateur déterministe** (`domain/regex/RegexSuggester`). L'exemple est découpé en blocs (chiffres, majuscules, minuscules, caractères fixes) dont on dérive jusqu'à cinq formules, de la plus stricte à la plus large : format exact, majuscules ou minuscules, séparateurs tolérés, début fixe, longueurs libres. Chacune est **vérifiée par le moteur contre les exemples avant d'être affichée**, avec un commentaire et une valeur d'illustration (« Accepte aussi » / « Refuse ») elle aussi vérifiée. Plusieurs exemples séparés par une virgule donnent des plages de longueur (`\d{3,4}`) ; des exemples de structures différentes sont signalés sans proposition. Bouton à côté du champ de formule, en OCR comme dans le filtre de conformité Code 2D / code-barres. La formule choisie suit le chemin d'une formule tapée à la main : en OCR, elle désarme la capture (décision 12). **Non retenu dans cette version** : la saisie en phrase (« 1 chiffre, 2 lettres, 3 chiffres »), l'utilisateur ayant choisi la saisie par exemple. | 2026-10-26 |
| 26 | **Panneau de formule épuré, outils sous le champ** | Demandé par l'utilisateur, capture d'écran à l'appui : **retrait de tout ce qui séparait le champ « Formule regex » du bouton « Démarrer la capture »** en OCR, soit l'aperçu en direct des correspondances et le texte lu (« Voir le texte lu »). Les outils de formule passent **sous le champ**, en **trois boutons de largeur égale avec icône et libellé**, libellés choisis par l'utilisateur : **« Info »** (aide-mémoire regex), **« Regex Auto »** (générateur, décision 25), **« Enregistré »** (formules enregistrées). Même rangée dans le filtre de conformité Code 2D / code-barres, par cohérence (choix du développeur : le bouton « Info » y apparaît, il n'y existait pas). Conséquences techniques : plus aucune extraction regex par image tant que la capture OCR est désarmée (allège le thread d'analyse) ; le panneau OCR prend la hauteur de son contenu au lieu de 40 % de l'écran, la caméra récupère la place. **Conservé** : le choix du groupe capturant, qui n'apparaît que si la formule contient un groupe. **Contrepartie assumée** : une formule ne se vérifie plus sur l'étiquette avant d'armer la capture ; le générateur, lui, contrôle chaque proposition contre l'exemple saisi. | 2026-10-26 |
| 27 | **Refonte de l'interface : style « Bleu de travail »** | Demandé par l'utilisateur : revoir toute l'interface en s'appuyant sur les guides *impeccable* (`github.com/pbakaus/impeccable`, mode « Operate » et règles Android), avec **un style uniforme**, **cinq variantes proposées à chaque changement** et le choix demandé **par notification**. Étape 1 (style global) : cinq directions présentées sur un canevas de maquettes, mêmes trois écrans à contenu réaliste — A Kraft & acier, B Étiquette thermique, C Bleu de travail, D Signalétique d'entrepôt, E Material You natif. **Choix de l'utilisateur : C.** En-tête de chaque écran teint du bleu des tenues de travail (`#22386F`, plus sombre en thème nuit) avec grand titre blanc ; contenu clair sur fond gris bleuté, cartes blanches arrondies ; grands caractères (**Source Sans 3**) et valeurs en **Source Code Pro**, polices **embarquées** (application hors-ligne) ; **le compteur qui compte vit dans l'en-tête** (captures d'un scan, éléments d'un dossier, avancement d'une comparaison). « Nouveau scan » et le dernier mode utilisé sont teints du bleu de l'en-tête. **Inchangés** : le code rouge / vert de Comparer (décision 14), le gris réservé aux bandeaux passagers, la hiérarchie des menus (§ 4). Étapes suivantes, chacune en cinq variantes soumises à l'utilisateur : menu principal, choix du mode, écran de scan, dossiers, comparer, export et fenêtres de formules. | 2026-10-26 |
| 28 | **Le menu principal devient une barre de navigation** *(modifie le § 4)* | Étape 2 de la refonte (décision 27) : cinq organisations du menu principal présentées sur le canevas — trois cartes, « Nouveau scan » d'abord, entrées à portée de pouce, reprise rapide du dernier dossier, barre de navigation Android. **Choix de l'utilisateur : la barre de navigation**, sachant qu'elle modifie la hiérarchie imposée. L'écran « Menu principal » disparaît ; ses trois branches deviennent les onglets d'une barre en bas de l'écran (Dossiers · Comparer · Nouveau scan, dans l'ordre du menu qu'ils remplacent), affichée **sur les trois écrans racines seulement** (les écrans plus profonds gardent leur flèche de retour, l'écran de scan garde toute sa hauteur pour la caméra). L'application **s'ouvre sur Nouveau scan** : un appui de moins pour lancer un inventaire. Retour système : d'un onglet vers Nouveau scan, puis sortie de l'application (comportement standard d'Android). Rien ne change sous les trois branches. | 2026-10-27 |

## 6. Hors périmètre (non demandé)

- Synchronisation cloud, compte utilisateur, backend
- Envoi SMTP direct depuis l'application
- Version iOS ou web
- Scan de documents / photo d'archive attachée aux lignes
