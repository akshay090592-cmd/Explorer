package com.example.malaysiaitinerary.util

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DiscoveredModel(
    val file: File,
    val name: String = file.name,
    val sourceApp: String = "Local Storage",
    val sizeMb: Long = file.length() / (1024 * 1024)
)

object ModelScanner {

    suspend fun scanForGemmaModels(context: Context): List<DiscoveredModel> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiscoveredModel>()

        val candidateDirectories = listOf(
            context.filesDir to "App Internal",
            context.getExternalFilesDir(null) to "App External",
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) to "Downloads",
            File("/sdcard/Download") to "Downloads",
            File("/sdcard/Download/Gemma") to "Downloads/Gemma",
            File("/sdcard/Download/GoogleAIEdge") to "AI Edge Gallery",
            File("/sdcard/Android/media/com.google.ai.edge.gallery") to "AI Edge Gallery",
            File("/sdcard/Android/media/com.google.ai.edge.gallery/files") to "AI Edge Gallery",
            File("/sdcard/Models") to "Models Folder"
        )

        val targetExtensions = listOf("litertlm", "task", "bin", "gguf")

        for ((dir, sourceName) in candidateDirectories) {
            if (dir != null && dir.exists() && dir.isDirectory) {
                try {
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val ext = file.extension.lowercase()
                            val nameLower = file.name.lowercase()
                            if (targetExtensions.contains(ext) || nameLower.endsWith(".litertlm") || nameLower.endsWith(".task") || nameLower.endsWith(".bin")) {
                                val sizeMb = file.length() / (1024 * 1024)
                                if (sizeMb > 5 || nameLower.contains("gemma") || nameLower.contains("llm") || nameLower.contains("model")) {
                                    results.add(
                                        DiscoveredModel(
                                            file = file,
                                            name = file.name,
                                            sourceApp = sourceName,
                                            sizeMb = sizeMb
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        results.distinctBy { it.file.absolutePath }
    }
}
