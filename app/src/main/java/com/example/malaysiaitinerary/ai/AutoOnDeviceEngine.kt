package com.example.malaysiaitinerary.ai

import android.content.Context
import com.example.malaysiaitinerary.data.repository.GemmaModelChoice
import java.io.File

/**
 * Smart Auto Engine:
 * 1. Primary: On-Device Gemma 4 Local Model (LiteRT-LM / MediaPipe).
 * 2. Backup: Gemini Cloud API.
 */
class AutoOnDeviceEngine(
    private val context: Context,
    private val modelChoice: GemmaModelChoice,
    private val customModelPath: String?,
    private val apiKey: String
) : AiEngine {

    override val engineName: String = "Gemma 4 On-Device (Cloud Backup)"

    private val gemmaEngine by lazy { MediaPipeGemmaEngine(context, modelChoice, customModelPath) }
    private val cloudEngine by lazy { GeminiCloudEngine(apiKey) }

    override suspend fun parseDocument(file: File, mimeType: String): Result<ExtractedDocumentResult> {
        val gemmaRes = gemmaEngine.parseDocument(file, mimeType)
        if (gemmaRes.isSuccess) return gemmaRes

        if (apiKey.isNotBlank()) {
            val cloudRes = cloudEngine.parseDocument(file, mimeType)
            if (cloudRes.isSuccess) return cloudRes
        }
        return Result.failure(
            Exception(
                gemmaEngine.initErrorReason
                    ?: "Gemma 4 local model setup is pending. Please download or import a model file (.litertlm / .task) or enter a Gemini API key in Settings."
            )
        )
    }

    override suspend fun chat(
        userPrompt: String,
        chatHistory: List<Pair<String, String>>,
        tripSummaryContext: String,
        imageBitmap: android.graphics.Bitmap?,
        audioBytes: ByteArray?
    ): Result<AiResponse> {
        // 1. Primary: Try On-Device Gemma 4 Local Engine
        val gemmaRes = gemmaEngine.chat(userPrompt, chatHistory, tripSummaryContext, imageBitmap, audioBytes)
        if (gemmaRes.isSuccess) return gemmaRes

        // 2. Backup: Try Gemini Cloud Engine if API key exists
        if (apiKey.isNotBlank()) {
            val cloudRes = cloudEngine.chat(userPrompt, chatHistory, tripSummaryContext, imageBitmap, audioBytes)
            if (cloudRes.isSuccess) return cloudRes
        }

        // Return user-friendly setup pending message
        val pendingMsg = gemmaEngine.initErrorReason
            ?: "Gemma 4 local model setup is pending. Please download or import a Gemma 4 model file (.litertlm / .task) or enter a Gemini API key in Settings."

        return Result.failure(Exception(pendingMsg))
    }

    override suspend fun generateItinerary(
        destination: String,
        daysCount: Int,
        preferences: String,
        enableSearchGrounding: Boolean
    ): Result<StructuredItineraryPlan> {
        // 1. Primary: Try On-Device Gemma 4 Local Engine
        val gemmaRes = gemmaEngine.generateItinerary(destination, daysCount, preferences, enableSearchGrounding)
        if (gemmaRes.isSuccess) return gemmaRes

        // 2. Backup: Try Gemini Cloud Engine if API key exists
        if (apiKey.isNotBlank()) {
            val cloudRes = cloudEngine.generateItinerary(destination, daysCount, preferences, enableSearchGrounding)
            if (cloudRes.isSuccess) return cloudRes
        }

        val pendingMsg = gemmaEngine.initErrorReason
            ?: "Gemma 4 local model setup is pending. Please download or import a model file (.litertlm / .task) or enter a Gemini API key in Settings."

        return Result.failure(Exception(pendingMsg))
    }
}
