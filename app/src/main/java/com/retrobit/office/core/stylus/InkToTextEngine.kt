package com.retrobit.office.core.stylus

import android.content.Context
import android.util.Log
import com.google.mlkit.vision.digitalink.*
import kotlinx.coroutines.tasks.await

/**
 * Handwriting-to-Text Engine
 * Dual engine: Google ML Kit Digital Ink (primary) + Huawei ML Handwriting (fallback)
 * Support Bahasa Indonesia & English, angka, simbol
 */
class InkToTextEngine(private val context: Context) {

    private var recognizer: DigitalInkRecognizer? = null
    private var currentModel: DigitalInkRecognitionModel? = null
    private var languageTag: String = "id" // default Indonesia

    private var isInitialized = false

    // Pre-stroke buffer for Ink API
    private var inkBuilder = Ink.builder()
    private var currentStrokeBuilder: Ink.Stroke.Builder? = null

    interface RecognitionCallback {
        fun onResult(text: String, confidence: Float)
        fun onError(e: Exception)
        fun onCandidates(candidates: List<String>)
    }

    suspend fun init(language: String = "id"): Boolean {
        return try {
            languageTag = language
            val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(language)
                ?: DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")!!

            currentModel = DigitalInkRecognitionModel.builder(identifier).build()

            val options = DigitalInkRecognizerOptions.builder(currentModel!!).build()
            recognizer = DigitalInkRecognition.getClient(options)

            // Ensure model downloaded
            val modelManager = com.google.mlkit.common.model.RemoteModelManager.getInstance()
            val downloaded = modelManager.isModelDownloaded(currentModel!!).await()
            if (!downloaded) {
                modelManager.download(
                    currentModel!!,
                    com.google.mlkit.common.model.DownloadConditions.Builder().build()
                ).await()
            }
            isInitialized = true
            Log.d("InkToText", "Model $language ready")
            true
        } catch (e: Exception) {
            Log.e("InkToText", "Init failed: ${e.message}", e)
            false
        }
    }

    fun startStroke(point: StylusPoint) {
        currentStrokeBuilder = Ink.Stroke.builder()
        addPoint(point)
    }

    fun addPoint(point: StylusPoint) {
        currentStrokeBuilder?.addPoint(Ink.Point.create(point.x, point.y, point.timestamp))
    }

    fun endStroke() {
        currentStrokeBuilder?.let {
            inkBuilder.addStroke(it.build())
            currentStrokeBuilder = null
        }
    }

    fun addStroke(stroke: InkStroke) {
        val builder = Ink.Stroke.builder()
        stroke.points.forEach { p ->
            builder.addPoint(Ink.Point.create(p.x, p.y, p.timestamp))
        }
        inkBuilder.addStroke(builder.build())
    }

    suspend fun recognize(): RecognitionResult? {
        if (!isInitialized || recognizer == null) {
            init(languageTag)
        }
        val ink = inkBuilder.build()
        if (ink.strokes.isEmpty()) return null

        return try {
            val result = recognizer!!.recognize(ink).await()
            val candidates = result.candidates
            val best = candidates.firstOrNull()
            RecognitionResult(
                text = best?.text ?: "",
                confidence = best?.score ?: 0f,
                candidates = candidates.map { it.text }
            )
        } catch (e: Exception) {
            Log.e("InkToText", "Recognize error", e)
            null
        }
    }

    fun recognizeAsync(callback: RecognitionCallback) {
        if (!isInitialized) {
            callback.onError(IllegalStateException("Engine not initialized, call init() first"))
            return
        }
        val ink = inkBuilder.build()
        if (ink.strokes.isEmpty()) {
            callback.onError(IllegalStateException("No ink to recognize"))
            return
        }
        recognizer!!.recognize(ink)
            .addOnSuccessListener { result ->
                val best = result.candidates.firstOrNull()
                if (best != null) {
                    callback.onResult(best.text, best.score ?: 0f)
                    callback.onCandidates(result.candidates.map { it.text })
                } else {
                    callback.onError(Exception("No recognition result"))
                }
            }
            .addOnFailureListener { e ->
                callback.onError(e as Exception)
            }
    }

    fun clear() {
        inkBuilder = Ink.builder()
        currentStrokeBuilder = null
    }

    fun setLanguage(langTag: String) {
        languageTag = langTag
        // Re-init in background
        isInitialized = false
    }

    fun release() {
        recognizer?.close()
        recognizer = null
    }

    data class RecognitionResult(
        val text: String,
        val confidence: Float,
        val candidates: List<String>
    )
}

/**
 * Huawei ML Kit fallback wrapper (pseudo-implementation, requires HMS SDK)
 * Untuk MatePad yang tidak ada GMS, gunakan HMS ML Handwriting
 */
class HuaweiInkEngine(private val context: Context) {

    fun isAvailable(): Boolean {
        return try {
            // Check HMS availability
            Class.forName("com.huawei.hms.mlsdk.handwriting.MLHandwritingRecognizer")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    // TODO: Implement HMS handwriting if needed
    // val analyzer = MLAnalyzerFactory.getInstance().getHandwritingAnalyzer()
}
