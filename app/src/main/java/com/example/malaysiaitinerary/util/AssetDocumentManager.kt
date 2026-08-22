package com.example.malaysiaitinerary.util

import android.content.Context
import com.example.malaysiaitinerary.data.local.entity.Document
import com.example.malaysiaitinerary.data.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object AssetDocumentManager {

    private const val ASSET_DOCUMENTS_DIR = "documents"

    suspend fun importAssetsIfNeeded(context: Context, repository: DocumentRepository) {
        withContext(Dispatchers.IO) {
            val existingDocs = repository.allDocuments.first()
            if (existingDocs.isNotEmpty()) return@withContext

            val assets = context.assets.list(ASSET_DOCUMENTS_DIR) ?: return@withContext
            
            assets.forEach { assetName ->
                if (assetName.endsWith(".pdf", ignoreCase = true) || 
                    assetName.endsWith(".jpg", ignoreCase = true) || 
                    assetName.endsWith(".png", ignoreCase = true)) {
                    
                    try {
                        val file = copyAssetToInternalStorage(context, assetName)
                        val document = parseDocumentFromFileName(assetName, file.absolutePath)
                        repository.insertDocument(document)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun copyAssetToInternalStorage(context: Context, assetName: String): File {
        val assetPath = "$ASSET_DOCUMENTS_DIR/$assetName"
        val outFile = File(context.filesDir, assetName)
        
        context.assets.open(assetPath).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        return outFile
    }

    private fun parseDocumentFromFileName(fileName: String, filePath: String): Document {
        // Expected format: "Mar 22, 2026 - Flight (BLR-KUL).pdf"
        // Generic fallback:
        var type = "TICKET"
        var date: String? = null
        var location: String? = null

        try {
            val cleanName = fileName.substringBeforeLast(".")
            if (cleanName.contains(" - ")) {
                val parts = cleanName.split(" - ")
                val datePart = parts[0] // "Mar 22, 2026"
                val rest = parts[1] // "Flight (BLR-KUL)"

                // Parse date
                val inputFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)
                val parsedDate = LocalDate.parse(datePart, inputFormatter)
                date = parsedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

                if (rest.contains(" (") && rest.endsWith(")")) {
                    type = rest.substringBefore(" (").uppercase()
                    location = rest.substringAfter(" (").substringBefore(")")
                } else {
                    type = rest.uppercase()
                }
            }
        } catch (e: Exception) {
            // Fallback to defaults
        }

        return Document(
            fileName = fileName,
            filePath = filePath,
            type = type,
            date = date,
            location = location
        )
    }
}
