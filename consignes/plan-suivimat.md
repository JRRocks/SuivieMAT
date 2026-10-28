# Plan — SuivieMAT : suivi de matériel sous contraintes comptables

> **Fichier de suivi du plan.** À relire après un `/clear`, avec les trois autres fichiers
> de `consignes/`. À mettre à jour à chaque réponse de l'utilisateur ou de la DSI, à chaque
> décision tranchée et à chaque étape terminée (§ 13 et § 14).
>
> **Statut : étude de faisabilité — rien n'est validé.** Les décisions du § 5 de
> `contexte.md` restent en vigueur tant que l'utilisateur n'a pas validé explicitement les
> propositions du § 11 ci-dessous. Une proposition validée y est reportée **par ajout de
> ligne**, jamais en réécrivant une décision existante.
>
> Créé le : 2026-09-24 — dernière mise à jour : 2026-09-25, *identification par carte à
> puce arrêtée au niveau 1 (lecture d'UID via le mode NFC existant) ; écran de test
> retiré ; clé de signature reportée juste avant la distribution* (§ 0, § 10 ter)

---

## 0. À ne pas oublier avant la distribution aux magasiniers

> Liste tenue à la demande de l'utilisateur (2026-09-24) : ces points sont **reportés
> volontairement**, pas abandonnés. À dérouler **avant la toute première installation**
> sur le téléphone d'un magasinier.

- [ ] **Créer la clé de signature de release** (C6, D13). Décision de l'utilisateur du
      2026-09-24 : faite **juste avant la distribution**, pas avant. Conservée **hors du
      dépôt** (secret de la CI + copie hors ligne). Toutes les versions suivantes signées
      avec elle : la première signature installée verrouille les mises à jour, et une
      désinstallation efface le journal non exporté.
- [ ] **Produire l'APK de release** avec cette clé : la CI ne compile aujourd'hui que
      l'APK de test `optimise` (optimisé par R8 depuis le 2026-10-28, mais signé avec la
      clé de debug).
- [ ] **Ne jamais distribuer un APK de `release/`** : ce sont des APK de test, signés
      avec la clé de debug versionnée `app/debug.keystore`, publique dans le dépôt
      (depuis le 2026-09-25, `outils.md` § 6 bis).
- [ ] `allowBackup="false"` (C4, D12), si validé.
- [ ] Architectures de l'APK selon les téléphones réels (C7, U9).

---

## 1. La demande, reformulée

Faire évoluer ScanList en **outil de suivi de matériel** dont les données sont
recevables par un comptable et par un auditeur : **journal (ledger), horodatage,
piste d'audit**.

```
            RÉFÉRENTIEL (descendant)                     RELEVÉS (montant)
  ┌────────────────────────────┐                ┌────────────────────────────┐
  │  Comptable — Excel 2016    │ ── référentiel ──►  Magasinier — application │
  │  poste intranet restreint  │                │  scan parent → enfants     │
  │  VBA : regroupe, trie,     │ ◄── journal .csv ── saisie des quantités     │
  │  synthétise                │                │  journalisation            │
  └────────────────────────────┘                └────────────────────────────┘
```

Scénario terrain :

1. le magasinier scanne un matériel **parent** (moteur de scan existant) ;
2. l'application cherche le parent dans le **référentiel** et en tire la configuration
   parent → enfants, avec la **quantité théorique** de chaque enfant ;
3. le magasinier relève la **quantité présente** de chaque enfant ;
4. l'application **journalise** chaque saisie et prépare les données pour l'envoi ;
5. le comptable regroupe tous les fichiers en VBA, **remet les lignes dans l'ordre** et
   fait la synthèse ;
6. le comptable **diffuse** un référentiel mis à jour vers tous les magasiniers.

## 2. Verdict de faisabilité

**Faisable, sans serveur, sans compte, sans permission réseau** — donc dans le cadre de
sécurité le plus strict qui soit — avec une **option 1 améliorée** (§ 4.3). La moitié de
la plomberie existe déjà : moteur de scan quatre modes, codec CSV (BOM UTF-8, `;`, RFC
4180), import par le Storage Access Framework, export par Intent mail, et la fonction
Comparer qui est déjà un « théorique contre constaté ».

Trois limites à accepter dès le départ, parce qu'aucune architecture 100 % hors-ligne
sans serveur ne peut les lever :

| Ce qu'on peut garantir | Ce qu'on ne peut pas garantir |
|---|---|
| **L'ordre** des saisies d'un appareil (numéro de séquence sans trou) | **L'heure réelle** d'une saisie : l'horloge d'un téléphone se règle à la main. On la **fiabilise** (MDM, détection d'anomalies, § 3), on ne la **prouve** pas |
| **L'intégrité** d'un fichier : toute modification, suppression ou insertion de ligne est **détectée** (chaîne de hash) | L'**impossibilité** de modifier : un CSV reste un texte modifiable. On détecte la falsification, on ne l'empêche pas |
| La **complétude** : un fichier manquant se voit (trou dans les séquences) | L'**identité** certaine de l'opérateur : sans compte, elle est déclarative — **renforcée** par la carte à puce (§ 10 ter), sans devenir une preuve tant qu'il n'y a pas de défi-réponse |

Un horodatage **opposable** (horodatage qualifié eIDAS, signature électronique) exige un
tiers en ligne : hors de portée d'une application sans réseau. Si l'audit l'exige, c'est
l'option 2 avec un serveur de la DSI — à savoir **avant** de développer (question U6).

~~**Trois inconnues peuvent tout bloquer**~~ — *état au 2026-09-24 après les réponses de
l'utilisateur :*

- ✅ **Macros** : VBA est déjà utilisé sur le poste comptable et fonctionne.
- ✅ **Mail montant** : un `.csv` envoyé d'une messagerie personnelle vers Outlook pro
  passe la sécurité.
- 🔄 **Distribution et MDM** : la question change de nature. L'application ira sur les
  **téléphones personnels** des magasiniers, donc sans MDM : plus aucune politique ne peut
  bloquer le partage, mais **plus aucune garantie n'est imposée non plus** (heure
  automatique, verrouillage, sauvegarde, version installée). L'application doit porter
  elle-même ces garanties — voir § 10 bis.

Restent à vérifier : le **sens descendant** (Outlook pro → messagerie personnelle, Q10)
et l'**autorisation de ces données** sur des téléphones et des messageries personnels
(Q11).

## 3. Les contraintes comptables, traduites en technique

| Principe comptable / d'audit | Traduction dans l'application | Traduction côté comptable |
|---|---|---|
| **Intangibilité** : une écriture validée ne se modifie ni ne se supprime ; on la corrige par une contre-écriture | Table `ledger_entries` **en ajout seul**. Des **triggers SQLite** `BEFORE UPDATE` / `BEFORE DELETE` → `RAISE(ABORT)` rendent la règle impossible à contourner par du code — même philosophie que l'index unique `(folderId, value)` : la règle métier est portée par le moteur SQL. Une erreur de saisie se corrige par un évènement `ANNULATION` qui référence la séquence annulée | Les fichiers reçus sont des **pièces justificatives** : archivés en lecture seule, **jamais ouverts puis réenregistrés dans Excel** (Excel réécrit l'encodage, supprime les zéros de tête, convertit les dates) |
| **Numérotation continue et chronologique** | `seq` par appareil, sans trou, attribué dans la même transaction que l'insertion, index unique `(appareil, seq)` | Contrôle de continuité : la première séquence d'un fichier = la dernière du fichier précédent + 1, sinon **alerte « fichier manquant »** |
| **Piste d'audit** : qui, quoi, quand, sur quelle base | Chaque ligne porte l'appareil, l'opérateur, le site, la campagne, la **version du référentiel** utilisée, le mode de scan | Synthèse filtrable par site, opérateur, campagne ; signalement des relevés faits sur un référentiel périmé |
| **Horodatage** | Heure UTC **et** décalage horaire (`2026-09-24T12:03:12Z` + `+02:00`) — le format actuel, local sans décalage, rend ambiguë l'heure qui se répète au passage à l'heure d'hiver. En complément : `elapsedRealtime` et `BOOT_COUNT` (API 24+) pour **détecter** une horloge reculée, et l'état du réglage « date et heure automatiques » (`Settings.Global.AUTO_TIME`, lisible sans permission) — seule parade sur un téléphone personnel, qu'aucun MDM ne peut contraindre | Tri : par `(appareil, seq)` à l'intérieur d'un appareil — **c'est l'ordre qui fait foi** ; par horodatage UTC entre appareils. Une séquence croissante dont l'heure recule est signalée « anomalie d'horloge » |
| **Intégrité / inaltérabilité** | Chaîne de hash : `hash = SHA-256(hash_prec ‖ ligne canonique)`. Modifier, supprimer ou insérer une ligne casse toute la suite | Le kit Excel recalcule la chaîne sur le **fichier brut** (pas sur la feuille) et signale la première ligne rompue |
| **Conservation** (pièces justificatives : 10 ans, art. L123-22 du Code de commerce) | Les exports sont eux-mêmes journalisés (évènement `EXPORT` avec la plage de séquences) | Archivage des fichiers originaux et des mails — politique à fixer avec la DSI (Q8) |
| **Séparation des tâches** | Le téléphone ne porte **aucune valorisation** (pas de prix) : il relève des quantités, c'est tout | La valorisation reste dans Excel, chez le comptable |

### Niveaux d'intégrité possibles (décision D3)

| Niveau | Mécanisme | Protège contre | Vérifiable en VBA pur ? |
|---|---|---|---|
| **1** | Séquence + chaîne SHA-256 | Corruption, perte de ligne, retouche naïve dans un tableur, fichier manquant | ✅ — SHA-256 en VBA pur (implémentations publiques, ~300 lignes) |
| **2** | Niveau 1 + **HMAC-SHA-256** avec une clé propre à chaque appareil, générée et échangée à l'enrôlement | En plus : falsification par quiconque ne possède pas la clé (entre le téléphone et le comptable) | ✅ — HMAC en VBA pur, clés conservées dans le classeur du comptable |
| **3** | Signature asymétrique par une clé du **Android Keystore** (non extractible) | En plus : falsification par le magasinier lui-même | ❌ — vérification ECDSA irréaliste en VBA, il faudrait un outil tiers sur le poste |

Recommandation : **niveau 1 d'office, niveau 2 si l'audit l'exige.** Le niveau 3
suppose un outil hors Excel, donc une nouvelle validation de la DSI.

> ⚠️ **Piège principal de la chaîne de hash** : Kotlin et VBA doivent sérialiser la
> ligne **à l'octet près** de la même façon (ordre des champs, séparateur, UTF-8, format
> des nombres et des dates). La spécification de la forme canonique s'accompagne de
> **vecteurs de test partagés** — un fichier et ses hash attendus — vérifiés par les tests
> unitaires Kotlin **et** par le kit VBA.

## 4. Centraliser les données : analyse des options

### 4.1 Option 1 — un `.csv` par mail au comptable

Les avantages listés sont réels, et le principal n'y figure pas : **l'application n'a
besoin d'aucune permission réseau**. Aujourd'hui, `AndroidManifest.xml` n'en déclare
aucune — le téléphone ne peut **physiquement** rien envoyer de lui-même. C'est l'argument
le plus fort face à une DSI stricte, et il disparaît avec l'option 2.

Les deux inconvénients cités se traitent :

| Inconvénient | Réponse |
|---|---|
| Beaucoup de mails à traiter à la main | Objet normalisé (`[SUIVIMAT] <site> <appareil> <plage>`) → règle Outlook **simple** (sans script) qui range les mails dans un dossier → **une macro, un clic** : extraction des pièces jointes, contrôle, consolidation. À 20 magasiniers et un envoi par semaine, cela fait 20 mails triés automatiquement |
| Passation en cas de changement de comptable | Envoyer à une **boîte mail fonctionnelle** (`inventaire@…`), jamais à une personne. La passation se réduit à donner l'accès à la boîte et au classeur |

Inconvénients réels qui restent :

- **complétude** : un mail peut ne jamais partir (oubli, téléphone perdu) → c'est le
  contrôle de continuité des séquences qui le révèle ;
- **délai** : les données ne remontent que quand le magasinier exporte ;
- ~~**politique MDM** : sur un téléphone géré, une politique de protection d'application
  peut interdire le partage d'une application non gérée vers Outlook (question Q3).~~
  Sans objet : téléphones personnels, messagerie personnelle, passage vers Outlook pro
  vérifié par l'utilisateur (2026-09-24).

### 4.2 Option 2 — centralisation « cloud » par l'application

- demande la permission `INTERNET`, un serveur, une authentification par magasinier
  (donc des **comptes**, explicitement hors périmètre au § 6 de `contexte.md`), du TLS,
  une maintenance et très probablement un audit de sécurité du serveur ;
- **le poste comptable, en intranet restreint, n'atteindra sans doute pas un cloud
  public** — c'est le point qui fait tomber Google Drive, en plus de l'hébergement de
  données d'entreprise hors du périmètre homologué ;
- l'inconvénient « passation des accès » n'est pas propre à l'option 2 : il se règle de
  la même façon, par un **groupe** d'accès plutôt qu'une personne.

L'option 2 ne se justifie que si la DSI **fournit** l'infrastructure (serveur interne
joignable par VPN depuis les téléphones, ou SharePoint d'entreprise) **et** si l'audit
exige un horodatage opposable.

### 4.3 Recommandation — option 1 améliorée (« 1+ »)

| Critère | Option 1 brute | **Option 1+** | Option 2 |
|---|---|---|---|
| Permission réseau dans l'application | aucune | **aucune** | `INTERNET` |
| Infrastructure à créer | aucune | **une boîte fonctionnelle** | serveur + comptes + TLS |
| Validation DSI | faible | **faible** (tout passe par le serveur mail déjà homologué) | lourde |
| Charge du comptable | un mail = une manipulation | **un clic par lot** | extraction à la demande |
| Passation | adresse personnelle à changer partout | **accès à une boîte fonctionnelle** | transfert de droits |
| Détection d'un envoi manquant | non | **oui** (séquences) | oui |
| Hors-ligne sur le terrain | oui | **oui** | synchronisation différée à gérer |
| Évolution vers l'option 2 | — | **sans changer le format** | — |

Le point décisif : **le format du fichier est le contrat, le mail n'est que le
transport.** Si un jour la DSI ouvre un SharePoint accessible des deux côtés, le
magasinier choisira simplement l'application OneDrive/SharePoint dans le même menu de
partage Android, **sans que l'application change** — elle ne gagne toujours aucune
permission réseau.

## 5. Le flux descendant : diffusion du référentiel

Il **ne remet pas en cause l'option 1+** : le mail marche dans les deux sens. Il ajoute
en revanche une exigence que les deux options partagent : **le versionnage**.

```
Comptable : classeur référentiel ──(macro)──► REFERENTIEL_v043_20260924.csv
        ──► mail depuis la boîte fonctionnelle vers la liste de diffusion des magasiniers
Magasinier : touche la pièce jointe ──► « Ouvrir avec SuivieMAT »
        (repli : import par le sélecteur de fichiers, déjà existant dans Comparer)
Application : contrôle ──► résumé avant remplacement ──► version active = 43
        ──► évènement REFERENTIEL_CHARGE dans le journal
```

Contrôles côté application :

- **format** reconnu et **version** strictement supérieure à la version active (pas de
  retour arrière silencieux) ;
- **fichier complet** : ligne de fin avec le nombre de lignes et un hash — détecte un
  fichier tronqué ;
- **résumé avant remplacement** : nombre de parents et d'enfants, ajouts et retraits par
  rapport à la version active ;
- chaque ligne du journal porte **la version du référentiel utilisée** : le comptable
  sait sur quelle base chaque relevé a été fait, et repère ceux faits sur une version
  périmée.

> ⚠️ **Encodage.** Un « CSV (séparateur : point-virgule) » enregistré par Excel 2016 est
> en **Windows-1252**, pas en UTF-8 (l'option « CSV UTF-8 » manque selon les builds
> d'Excel 2016). Or l'import actuel décode **uniquement en UTF-8**
> (`CompareStartViewModel.kt:71`) : toutes les désignations accentuées arriveraient
> corrompues. L'import doit reconnaître l'UTF-8 (avec ou sans BOM) et **retomber sur
> Windows-1252** sinon — ce qui évite aussi au VBA d'avoir à écrire de l'UTF-8.

Piste ultérieure : le mail de référentiel peut porter un **accusé de réception** (dernière
séquence reçue par appareil), qui autoriserait l'application à purger ce qui est
confirmé. Sans lui, un téléphone conserve tout son journal.

## 6. Le workflow métier dans l'application

```
INVENTAIRE ─► scan du parent (OCR | Code 2D | Code-barres | NFC/RFID)
      │
      ├── parent absent du référentiel ──► bandeau gris fugace, rien d'enregistré
      │                                    (même règle que la décision 14)
      ▼
   évènement OUVERTURE_PARENT
      ▼
   liste des enfants, quantité théorique affichée
      ├── à relever      (aucune saisie)
      ├── conforme       (relevé = théorique)
      └── écart          (relevé ≠ théorique)
      ▼
   saisie : boutons − / + , champ numérique, « = théorique » en un geste
      ▼   (chaque saisie = un évènement RELEVE, valeur absolue, la dernière fait foi)
   CLOTURE_PARENT ──► prêt pour l'export
```

Points de conception :

- **Valeur absolue, la dernière fait foi** plutôt qu'incréments : un relevé se relit sans
  recalcul ; les valeurs précédentes restent dans le journal pour l'audit.
- Le bouton **« = théorique »** est un gain de temps majeur, mais un biais d'audit (on
  confirme sans compter). Le journal distingue donc une quantité **saisie** d'une
  quantité **confirmée en bloc**.
- La saisie des quantités s'écarte du principe « une main, sans toucher l'écran » : c'est
  inévitable pour compter, mais le **scan du parent** reste sur le chemin critique et ne
  doit rien perdre de sa vitesse (aucune suspension entre détection et retour à
  l'analyse — le calcul d'un SHA-256 sur 200 octets se fait avec l'écriture, sur
  `viewModelScope`, jamais sur le thread d'analyse).
- Le code couleur des lignes enfants est **à faire valider** (D6) : le rouge/vert de
  Comparer est une règle imposée pour Comparer, pas nécessairement pour l'inventaire, où
  l'**écart** est un troisième état légitime.

## 7. Modèle de données proposé

Montée de version de la base **1 → 2**, avec migration (le schéma est exporté dans
`app/schemas/` et `room-testing` est déjà en dépendance : la migration se teste). C'est
aussi l'occasion prévue par la décision 15 de **supprimer `intruder_entries`**.

### `referentiel_versions`
| Colonne | Type | Note |
|---|---|---|
| `version` | Int PK | fournie par le comptable, strictement croissante |
| `fileName` | String | nom du fichier reçu |
| `fileHash` | String | SHA-256 du fichier |
| `loadedAt` | Long | |
| `active` | Boolean | une seule version active |

### `referentiel_items`
| Colonne | Type | Note |
|---|---|---|
| `version` | Int FK | |
| `parentCode` | String | **normalisé à l'insertion** (même règle que `reference_entries`) |
| `parentLabel` | String? | |
| `childCode` | String | normalisé à l'insertion |
| `childLabel` | String? | |
| `qtyTheoretical` | Int | |
| `unit` | String? | |

Index unique `(version, parentCode, childCode)`.

### `ledger_entries` — **ajout seul, triggers SQL**
| Colonne | Type | Note |
|---|---|---|
| `deviceId` | String | UUID généré à l'enrôlement (pas `ANDROID_ID`) |
| `seq` | Long | sans trou, par appareil ; index unique `(deviceId, seq)` |
| `event` | enum | `ENROLEMENT`, `IDENTIFICATION`, `REFERENTIEL_CHARGE`, `OUVERTURE_PARENT`, `RELEVE`, `CONFIRMATION_BLOC`, `ANNULATION`, `CLOTURE_PARENT`, `EXPORT` |
| `operator` / `site` / `campaign` | String | fixés à l'enrôlement ou en début de session |
| `cardVerified` | Boolean | la session a été ouverte par la présentation de la carte enrôlée (§ 10 ter) — l'identifiant de la carte, lui, **ne quitte jamais le téléphone** |
| `autoTime` | Boolean | réglage « date et heure automatiques » actif au moment de la saisie |
| `referentielVersion` | Int | |
| `parentCode` / `childCode` | String? | |
| `qtyTheoretical` / `qtyCounted` | Int? | |
| `scanMode` | enum? | pour `OUVERTURE_PARENT` |
| `cancelsSeq` | Long? | pour `ANNULATION` |
| `recordedAtUtc` | Long | epoch ms |
| `utcOffsetMin` | Int | décalage au moment de la saisie |
| `elapsedRealtime` / `bootCount` | Long / Int | détection d'une horloge reculée |
| `prevHash` / `hash` | String | chaîne SHA-256 (HMAC au niveau 2) |

## 8. Le contrat d'échange (ébauche, à figer à l'étape S1)

### Montant : journal exporté

Nom : `SUIVIMAT_<site>_<appareil>_<seqDébut>-<seqFin>_<AAAAMMJJ-HHMM>.csv` — UTF-8 avec
BOM, `;`, CRLF, **une ligne = un évènement**, tabulaire pur (lisible tel quel par Power
Query comme par VBA).

```csv
fmt;appareil;seq;horodatage_utc;decalage;heure_auto;operateur;carte_verifiee;site;campagne;version_ref;evenement;code_parent;code_enfant;qte_theorique;qte_relevee;mode_scan;annule_seq;hash_prec;hash
1;TEL-07;128;2026-09-24T12:03:12Z;+02:00;1;DUPONT J.;1;MAG-NORD;INV-2026-T4;43;RELEVE;BJ-568A/00090;CAB-220;4;3;;;9f2c…;41ab…
```

Pas de désignation dans le fichier montant (proposition D17) : il ne transporte que des
codes et des quantités, le comptable rejoint les libellés depuis son propre référentiel.
Moins il y a de contenu dans une messagerie personnelle, mieux c'est.

Chaque export reprend **à partir de la séquence qui suit le dernier export**. Renvoyer une
plage déjà envoyée est sans danger : le comptable dédoublonne sur `(appareil, seq)`, et
**une même séquence reçue avec deux hash différents** déclenche une alerte de
falsification.

### Descendant : référentiel

Nom : `REFERENTIEL_v<version>_<AAAAMMJJ>.csv`

```csv
version_ref;code_parent;designation_parent;code_enfant;designation_enfant;qte_theorique;unite
43;BJ-568A/00090;Boîtier BJ-568A;CAB-220;Câble 220 V;4;u
#FIN;lignes=1;sha256=…
```

## 9. Le poste comptable (Excel 2016)

Le kit comptable est un **livrable à part entière**, pas un détail : c'est la partie la
plus contrainte du projet. Il doit donc être **prototypé et validé sur le vrai poste
avant d'écrire le code Android** (étape S2).

Deux moteurs possibles, non exclusifs :

| | VBA | Power Query (intégré à Excel 2016 : *Données › Nouvelle requête › À partir d'un dossier*) |
|---|---|---|
| Consolider un dossier de `.csv` | ✅ | ✅ **sans aucune macro**, actualisable en un clic |
| Importer les colonnes **en texte** | ✅ | ✅ |
| Vérifier la chaîne SHA-256 | ✅ (VBA pur) | ❌ |
| Contrôle de continuité, tri, synthèse | ✅ | ✅ (tri, fusion, synthèse) |
| Extraire les pièces jointes d'Outlook | ✅ (automation Outlook, à tester — l'Object Model Guard peut afficher une invite selon la GPO) | ❌ |
| Générer le référentiel | ✅ | ❌ |

**Vérifié le 2026-09-24 : VBA est déjà utilisé sur le poste et fonctionne.** VBA devient
donc le moteur principal du kit ; Power Query reste un repli, sans plus.

Règles de conception du kit, pour passer un EDR strict :

- **VBA pur uniquement** : pas de `Declare` (appels d'API Win32 — bloqués par la règle
  ASR de Defender « Bloquer les appels d'API Win32 depuis les macros Office »), pas de
  `Shell`, pas d'`ADODB.Stream`, pas de téléchargement. Ce sont précisément les signatures
  des macros malveillantes que l'EDR recherche ;
- pas de `CreateObject("System.Security.Cryptography…")` : dépend du .NET Framework 3.5,
  souvent absent ;
- **import en texte** de toutes les colonnes : sinon Excel supprime les zéros de tête
  (`00090` → `90`), passe les longs numéros de série en notation scientifique et
  convertit ce qui ressemble à une date ;
- **ne jamais réenregistrer un fichier source** : il est archivé tel que reçu ;
- classeur **signé** ou placé dans un **emplacement approuvé**, selon la politique de la
  DSI.

## 10. Sécurité — constats sur le code actuel

Ces points existent **dès aujourd'hui** dans ScanList, indépendamment du projet :

| # | Constat | Où | Risque | Correctif proposé |
|---|---|---|---|---|
| C1 | **Aucune neutralisation de l'injection de formule CSV** : une valeur commençant par `=`, `+`, `-` ou `@` est exportée telle quelle | `data/csv/CsvCodec.kt:51` (`escapeField`) | Un QR code est **contrôlable par n'importe qui** : un code contenant `=HYPERLINK(…)` ou une formule DDE devient une **formule active** quand le comptable ouvre le fichier dans Excel. C'est l'attaque la plus directe contre un poste « sécurisé » | Refuser ces valeurs à la capture (cohérent avec « ne rien faire », décision 7) **ou** les neutraliser à l'export — à trancher (D7). Le kit importe de toute façon en texte |
| C2 | Import décodé **uniquement en UTF-8** | `ui/compare/CompareStartViewModel.kt:71` | Référentiel issu d'Excel 2016 (Windows-1252) : désignations corrompues | Détection UTF-8, repli Windows-1252 |
| C3 | Horodatage **local sans décalage** | `data/csv/ScanCsvBuilder.kt:22` | Heure ambiguë au passage à l'heure d'hiver ; ordre faux entre fuseaux | UTC + décalage (§ 3) |
| C4 | `android:allowBackup="true"` | `AndroidManifest.xml` | La base peut partir dans la sauvegarde Google du compte personnel de l'utilisateur — **certain** sur un téléphone personnel, où aucun MDM ne l'empêche | `allowBackup="false"` (D12) — **prioritaire** |
| C5 | Suppression de lignes et de dossiers, renommage de dossier | `ScanItemDao`, `FolderDao` | Incompatible avec l'intangibilité **si** le journal couvrait les dossiers de scan | Journal limité à la nouvelle fonction Inventaire (D4) |
| C6 | **Aucune configuration de signature** (`signingConfigs` absent) : chaque machine de compilation signe avec sa propre clé de debug | `app/build.gradle.kts` | Un APK compilé en local et un APK compilé par la CI ne peuvent pas se mettre à jour l'un l'autre ; il faut désinstaller, **ce qui efface le journal non exporté**. Sur des téléphones personnels installés à la main, c'est une perte de données assurée à la première mise à jour | Une clé de release unique, créée une fois, **avant la première installation chez un magasinier** — la première signature installée verrouille toutes les suivantes (D13) |
| C7 | Release limitée à `arm64-v8a` | `app/build.gradle.kts` (décision 10) | Décidé pour le seul Galaxy S25 ; un téléphone personnel 32 bits refusera l'installation | Selon la liste des téléphones réels (U9) : ajouter `armeabi-v7a` si besoin |

> **C1 devient prioritaire** avec la réponse de l'utilisateur : « tant que c'est du texte
> et pas du script, ça passe ». Une formule Excel **est** du texte — la passerelle la
> laisse passer. Le danger est à l'ouverture : un comptable qui double-clique sur la pièce
> jointe dans Outlook fait interpréter la formule par Excel (lien piégé, extraction de
> données du classeur, voire lancement de commande selon la configuration DDE d'Excel).
> Le kit VBA, qui importe en texte, n'est pas exposé ; le double-clic, si.

Point RGPD : le nom de l'opérateur dans le journal est une **donnée personnelle** →
inscription au registre des traitements de l'entreprise, durée de conservation à fixer
(Q8).

## 10 bis. Conséquences des téléphones personnels

*Réponse de l'utilisateur du 2026-09-24 : l'application sera installée sur les
téléphones **personnels** des magasiniers.*

Sans MDM, rien n'est imposé au téléphone : chaque garantie que la DSI aurait pu
configurer doit être portée par l'application elle-même, ou acceptée comme absente.

| Sujet | Conséquence | Réponse proposée |
|---|---|---|
| **Heure** | Impossible d'imposer l'heure automatique (Q7 caduque) | Le journal enregistre l'état du réglage à chaque saisie (`autoTime`, § 7) : le comptable voit les saisies faites en heure manuelle. Option : **refuser l'ouverture d'un inventaire** tant que l'heure automatique est désactivée (D16) |
| **Signature et mises à jour** | Voir C6 : sans clé unique, chaque mise à jour efface le journal | Clé de release créée une fois, conservée hors du dépôt (secret de la CI + copie hors ligne) (D13) |
| **Distribution** | Pas de magasin d'entreprise : installation manuelle (« sources inconnues ») | Canal à choisir (D13). Empreinte SHA-256 de l'APK communiquée par un autre canal que l'APK. Une fois la première version installée, Android **refuse** toute mise à jour signée par une autre clé : une APK piégée ne peut pas remplacer la vraie |
| **Données au repos** | Verrouillage de l'écran et chiffrement laissés au bon vouloir du propriétaire ; sauvegarde vers son compte Google personnel | C4 prioritaire ; **verrou applicatif** (empreinte ou code via `BiometricPrompt`) à l'ouverture de l'application (D15) ; chiffrement de la base (SQLCipher) seulement si la sensibilité l'exige — nouvelle dépendance |
| **Données en transit** | Le `.csv` passe par les serveurs du fournisseur de messagerie personnelle et reste dans le dossier « Envoyés » du magasinier. Le filtre de la passerelle vérifie la **nature** du fichier (du texte), pas la **confidentialité** de son contenu | Question Q11 au responsable de la sécurité — d'autant que la bibliothèque de formules préchargée vise des numéros de série d'armes (`HK416`). Minimisation : codes et quantités seulement, pas de désignations (D17) |
| **Parc hétérogène** | Modèles, versions d'Android, caméras et présence du NFC varient d'un magasinier à l'autre | Liste des téléphones réels (U9) ; C7 ; le mode NFC gère déjà l'absence de puce (`NfcScanSurface`) |
| **Départ d'un magasinier** | Le téléphone lui appartient : l'application et ses données partent avec lui | Procédure de sortie : export final, désinstallation constatée, appareil **clos** dans le kit (plus aucun fichier accepté de lui) |
| **Téléphone partagé ou prêté** | Un téléphone personnel n'est pas forcément utilisé par une seule personne | Identification par carte à chaque ouverture de session (§ 10 ter) |

## 10 ter. Identification par carte à puce

*Réponse de l'utilisateur du 2026-09-24 : les magasiniers pourront s'identifier avec une
carte à puce ; la clé privée n'est pas disponible, ce n'est donc pas une certitude, mais
une couche de vérification.*

« Carte à puce SIM » peut désigner deux choses aux conséquences opposées — **à préciser
(U10)** :

**A. La carte SIM du téléphone — inutilisable en pratique.**
Depuis Android 10, une application ordinaire ne peut plus lire l'ICCID ni l'IMSI
(réservé aux applications système et opérateur). Le numéro de téléphone demande une
permission supplémentaire et n'est souvent pas inscrit sur la SIM. Et une SIM identifie
l'**abonnement personnel** du magasinier, pas l'agent.

**B. Une carte professionnelle (badge, carte agent) présentée au dos du téléphone, en
NFC.** L'application sait déjà lire une puce sans contact (mode NFC, `NfcValue`) : c'est
exactement ce que fait la fonction Comparer avec l'UID d'un tag NFC, sans rien y ajouter.

### Décision finale (2026-09-25) : niveau 1 seul

Le badge présenté par l'utilisateur pour ses tests s'est révélé être une **carte CIMS**
(Carte d'Identité Multi-Services, ministère des Armées). Une CIMS porte un certificat géré
par l'IGC du ministère, interne et non publique : aucune application hors du ministère ne
peut en vérifier l'authenticité de façon honnête. En vérifier l'authenticité n'est donc
**pas envisagé** — ce serait du théâtre de sécurité (afficher « authentique » sans pouvoir
réellement écarter une contrefaçon), et ce n'est pas le rôle d'une application d'inventaire
privée sur téléphone personnel.

**Ce qui est retenu :** l'identification se fait au **niveau 1 seulement** — lecture de
l'identifiant (UID) de la carte, comme pour n'importe quel tag NFC, via le mode NFC déjà
implémenté (`ScanMode.NFC`, `NfcValue`, `NfcTagDecoder`). **Aucun développement
supplémentaire n'est nécessaire pour cela** : c'est le chemin de lecture NFC déjà en
place, déjà testé. Ce n'est qu'une couche de commodité (un UID peut être recopié), pas une
preuve d'identité — cohérent avec la remarque initiale de l'utilisateur (« pas une
certitude, une couche de vérification »).

**Aucun matériel de test à fabriquer** : la carte CIMS du magasinier sert elle-même de
badge d'enrôlement, sans besoin de dupliquer un dispositif NFC de substitution.

**Écran de diagnostic retiré (2026-09-25).** L'écran « Test du badge » et toute la
logique associée (`domain/card/`, `ui/cardtest/`, `scanner/CardTestReader.kt`, décision 22
de `contexte.md`), construits pour explorer une lecture de certificat et un défi-réponse,
ont été supprimés du code avec leurs tests : cette voie est refermée.

**Enrôlement et usage courant (D14) :**

- **enrôlement** une fois, en présence du référent : la carte est présentée, le
  téléphone l'associe au matricule du magasinier (évènement `ENROLEMENT`) ;
- **présentation de la carte à l'ouverture de chaque session d'inventaire**, jamais à
  chaque saisie — le rythme du relevé n'en souffre pas ;
- **l'identifiant de la carte ne quitte jamais le téléphone** : le journal porte le
  matricule et `carte_verifiee = 1`. Exporter l'UID d'un badge qui ouvre aussi des portes
  dans un `.csv` qui transite par une messagerie personnelle reviendrait à diffuser de
  quoi le cloner ;
- un téléphone **sans puce NFC** ne peut pas vérifier la carte : session ouverte en
  déclaratif, `carte_verifiee = 0`, visible du comptable.

## 11. Impacts sur l'existant et décisions à valider

| # | Proposition | Touche à | Recommandation | État |
|---|---|---|---|---|
| D1 | Transport montant : **option 1+** (mail vers une boîte fonctionnelle, format = contrat) | Décision 1 (réutilisée), § 6 hors périmètre (respecté) | ✅ recommandé | ⬜ à valider |
| D2 | Transport descendant : mail + « Ouvrir avec », référentiel versionné, import tolérant à l'encodage | Import CSV existant | ✅ recommandé | ⬜ à valider |
| D3 | Niveau d'intégrité : **1** (chaîne SHA-256) ou **2** (HMAC par appareil) | — | 1 d'office, 2 si l'audit l'exige | ⬜ à valider |
| D4 | Journal en ajout seul **limité à la fonction Inventaire** ; les dossiers de scan gardent leur comportement (suppression annulable, renommage) | Phase 6 | ✅ recommandé — ne casse rien d'existant | ⬜ à valider |
| D5 | Enfant **en surplus** (présent mais hors de la configuration théorique) : enregistré comme écart, ou ignoré comme dans Comparer ? | Décision 14 (qui ne vaut que pour Comparer) | Enregistrer : en inventaire comptable, un surplus est un constat | ⬜ à valider |
| D6 | États et couleurs des lignes enfants : à relever / conforme / écart | Charte couleurs | — | ⬜ à valider |
| D7 | Injection CSV (C1) : refus à la capture ou neutralisation à l'export | Décision 7 | Refus à la capture : ne modifie pas la valeur, donc ne perturbe pas la chaîne de hash | ⬜ à valider |
| D8 | Consensus (3/5 lectures) sur le scan du **parent** | Décision 19 (portée limitée au scan de dossier) | Oui : une mauvaise lecture ouvrirait le mauvais parent | ⬜ à valider |
| D9 | Nouvelle branche **INVENTAIRE** au menu principal | § 4 de `contexte.md` (hiérarchie imposée) | — | ⬜ à valider |
| D10 | Nom de l'application (SuivieMAT) et `applicationId` | § 3 de `outils.md` | **Validé et fait** (2026-09-25, décision 23 de `contexte.md`) : `applicationId` → `com.suiviemat.app`, nom affiché → « SuivieMAT ». L'utilisateur a confirmé que ScanList est une **application indépendante** qu'il garde en l'état — c'est précisément ce que cette proposition anticipait : changer l'`applicationId` crée une application neuve pour Android, ce qui est ici l'effet recherché, pas un risque | ✅ **fait** |
| D11 | Valorisation (prix) **exclue** du téléphone | — | ✅ recommandé (moindre donnée sensible sur le terrain) | ⬜ à valider |
| D12 | `allowBackup="false"` (C4) | Manifest | ✅ recommandé — **prioritaire** depuis la réponse « téléphones personnels » | ⬜ à valider |
| D13 | **Clé de signature de release unique** (C6) et **canal de distribution** de l'APK : remise en main propre par un référent, lien de téléchargement interne, ou piste de test fermée Google Play | `app/build.gradle.kts`, CI | Clé : ✅ indispensable, à créer avant toute installation chez un magasinier. Canal : à choisir | 🔄 Clé : **reportée juste avant la distribution** (utilisateur, 2026-09-24), inscrite au § 0. Canal : ⬜ à valider |
| D14 | Identification par **carte à puce en NFC, niveau 1 seul** (lecture d'UID) à l'ouverture de chaque session, identifiant jamais exporté (§ 10 ter) | Mode NFC existant, déjà implémenté — aucun code nouveau | **Validé et fait** (2026-09-25) : le badge des magasiniers est une carte CIMS, dont l'authenticité ne peut pas être vérifiée par cette application (IGC interne au ministère) — seul le niveau 1 est retenu | ✅ **fait** |
| D15 | **Verrou applicatif** (empreinte ou code) à l'ouverture de l'application | Nouvelle dépendance `androidx.biometric` | ✅ recommandé (aucun verrouillage n'est garanti sur un téléphone personnel) | ⬜ à valider |
| D16 | Heure automatique désactivée : **signaler** seulement, ou **bloquer** l'ouverture d'un inventaire | — | Bloquer : rien ne permet sinon de rattraper l'heure après coup | ⬜ à valider |
| D17 | **Minimisation** du fichier montant : codes et quantités, pas de désignations | Format d'échange § 8 | ✅ recommandé | ⬜ à valider |

## 12. Questions ouvertes

### À la DSI — bloquantes, à poser en premier

| # | Question | Pourquoi c'est bloquant | Réponse |
|---|---|---|---|
| Q1 | Les téléphones des magasiniers sont-ils **gérés par un MDM** (Intune ou autre) ? Professionnels ou personnels ? | Conditionne tout le reste | ✅ **Personnels** (2026-09-24), donc a priori sans MDM — voir § 10 bis |
| Q2 | Une **APK interne** peut-elle être installée ? Par quel canal ? | Sans canal de distribution, pas de projet | 🔄 Installation manuelle sur téléphone personnel ; canal à choisir (D13) |
| Q3 | Quel client mail sur les téléphones ? Une **politique de protection d'application** empêche-t-elle de joindre un fichier provenant d'une application non gérée ? | Peut bloquer l'option 1 | ✅ Messagerie personnelle du magasinier, sans politique (2026-09-24) |
| Q4 | Les **macros VBA** sont-elles autorisées sur le poste comptable ? | Conditionne la vérification d'intégrité | ✅ **VBA déjà utilisé, fonctionnel** (2026-09-24). Règles ASR inconnues : le kit reste en VBA pur par prudence (§ 9) |
| Q5 | **Power Query** est-il utilisable ? | Repli si les macros sont interdites | ➖ Sans objet, VBA disponible |
| Q6 | Une **boîte fonctionnelle** et une **liste de diffusion** peuvent-elles être créées ? Outlook est-il le client du poste comptable ? | Option 1+ | ⬜ (Outlook pro confirmé côté comptable) |
| Q7 | Le MDM peut-il **imposer l'heure automatique** sur les téléphones ? | Fiabilité de l'horodatage | ❌ Caduque : pas de MDM. Remplacée par la journalisation du réglage (`autoTime`) et D16 |
| Q8 | Durée de conservation des pièces, lieu d'archivage, registre RGPD | Conformité | ⬜ |
| Q9 | Un `.csv` joint à un mail passe-t-il la passerelle sans être bloqué ni modifié ? | Option 1 | ✅ **Vérifié** : messagerie personnelle → Outlook pro, le `.csv` passe tant que c'est du texte (2026-09-24) |
| Q10 | **Sens inverse** : un `.csv` envoyé d'Outlook pro **vers une messagerie personnelle** passe-t-il ? (une règle de prévention des fuites peut filtrer le sortant) | Diffusion du référentiel | ⬜ — **à tester tout de suite**, c'est le même geste que Q9 dans l'autre sens |
| Q11 | La politique de classification autorise-t-elle ces données (numéros de série, configurations, quantités) **sur un téléphone personnel** et **dans une messagerie personnelle** ? | Conformité de toute l'option 1 | ⬜ — distinct de Q9 : la passerelle juge la nature du fichier, pas la sensibilité de son contenu |

### À l'utilisateur — métier

| # | Question | Réponse |
|---|---|---|
| U1 | Volumes : nombre de magasiniers, de sites, fréquence d'envoi, taille du référentiel (parents, enfants par parent) | ⬜ |
| U2 | Les enfants sont-ils **comptés en quantité**, **identifiés un par un** (numéro de série scanné), ou les deux ? | ⬜ |
| U3 | Nomenclature **à plusieurs niveaux** (un enfant peut-il être lui-même parent) ? | ⬜ |
| U4 | Existe-t-il une notion de **campagne** d'inventaire (ex. `INV-2026-T4`) ? | ⬜ |
| U5 | Un même parent peut-il être relevé par **plusieurs magasiniers** ? Que fait la synthèse en cas de désaccord ? | ⬜ |
| U6 | Qui audite (contrôle interne, commissaire aux comptes) et **quel niveau de preuve** est attendu ? Un horodatage opposable est-il exigé ? | ⬜ |
| U7 | Que contient exactement la **synthèse** du comptable (écarts par parent, par site, valorisation…) ? | ⬜ |
| U8 | Parent **absent du référentiel** : ignoré (règle actuelle) ou signalé au comptable ? | ⬜ |
| U9 | **Liste des téléphones** des magasiniers : modèle, version d'Android, présence du NFC | ⬜ |
| U10 | La « carte à puce SIM » : **carte SIM du téléphone** ou **carte professionnelle sans contact** (badge, carte agent) ? | ✅ **Badge professionnel sans contact, format carte bancaire** (2026-09-24). Norme suivie : à établir avec l'écran « Test du badge » (§ 10 ter) |

## 13. Risques

| Risque | Effet | Parade |
|---|---|---|
| ~~APK non distribuable (Q2)~~ | — | Levé : installation manuelle sur téléphones personnels |
| ~~Macros interdites (Q4)~~ | — | Levé : VBA opérationnel |
| ~~Politique MDM de partage (Q3)~~ | — | Levé : messagerie personnelle, passage vérifié |
| Mise à jour signée par une autre clé (C6) | Désinstallation obligatoire, **journal non exporté perdu** | Clé de release unique avant la première installation (D13) ; exporter avant toute mise à jour |
| Sortant pro → personnel filtré (Q10) | Plus de diffusion du référentiel par mail | Tester tout de suite ; repli : remise du fichier par un référent |
| Données refusées sur appareils personnels (Q11) | Remet en cause l'option 1 elle-même | Poser la question au responsable de la sécurité avant le développement |
| Heure réglée à la main | Horodatage faux, non détectable par l'heure seule | `autoTime` journalisé, blocage proposé (D16), l'ordre fait foi par `seq` |
| UID de badge exporté | Clonage du badge (s'il ouvre aussi des portes) | L'identifiant de carte ne quitte jamais le téléphone (§ 10 ter) |
| Carte à UID aléatoire | Niveau 1 d'identification inopérant | Niveau 2 (certificat), selon la spécification de la carte |
| Divergence Kotlin / VBA sur la forme canonique | Toutes les chaînes « rompues » | Vecteurs de test partagés (S1) |
| Excel reformate les valeurs | Codes faussés, hash invalides | Import en texte, fichiers sources jamais réenregistrés |
| Téléphone perdu avant export | Relevés perdus | Exports fréquents ; le trou de séquence au moins se voit |
| Décodage réel jamais validé (phase 10 de ScanList) | Scan du parent non fiable | La phase 10 reste un préalable au pilote |

## 14. Feuille de route

> Numérotation **S0–S9**, distincte des phases 0–10 de ScanList (`workflow.md` § 6), qui
> restent à terminer. Charge : **S** petite, **M** moyenne, **L** importante — relative,
> pas en jours.

| # | Étape | Contenu | Dépend de | Charge | État |
|---|---|---|---|---|---|
| S0 | **Cadrage** | Réponses Q1–Q11 et U1–U10, validation D1–D17. Acquis : Q1, Q3, Q4, Q9, U10. Tests immédiats : envoi pro → personnel (Q10), **test physique du badge avec l'écran « Test du badge »** (§ 10 ter) | — | S | 🔄 En cours |
| S1 | **Contrat d'échange** | Spécification figée des deux formats, forme canonique du hash, fichiers spécimens, vecteurs de test | S0 | S | ⬜ À faire |
| S2 | **Prototype poste comptable** | Kit Excel sur fichiers spécimens, **testé sur le vrai poste** : import en texte, vérification de chaîne, continuité, tri, synthèse, génération du référentiel | S1 | M | ⬜ À faire |
| S3 | **Correctifs du socle** | **C6 clé de signature (avant toute installation chez un magasinier)**, C1 injection CSV, C4 sauvegarde, C2 encodage d'import, C3 horodatage UTC, C7 ABI selon U9 | S0 | S | ⬜ À faire |
| S4 | **Référentiel dans l'application** | Tables, import versionné et contrôlé, « Ouvrir avec », résumé avant remplacement, historique des versions | S1 | M | ⬜ À faire |
| S5 | **Journal en ajout seul** | Table, triggers SQL, séquence, chaîne de hash, annulation, migration 1 → 2 (et retrait d'`intruder_entries`) | S1 | M | ⬜ À faire |
| S6 | **Écran Inventaire** | Scan du parent → liste des enfants → saisie des quantités → clôture | S4, S5 | L | ⬜ À faire |
| S7 | **Export par lots** | Plage de séquences, nommage, évènement `EXPORT`, envoi | S5 | S | ⬜ À faire |
| S8 | **Enrôlement et sécurité** | Identifiant d'appareil, enrôlement de la carte (§ 10 ter), identification à l'ouverture de session, verrou applicatif (D15), contrôle de l'heure automatique (D16), clé HMAC (si D3 = 2), procédure de distribution et de sortie d'un magasinier | S0 | M | ⬜ À faire |
| S9 | **Pilote** | 1 ou 2 magasiniers, 1 comptable, un cycle complet montant + descendant | tout + phase 10 | M | ⬜ À faire |

Ce qui est vérifiable où :

- **session cloud** : écriture du code et des tests JVM ; l'APK et les tests passent par
  la CI GitHub (pas de SDK Android local) ;
- **CI** : `testDebugUnitTest`, `assembleDebug`, vecteurs de test côté Kotlin ;
- **poste comptable uniquement** : tout le kit Excel/VBA — il ne peut s'exécuter ni en
  session cloud ni en CI ;
- **Galaxy S25 et téléphones des magasiniers** : décodage réel, partage vers la
  messagerie personnelle, lecture de la carte à puce.

## 15. Journal du plan

| Date | Évolution |
|---|---|
| 2026-09-24 | Création : analyse de faisabilité, recommandation option 1+, modèle de journal, ébauche du contrat d'échange, constats de sécurité C1–C5, questions Q1–Q9 / U1–U8, décisions à valider D1–D12, feuille de route S0–S9. **Aucune décision validée, aucun code modifié.** |
| 2026-09-24 | **Premières réponses de l'utilisateur.** Téléphones **personnels** (Q1) ; VBA déjà utilisé et fonctionnel (Q4) ; `.csv` d'une messagerie personnelle vers Outlook pro vérifié (Q3, Q9) ; identification par **carte à puce**, sans clé privée, comme couche de vérification. Conséquences : nouvelle section § 10 bis (téléphones personnels : l'application porte seule les garanties qu'un MDM aurait imposées) et § 10 ter (identification par carte, trois niveaux — le défi-réponse ne demande **pas** de détenir la clé privée) ; C1 et C4 deviennent prioritaires ; deux nouveaux constats, **C6 (aucune clé de signature : chaque mise à jour effacerait le journal)** et C7 (release arm64 seulement) ; décisions D13–D17, questions Q10–Q11 et U9–U10 ajoutées ; Q7 caduque. S0 passe en cours. **Aucune décision validée, aucun code modifié.** |
| 2026-09-24 à 25 | **Écran « Test du badge » : construit, testé, puis retiré.** À la demande de l'utilisateur, un écran de diagnostic (décision 22) a exploré une identification aux niveaux 2 (certificat) et 3 (défi-réponse) sur le badge des magasiniers, sans jamais envoyer de code PIN. Les tests physiques ont montré que ce badge est une **carte CIMS** (ministère des Armées) : son authenticité ne peut pas être vérifiée par cette application, l'IGC qui la gère étant interne au ministère. **Décision du 2026-09-25 : identification arrêtée au niveau 1** (lecture d'UID, via le mode NFC déjà existant, sans code nouveau), **écran de diagnostic et toute sa logique retirés du code** (`domain/card/`, `ui/cardtest/`, `scanner/CardTestReader.kt` supprimés). Voir § 10 ter. |
| 2026-09-25 | **D10 validée : SuivieMAT devient une application distincte de ScanList.** L'utilisateur confirme que ScanList est une application indépendante, gardée en l'état — les deux se seraient superposées sous l'ancien `applicationId` hérité du projet d'origine. `applicationId` → `com.suiviemat.app`, nom affiché → « SuivieMAT » (décision 23 de `contexte.md`). |
