package com.retrobit.office

import android.app.Application
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier

/**
 * RetroBit Office Application
 * Optimized for Huawei MatePad Mini (8.8" OLED, 165Hz, M-Pencil Pro)
 */
class RetroBitApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        preloadHandwritingModels()
    }

    private fun preloadHandwritingModels() {
        // Pre-download ML Kit handwriting models for Indonesian + English
        val languages = listOf("id", "en", "en-US")
        languages.forEach { lang ->
            try {
                val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(lang)
                if (identifier != null) {
                    val model = DigitalInkRecognitionModel.builder(identifier).build()
                    val modelManager = RemoteModelManager.getInstance()
                    // Check if downloaded, if not download in background
                    modelManager.isModelDownloaded(model)
                        .addOnSuccessListener { downloaded ->
                            if (!downloaded) {
                                modelManager.download(model, DownloadConditions.Builder().build())
                            }
                        }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        lateinit var instance: RetroBitApplication
            private set
    }
}
