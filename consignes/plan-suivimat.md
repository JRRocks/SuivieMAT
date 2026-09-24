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
> Créé le : 2026-09-24

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
| La **complétude** : un fichier manquant se voit (trou dans les séquences) | L'**identité** certaine de l'opérateur : sans compte, elle est déclarative |

Un horodatage **opposable** (horodatage qualifié eIDAS, signature électronique) exige un
tiers en ligne : hors de portée d'une application sans réseau. Si l'audit l'exige, c'est
l'option 2 avec un serveur de la DSI — à savoir **avant** de développer (question U6).

**Trois inconnues peuvent tout bloquer** et doivent être levées en premier auprès de la
DSI (§ 12) : la **distribution de l'APK** sur des téléphones d'entreprise, l'**autorisation
des macros** sur le poste comptable, et la **politique MDM de partage** entre applications
(une politique Intune peut interdire de joindre un fichier d'une application non gérée à
Outlook).

## 3. Les contraintes comptables, traduites en technique

| Principe comptable / d'audit | Traduction dans l'application | Traduction côté comptable |
|---|---|---|
| **Intangibilité** : une écriture validée ne se modifie ni ne se supprime ; on la corrige par une contre-écriture | Table `ledger_entries` **en ajout seul**. Des **triggers SQLite** `BEFORE UPDATE` / `BEFORE DELETE` → `RAISE(ABORT)` rendent la règle impossible à contourner par du code — même philosophie que l'index unique `(folderId, value)` : la règle métier est portée par le moteur SQL. Une erreur de saisie se corrige par un évènement `ANNULATION` qui référence la séquence annulée | Les fichiers reçus sont des **pièces justificatives** : archivés en lecture seule, **jamais ouverts puis réenregistrés dans Excel** (Excel réécrit l'encodage, supprime les zéros de tête, convertit les dates) |
| **Numérotation continue et chronologique** | `seq` par appareil, sans trou, attribué dans la même transaction que l'insertion, index unique `(appareil, seq)` | Contrôle de continuité : la première séquence d'un fichier = la dernière du fichier précédent + 1, sinon **alerte « fichier manquant »** |
| **Piste d'audit** : qui, quoi, quand, sur quelle base | Chaque ligne porte l'appareil, l'opérateur, le site, la campagne, la **version du référentiel** utilisée, le mode de scan | Synthèse filtrable par site, opérateur, campagne ; signalement des relevés faits sur un référentiel périmé |
| **Horodatage** | Heure UTC **et** décalage horaire (`2026-09-24T12:03:12Z` + `+02:00`) — le format actuel, local sans décalage, rend ambiguë l'heure qui se répète au passage à l'heure d'hiver. En complément : `elapsedRealtime` et `BOOT_COUNT` (API 24+) pour **détecter** une horloge reculée | Tri : par `(appareil, seq)` à l'intérieur d'un appareil — **c'est l'ordre qui fait foi** ; par horodatage UTC entre appareils. Une séquence croissante dont l'heure recule est signalée « anomalie d'horloge » |
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
- **politique MDM** : sur un téléphone géré, une politique de protection d'application
  peut interdire le partage d'une application non gérée vers Outlook (question Q3).

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
| `event` | enum | `REFERENTIEL_CHARGE`, `OUVERTURE_PARENT`, `RELEVE`, `CONFIRMATION_BLOC`, `ANNULATION`, `CLOTURE_PARENT`, `EXPORT` |
| `operator` / `site` / `campaign` | String | déclaratifs, fixés à l'enrôlement ou en début de session |
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
fmt;appareil;seq;horodatage_utc;decalage;operateur;site;campagne;version_ref;evenement;code_parent;code_enfant;qte_theorique;qte_relevee;mode_scan;annule_seq;hash_prec;hash
1;TEL-07;128;2026-09-24T12:03:12Z;+02:00;DUPONT J.;MAG-NORD;INV-2026-T4;43;RELEVE;BJ-568A/00090;CAB-220;4;3;;;9f2c…;41ab…
```

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

Si la DSI **interdit les macros**, Power Query assure seul la consolidation, et la
vérification de la chaîne devient impossible sur le poste : il faudra le savoir tôt (Q4,
Q5).

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
| C4 | `android:allowBackup="true"` | `AndroidManifest.xml` | La base peut partir dans la sauvegarde Google du compte personnel de l'utilisateur | `allowBackup="false"` ou base exclue — à confirmer avec la politique MDM (D12) |
| C5 | Suppression de lignes et de dossiers, renommage de dossier | `ScanItemDao`, `FolderDao` | Incompatible avec l'intangibilité **si** le journal couvrait les dossiers de scan | Journal limité à la nouvelle fonction Inventaire (D4) |

Point RGPD : le nom de l'opérateur dans le journal est une **donnée personnelle** →
inscription au registre des traitements de l'entreprise, durée de conservation à fixer
(Q8).

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
| D10 | Nom de l'application (SuivieMAT) et `applicationId` | § 3 de `outils.md` | Changer l'`applicationId` crée **une autre application** : les données de ScanList déjà sur le S25 ne suivent pas | ⬜ à valider |
| D11 | Valorisation (prix) **exclue** du téléphone | — | ✅ recommandé (moindre donnée sensible sur le terrain) | ⬜ à valider |
| D12 | `allowBackup="false"` (C4) | Manifest | ✅ recommandé | ⬜ à valider |

## 12. Questions ouvertes

### À la DSI — bloquantes, à poser en premier

| # | Question | Pourquoi c'est bloquant | Réponse |
|---|---|---|---|
| Q1 | Les téléphones des magasiniers sont-ils **gérés par un MDM** (Intune ou autre) ? Professionnels ou personnels ? | Conditionne tout le reste | ⬜ |
| Q2 | Une **APK interne** peut-elle être installée ? Par quel canal (Google Play géré en application privée, application métier du MDM) ? | Sans canal de distribution, pas de projet | ⬜ |
| Q3 | Quel client mail sur les téléphones ? Une **politique de protection d'application** empêche-t-elle de joindre un fichier provenant d'une application non gérée ? | Peut bloquer l'option 1 | ⬜ |
| Q4 | Les **macros VBA** sont-elles autorisées sur le poste comptable ? Signées, emplacement approuvé ? Règles ASR actives ? | Conditionne la vérification d'intégrité | ⬜ |
| Q5 | **Power Query** est-il utilisable ? | Repli si les macros sont interdites | ⬜ |
| Q6 | Une **boîte fonctionnelle** et une **liste de diffusion** peuvent-elles être créées ? Outlook est-il le client du poste comptable ? | Option 1+ | ⬜ |
| Q7 | Le MDM peut-il **imposer l'heure automatique** sur les téléphones ? | Fiabilité de l'horodatage | ⬜ |
| Q8 | Durée de conservation des pièces, lieu d'archivage, registre RGPD | Conformité | ⬜ |
| Q9 | Un `.csv` joint à un mail interne passe-t-il la passerelle sans être bloqué ni modifié ? | Option 1 | ⬜ |

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

## 13. Risques

| Risque | Effet | Parade |
|---|---|---|
| APK non distribuable (Q2) | Projet bloqué | Poser la question avant tout développement |
| Macros interdites (Q4) | Plus de vérification d'intégrité sur le poste | Power Query pour la consolidation ; vérification à déporter |
| Politique MDM de partage (Q3) | L'export ne peut pas être joint | Tester dès S0 avec l'APK actuelle, qui exporte déjà par mail |
| Divergence Kotlin / VBA sur la forme canonique | Toutes les chaînes « rompues » | Vecteurs de test partagés (S1) |
| Excel reformate les valeurs | Codes faussés, hash invalides | Import en texte, fichiers sources jamais réenregistrés |
| Horloge du téléphone modifiée | Ordre faux entre appareils | L'ordre fait foi par `seq` ; détection d'anomalie ; heure automatique imposée (Q7) |
| Téléphone perdu avant export | Relevés perdus | Exports fréquents ; le trou de séquence au moins se voit |
| Décodage réel jamais validé (phase 10 de ScanList) | Scan du parent non fiable | La phase 10 reste un préalable au pilote |

## 14. Feuille de route

> Numérotation **S0–S9**, distincte des phases 0–10 de ScanList (`workflow.md` § 6), qui
> restent à terminer. Charge : **S** petite, **M** moyenne, **L** importante — relative,
> pas en jours.

| # | Étape | Contenu | Dépend de | Charge | État |
|---|---|---|---|---|---|
| S0 | **Cadrage** | Réponses Q1–Q9 et U1–U8, validation D1–D12 ; test immédiat de l'export mail actuel sur un téléphone géré (Q3) | — | S | ⬜ À faire |
| S1 | **Contrat d'échange** | Spécification figée des deux formats, forme canonique du hash, fichiers spécimens, vecteurs de test | S0 | S | ⬜ À faire |
| S2 | **Prototype poste comptable** | Kit Excel sur fichiers spécimens, **testé sur le vrai poste** : import en texte, vérification de chaîne, continuité, tri, synthèse, génération du référentiel | S1 | M | ⬜ À faire |
| S3 | **Correctifs du socle** | C1 injection CSV, C2 encodage d'import, C3 horodatage UTC, C4 sauvegarde | S0 | S | ⬜ À faire |
| S4 | **Référentiel dans l'application** | Tables, import versionné et contrôlé, « Ouvrir avec », résumé avant remplacement, historique des versions | S1 | M | ⬜ À faire |
| S5 | **Journal en ajout seul** | Table, triggers SQL, séquence, chaîne de hash, annulation, migration 1 → 2 (et retrait d'`intruder_entries`) | S1 | M | ⬜ À faire |
| S6 | **Écran Inventaire** | Scan du parent → liste des enfants → saisie des quantités → clôture | S4, S5 | L | ⬜ À faire |
| S7 | **Export par lots** | Plage de séquences, nommage, évènement `EXPORT`, envoi | S5 | S | ⬜ À faire |
| S8 | **Enrôlement et sécurité** | Identifiant d'appareil, opérateur, site, clé HMAC (si D3 = 2), distribution par le MDM | S0 | M | ⬜ À faire |
| S9 | **Pilote** | 1 ou 2 magasiniers, 1 comptable, un cycle complet montant + descendant | tout + phase 10 | M | ⬜ À faire |

Ce qui est vérifiable où :

- **session cloud** : écriture du code et des tests JVM ; l'APK et les tests passent par
  la CI GitHub (pas de SDK Android local) ;
- **CI** : `testDebugUnitTest`, `assembleDebug`, vecteurs de test côté Kotlin ;
- **poste comptable uniquement** : tout le kit Excel/VBA — il ne peut s'exécuter ni en
  session cloud ni en CI ;
- **Galaxy S25 / téléphone géré** : décodage réel, partage vers le client mail, MDM.

## 15. Journal du plan

| Date | Évolution |
|---|---|
| 2026-09-24 | Création : analyse de faisabilité, recommandation option 1+, modèle de journal, ébauche du contrat d'échange, constats de sécurité C1–C5, questions Q1–Q9 / U1–U8, décisions à valider D1–D12, feuille de route S0–S9. **Aucune décision validée, aucun code modifié.** |
