package com.scanlist.app.ocrbench

/**
 * Pont vers la bibliotheque Rust du banc (ocrbench/rust).
 *
 * Les signatures doivent rester alignees sur `jni_bridge.rs` : JNI resout ces fonctions
 * par leur nom, une divergence ne se voit qu'a l'execution.
 */
object RustBridge {

    /** Faux si la bibliotheque n'a pas ete compilee pour cet appareil. */
    val available: Boolean = runCatching { System.loadLibrary("ocrbench") }.isSuccess

    /** Renvoie 0 si la formule n'est pas acceptee par le moteur regex de Rust. */
    @JvmStatic
    external fun pipelineCreate(pattern: String, groupIndex: Int, threshold: Int, ambiguityGuard: Boolean): Long

    /**
     * Traite une image : renvoie les valeurs a enregistrer, ou null s'il n'y en a pas.
     * [progressOut] recoit l'avancement du consensus quand la formule a trouve quelque
     * chose, comme `consensusProgressState` dans l'application.
     */
    @JvmStatic
    external fun pipelineProcess(handle: Long, text: String, nowMs: Long, progressOut: FloatArray): Array<String>?

    @JvmStatic
    external fun pipelineReset(handle: Long)

    @JvmStatic
    external fun pipelineDestroy(handle: Long)

    /**
     * Rejoue toute la suite d'images sans repasser par Java entre deux images.
     * Renvoie [duree en nanosecondes, nombre de valeurs validees].
     */
    @JvmStatic
    external fun pipelineLoop(
        handles: LongArray,
        framePipeline: IntArray,
        frameTexts: Array<String>,
        frameTimes: LongArray,
        repetitions: Int,
        periodMs: Long,
    ): LongArray?

    /** Renvoie 0 en cas d'echec ; la raison est dans [lastError]. */
    @JvmStatic
    external fun engineCreate(detectionModelPath: String, recognitionModelPath: String): Long

    @JvmStatic
    external fun lastError(): String

    /** [luma] : un octet par pixel, ligne par ligne. Null en cas d'echec. */
    @JvmStatic
    external fun engineRead(handle: Long, luma: ByteArray, width: Int, height: Int): String?

    @JvmStatic
    external fun engineDestroy(handle: Long)
}
