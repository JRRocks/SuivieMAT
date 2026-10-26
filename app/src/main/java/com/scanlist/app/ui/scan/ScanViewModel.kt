package com.scanlist.app.ui.scan

import androidx.annotation.StringRes
import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanlist.app.R
import com.scanlist.app.data.db.entity.ReferenceEntryEntity
import com.scanlist.app.data.db.entity.RegexRuleEntity
import com.scanlist.app.data.db.entity.ScanItemEntity
import com.scanlist.app.data.prefs.SettingsDataStore
import com.scanlist.app.data.repository.CompareRepository
import com.scanlist.app.data.repository.FolderRepository
import com.scanlist.app.data.repository.RegexSaveResult
import com.scanlist.app.data.repository.RegexRuleRepository
import com.scanlist.app.data.repository.ScanRecordResult
import com.scanlist.app.data.repository.ScanRepository
import com.scanlist.app.domain.compare.CompareEngine
import com.scanlist.app.domain.compare.CompareOutcome
import com.scanlist.app.domain.model.ScanMode
import com.scanlist.app.domain.regex.RegexRuleEngine
import com.scanlist.app.scanner.BarcodeAnalyzer
import com.scanlist.app.scanner.ConsensusCandidate
import com.scanlist.app.scanner.DetectedCode
import com.scanlist.app.scanner.DetectedNfcTag
import com.scanlist.app.scanner.OcrAnalyzer
import com.scanlist.app.scanner.ScanConsensus
import com.scanlist.app.scanner.ScanDebouncer
import com.scanlist.app.scanner.ScanFeedback
import com.scanlist.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/** Derniere issue de capture, affichee en bandeau au-dessus de la liste. */
sealed interface ScanEvent {
    data class Saved(val value: String) : ScanEvent
    data class Duplicate(val value: String) : ScanEvent

    /** Comparaison : la valeur figurait dans la reference et passe au vert. */
    data class Matched(val value: String) : ScanEvent

    /** Comparaison : entree deja passee au vert lors d'un passage precedent. */
    data class AlreadyMatched(val value: String) : ScanEvent

    /** Comparaison : valeur etrangere a la liste, signalee mais pas enregistree. */
    data class OutOfList(val value: String) : ScanEvent

    /**
     * Message fixe. Le ViewModel porte l'identifiant de ressource et non le texte :
     * la traduction reste dans strings.xml, seul endroit ou l'interface est redigee.
     */
    data class Notice(@StringRes val messageRes: Int) : ScanEvent

    /** Message dynamique : erreur camera, detail d'une formule invalide. */
    data class Error(val message: String) : ScanEvent
}

/**
 * Orchestration du scan a la chaine, pour les quatre modes.
 *
 * Le chemin critique est court par construction : l'analyseur (ou le callback NFC)
 * remonte les valeurs lues, le debouncer ecarte les repetitions, l'ecriture part sur
 * le scope du ViewModel et la source de capture n'est jamais suspendue.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScanViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scanRepository: ScanRepository,
    private val folderRepository: FolderRepository,
    private val regexRuleRepository: RegexRuleRepository,
    private val compareRepository: CompareRepository,
    private val settings: SettingsDataStore,
    private val feedback: ScanFeedback,
) : ViewModel() {

    val mode: ScanMode = ScanMode.fromNameOrNull(savedStateHandle[Routes.ARG_MODE])
        ?: ScanMode.QR_CODE

    /** Session de comparaison ciblee, ou [Routes.NO_ID] pour un scan de dossier. */
    val sessionId: Long = savedStateHandle[Routes.ARG_SESSION_ID] ?: Routes.NO_ID

    /**
     * Deux contextes pour un seul ecran camera.
     *
     * Le pipeline de capture est rigoureusement le meme — memes analyseurs, meme
     * anti-rafale, meme editeur de formule OCR — seule la destination change : une
     * ligne de plus dans un dossier, ou un verdict face a une liste de reference.
     */
    val isCompare: Boolean = sessionId > 0

    private val initialFolderId: Long = savedStateHandle[Routes.ARG_FOLDER_ID] ?: Routes.NO_ID

    /**
     * Dossier alimente. Vaut [Routes.NO_ID] tant que rien n'a ete capture dans un
     * nouveau scan : creer le dossier au premier code evite de laisser derriere soi
     * une liste vide chaque fois qu'on ouvre l'ecran par erreur.
     */
    private val folderIdState = MutableStateFlow(initialFolderId)
    val folderId: StateFlow<Long> = folderIdState.asStateFlow()

    private val folderNameState = MutableStateFlow<String?>(null)
    val folderName: StateFlow<String?> = folderNameState.asStateFlow()

    private val lastEventState = MutableStateFlow<ScanEvent?>(null)
    val lastEvent: StateFlow<ScanEvent?> = lastEventState.asStateFlow()

    private val torchState = MutableStateFlow(false)
    val torch: StateFlow<Boolean> = torchState.asStateFlow()

    val items: StateFlow<List<ScanItemEntity>> = folderIdState
        .flatMapLatest { id ->
            if (id <= 0) flowOf(emptyList()) else scanRepository.observeItems(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------------------------------------------------------- Comparaison

    private val sessionNameState = MutableStateFlow<String?>(null)
    val sessionName: StateFlow<String?> = sessionNameState.asStateFlow()

    /**
     * Liste de reference et son etat colore, lus depuis la base et non depuis le
     * moteur en memoire.
     *
     * Une seule source de verite pour l'affichage : ce que l'ecran montre est ce qui
     * est reellement enregistre. Un verdict rendu mais non persiste ne doit pas
     * apparaitre en vert.
     */
    val references: StateFlow<List<ReferenceEntryEntity>> =
        (if (isCompare) compareRepository.observeReferences(sessionId) else flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Moteur de la session, charge une fois puis conserve.
     *
     * Il tranche en memoire, sans requete : le scan a la chaine ne peut pas se
     * permettre un aller-retour SQLite entre deux codes.
     */
    private var engine: CompareEngine? = null

    /** Serialise l'acces au moteur, qui n'est pas concu pour l'acces concurrent. */
    private val compareMutex = Mutex()

    // ------------------------------------------------------------------ OCR

    private val patternState = MutableStateFlow("")
    val pattern: StateFlow<String> = patternState.asStateFlow()

    private val groupIndexState = MutableStateFlow(0)
    val groupIndex: StateFlow<Int> = groupIndexState.asStateFlow()

    private val groupCountState = MutableStateFlow(0)
    val groupCount: StateFlow<Int> = groupCountState.asStateFlow()

    private val validationState =
        MutableStateFlow<RegexRuleEngine.Validation>(RegexRuleEngine.Validation.Invalid(""))
    val validation: StateFlow<RegexRuleEngine.Validation> = validationState.asStateFlow()

    /**
     * En OCR, la capture est armee explicitement.
     *
     * Sans cela, une formule en cours de frappe ("\\d" par exemple) remplirait la liste
     * de dizaines de valeurs parasites avant meme d'etre terminee. Les modes QR et
     * code-barres n'ont pas ce risque : ils demarrent armes.
     */
    private val capturingState = MutableStateFlow(mode != ScanMode.OCR)
    val capturing: StateFlow<Boolean> = capturingState.asStateFlow()

    private val activeRuleIdState = MutableStateFlow<Long?>(null)
    val activeRuleId: StateFlow<Long?> = activeRuleIdState.asStateFlow()

    val savedRules: StateFlow<List<RegexRuleEntity>> = regexRuleRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Formule compilee, mise a jour a chaque frappe.
     *
     * Volatile : ecrite depuis le thread UI, lue depuis le thread d'analyse. La
     * recompiler a chaque frame couterait plus cher que la reconnaissance elle-meme.
     */
    @Volatile
    private var compiledRegex: Regex? = null

    private val debouncer = ScanDebouncer()

    /**
     * Validation par consensus, avant le verrou anti-rebond du debouncer.
     *
     * Seuil plus haut en OCR : le bruit par frame y est plus eleve qu'en decodage
     * optique pur, et le cout d'une lecture supplementaire reste faible. La garde
     * d'ambiguite est activee partout sauf en mode Code 2D, dont les quatre formats
     * (QR Code, PDF417, Data Matrix, Aztec) portent tous une correction d'erreur
     * Reed-Solomon qui rend une mauvaise lecture pratiquement impossible — l'activer
     * quand meme sur les codes-barres a checksum (EAN, UPC, Code 128) ne coute rien,
     * elle ne s'y declenche simplement jamais.
     *
     * Hors de portee de cette instance : le mode NFC (pas de bruit optique, le
     * debouncer seul suffit) et la fonction Comparer (portee validee par
     * l'utilisateur pour la premiere version — la fausse alarme "hors liste" sur une
     * frame isolee y subsiste).
     */
    private val consensus = ScanConsensus(
        threshold = if (mode == ScanMode.OCR) CONSENSUS_THRESHOLD_OCR else CONSENSUS_THRESHOLD_CODE,
        ambiguityGuard = mode != ScanMode.QR_CODE,
    )

    private val consensusProgressState = MutableStateFlow(0f)

    /** Avancement du consensus en cours, pour le remplissage du reticule. */
    val consensusProgress: StateFlow<Float> = consensusProgressState.asStateFlow()

    /** Serialise la creation paresseuse du dossier entre deux frames concurrentes. */
    private val folderMutex = Mutex()

    /**
     * Analyseur unique pour toute la duree de l'ecran : recreer le client ML Kit a
     * chaque recomposition relancerait le chargement du modele.
     *
     * Null en NFC : ce mode ne lit pas le flux camera, la lecture est evenementielle
     * (voir `NfcScanSurface`, pilote par `NfcAdapter.enableReaderMode`).
     */
    val analyzer: ImageAnalysis.Analyzer? = when (mode) {
        ScanMode.OCR -> OcrAnalyzer(::onTextRecognized)
        ScanMode.QR_CODE, ScanMode.BARCODE -> BarcodeAnalyzer(mode, ::onCodesDetected)
        ScanMode.NFC -> null
    }

    init {
        viewModelScope.launch {
            settings.setLastScanMode(mode)
            if (isCompare) {
                sessionNameState.value = compareRepository.getSession(sessionId)?.name
                // Charge des l'ouverture : le premier code ne doit pas attendre la
                // lecture de la liste de reference.
                ensureEngine()
            }
            if (initialFolderId > 0) {
                folderNameState.value = folderRepository.getById(initialFolderId)?.name
                folderRepository.setDefaultScanMode(initialFolderId, mode)
            }
            // La derniere formule utilisee est rechargee : c'est tout l'interet de la
            // bibliotheque, retrouver son filtre sans le retaper a chaque inventaire.
            if (mode == ScanMode.OCR) {
                val lastId = settings.lastRegexRuleId.first()
                lastId?.let { id -> regexRuleRepository.getById(id)?.let(::applyRule) }
            }
        }
    }

    // ------------------------------------------------- Reception des analyses

    /**
     * Appele depuis le thread d'analyse CameraX.
     *
     * Consensus et filtrage anti-rafale sont faits ici, en synchrone, pour ne pas
     * lancer une coroutine par frame. L'ecriture en base, elle, part sur le scope du
     * ViewModel.
     *
     * Le consensus ne s'applique qu'au scan de dossier : en comparaison, le chemin
     * reste inchange (verrou anti-rebond seul), portee validee par l'utilisateur.
     */
    private fun onCodesDetected(codes: List<DetectedCode>) {
        if (isCompare) {
            val fresh = codes.filter { debouncer.accept(it.value) }
            if (fresh.isEmpty()) return
            viewModelScope.launch { compare(fresh.map { it.value }) }
            return
        }

        val conforming = codes.filter { RegexRuleEngine.conforms(it.value, compiledRegex) }
        val confirmed = consensus.submit(conforming.map { ConsensusCandidate(it.value, it.symbology) })
        consensusProgressState.value = consensus.peakProgress()
        val fresh = confirmed.filter { debouncer.accept(it.value) }
        if (fresh.isEmpty()) return

        viewModelScope.launch {
            val target = ensureFolder() ?: return@launch
            // Plusieurs codes dans la meme image : une ligne chacun.
            val results = scanRepository.recordAll(
                folderId = target,
                rawValues = fresh.map { it.value },
                mode = mode,
                symbologies = fresh.map { it.symbology },
                regexRuleId = activeRuleIdState.value,
            )
            publish(results)
        }
    }

    /**
     * Appele depuis le callback systeme `NfcAdapter.ReaderCallback` (thread de service
     * NFC, pas le thread principal).
     *
     * Un seul tag par detection, a la difference des codes optiques qui peuvent en
     * cumuler plusieurs dans une image : pas de liste de valeurs ici.
     */
    fun onNfcTagRead(tag: DetectedNfcTag) {
        if (!debouncer.accept(tag.value)) return

        viewModelScope.launch {
            if (isCompare) {
                compare(listOf(tag.value))
                return@launch
            }
            val target = ensureFolder() ?: return@launch
            val results = scanRepository.recordAll(
                folderId = target,
                rawValues = listOf(tag.value),
                mode = mode,
                symbologies = listOf(tag.symbology),
            )
            publish(results)
        }
    }

    /**
     * Appele depuis le thread d'analyse CameraX, a chaque texte reconnu.
     *
     * Capture desarmee, rien n'est extrait : sans apercu en direct (decision 26), une
     * extraction a chaque image ne servirait plus a rien sur ce thread critique.
     */
    private fun onTextRecognized(rawText: String) {
        if (!capturingState.value) return
        val regex = compiledRegex ?: return
        val matches = RegexRuleEngine.extract(rawText, regex, groupIndexState.value)
        if (matches.isEmpty()) return

        if (isCompare) {
            val fresh = matches.filter { debouncer.accept(it) }
            if (fresh.isEmpty()) return
            viewModelScope.launch { compare(fresh) }
            return
        }

        val confirmed = consensus.submit(matches.map { ConsensusCandidate(it) })
        consensusProgressState.value = consensus.peakProgress()
        val fresh = confirmed.filter { debouncer.accept(it.value) }
        if (fresh.isEmpty()) return

        viewModelScope.launch {
            val target = ensureFolder() ?: return@launch
            // Chaque correspondance de la formule produit sa propre ligne.
            val results = scanRepository.recordAll(
                folderId = target,
                rawValues = fresh.map { it.value },
                mode = mode,
                regexRuleId = activeRuleIdState.value,
            )
            publish(results)
        }
    }

    /**
     * Confronte les valeurs lues a la liste de reference.
     *
     * Le verdict est rendu en memoire, l'ecriture suit. Plusieurs codes dans la meme
     * image donnent plusieurs verdicts : la regle "une ligne par code" vaut aussi ici.
     */
    private suspend fun compare(values: List<String>) {
        val current = ensureEngine() ?: return
        val outcomes = compareMutex.withLock { values.map(current::submit) }
        outcomes.forEach { outcome ->
            compareRepository.persistOutcome(sessionId, outcome)
        }
        publishCompare(outcomes)
    }

    /**
     * Renvoie le moteur, en le chargeant au premier appel.
     * Le mutex evite qu'une rafale de frames en construise deux.
     */
    private suspend fun ensureEngine(): CompareEngine? = compareMutex.withLock {
        engine ?: compareRepository.loadEngine(sessionId)?.also { engine = it }
    }

    /**
     * Signature de retour d'une lecture en comparaison.
     *
     * Trois issues possibles, par ordre de priorite dans une meme image : une entree
     * validee prime sur tout le reste, c'est la confirmation attendue ; une entree
     * deja validee vient ensuite ; une valeur hors liste ferme la marche.
     *
     * Le hors-liste n'est signale que par ce bandeau et son bip : rien n'est ecrit et
     * aucun compteur ne bouge. L'operateur doit savoir qu'il vient de viser autre
     * chose que ce qu'il controle, sans que cela laisse la moindre trace.
     */
    private fun publishCompare(outcomes: List<CompareOutcome>) {
        val matched = outcomes.filterIsInstance<CompareOutcome.Matched>()
        val already = outcomes.filterIsInstance<CompareOutcome.AlreadyMatched>()
        val outOfList = outcomes.filterIsInstance<CompareOutcome.OutOfList>()

        when {
            matched.isNotEmpty() -> {
                feedback.success()
                lastEventState.value = ScanEvent.Matched(
                    if (matched.size == 1) matched.first().referenceValue else "${matched.size} codes",
                )
            }

            already.isNotEmpty() -> {
                feedback.duplicate()
                lastEventState.value = ScanEvent.AlreadyMatched(already.first().referenceValue)
            }

            outOfList.isNotEmpty() -> {
                feedback.outOfList()
                lastEventState.value = ScanEvent.OutOfList(outOfList.first().value)
            }

            // Que des valeurs illisibles : ne rien faire.
            else -> Unit
        }
    }

    private fun publish(results: List<ScanRecordResult>) {
        // Une seule signature sonore par image, meme si elle contenait plusieurs codes :
        // enchainer cinq bips serait illisible a l'oreille.
        val saved = results.filterIsInstance<ScanRecordResult.Saved>()
        val duplicates = results.filterIsInstance<ScanRecordResult.Duplicate>()

        when {
            saved.isNotEmpty() -> {
                feedback.success()
                lastEventState.value = ScanEvent.Saved(
                    if (saved.size == 1) saved.first().value else "${saved.size} codes",
                )
            }

            duplicates.isNotEmpty() -> {
                feedback.duplicate()
                lastEventState.value = ScanEvent.Duplicate(duplicates.first().value)
            }

            // Que des valeurs vides : ne rien faire, conformement a la regle d'erreur.
            else -> Unit
        }
    }

    /**
     * Renvoie le dossier a alimenter, en le creant au premier code si necessaire.
     * Le mutex garantit qu'une rafale de frames ne cree pas deux dossiers.
     */
    private suspend fun ensureFolder(): Long? = folderMutex.withLock {
        val current = folderIdState.value
        if (current > 0) return@withLock current

        val created = folderRepository.create(
            name = folderRepository.defaultName(mode),
            defaultScanMode = mode,
        )
        folderIdState.value = created
        folderNameState.value = folderRepository.getById(created)?.name
        created
    }

    // ------------------------------------------------------ Edition de formule

    /** Saisie a la volee : la formule est recompilee et validee a chaque frappe. */
    fun setPattern(value: String) {
        patternState.value = value
        validationState.value = RegexRuleEngine.validate(value)
        compiledRegex = RegexRuleEngine.compileOrNull(value)
        groupCountState.value = RegexRuleEngine.capturingGroupCount(value)
        if (groupIndexState.value > groupCountState.value) groupIndexState.value = 0
        // Une formule modifiee n'est plus celle de la bibliotheque.
        activeRuleIdState.value = null
        // Desarmer : on ne veut pas enregistrer avec une formule en cours de frappe.
        if (mode == ScanMode.OCR) capturingState.value = false
    }

    fun setGroupIndex(index: Int) {
        groupIndexState.value = index
    }

    fun applyRule(rule: RegexRuleEntity) {
        patternState.value = rule.pattern
        validationState.value = RegexRuleEngine.validate(rule.pattern)
        compiledRegex = RegexRuleEngine.compileOrNull(rule.pattern)
        groupCountState.value = RegexRuleEngine.capturingGroupCount(rule.pattern)
        groupIndexState.value = rule.groupIndex
        activeRuleIdState.value = rule.id
        capturingState.value = false
        viewModelScope.launch {
            regexRuleRepository.markUsed(rule.id)
            settings.setLastRegexRuleId(rule.id)
        }
    }

    fun saveCurrentRule(name: String) = viewModelScope.launch {
        when (val result = regexRuleRepository.save(name, patternState.value, groupIndexState.value)) {
            is RegexSaveResult.Saved -> {
                activeRuleIdState.value = result.id
                settings.setLastRegexRuleId(result.id)
                lastEventState.value = ScanEvent.Notice(R.string.ocr_saved_notice)
            }

            is RegexSaveResult.Invalid ->
                lastEventState.value = ScanEvent.Error(result.message)

            RegexSaveResult.NameTaken ->
                lastEventState.value = ScanEvent.Notice(R.string.ocr_name_taken)
        }
    }

    fun deleteRule(rule: RegexRuleEntity) = viewModelScope.launch {
        regexRuleRepository.delete(rule)
        if (activeRuleIdState.value == rule.id) activeRuleIdState.value = null
    }

    /**
     * Arme ou desarme la capture. Impossible d'armer sur une formule invalide : le
     * scan tournerait sans jamais rien retenir.
     */
    fun toggleCapture() {
        if (mode != ScanMode.OCR) return
        if (validationState.value !is RegexRuleEngine.Validation.Valid) {
            lastEventState.value = ScanEvent.Notice(R.string.ocr_need_valid)
            return
        }
        debouncer.reset()
        consensus.reset()
        consensusProgressState.value = 0f
        capturingState.value = !capturingState.value
    }

    // ----------------------------------------------------------------- Divers

    fun toggleTorch() {
        torchState.value = !torchState.value
    }

    fun deleteItem(item: ScanItemEntity) = viewModelScope.launch {
        scanRepository.delete(item)
        // Le code redevient scannable immediatement apres une suppression volontaire.
        debouncer.reset()
        consensus.reset()
        consensusProgressState.value = 0f
    }

    fun onCameraError(message: String) {
        lastEventState.value = ScanEvent.Error(message)
    }

    fun consumeEvent() {
        lastEventState.value = null
    }

    override fun onCleared() {
        when (val current = analyzer) {
            is BarcodeAnalyzer -> current.close()
            is OcrAnalyzer -> current.close()
        }
        feedback.release()
        super.onCleared()
    }

    private companion object {
        /** ~100 ms de tenue camera a la cadence capteur : les erreurs transitoires n'y survivent pas. */
        const val CONSENSUS_THRESHOLD_CODE = 3

        /** Plus haut qu'en optique pure : le bruit par frame de l'OCR est plus eleve. */
        const val CONSENSUS_THRESHOLD_OCR = 5
    }
}
