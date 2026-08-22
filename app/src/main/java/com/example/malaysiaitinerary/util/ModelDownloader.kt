package com.example.malaysiaitinerary.util

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

data class ModelDownloadProgress(
    val downloadId: Long = -1L,
    val isDownloading: Boolean = false,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val progressPercent: Float = 0f,
    val statusText: String = "",
    val isCompleted: Boolean = false,
    val isSuccess: Boolean = false,
    val downloadedFile: File? = null,
    val errorMessage: String? = null
)

object ModelDownloader {

    // Official Google AI Edge & Kaggle MediaPipe Gemma Task Bundle links
    const val KAGGLE_GEMMA_TFLITE_URL = "https://www.kaggle.com/models/google/gemma/tfLite"
    const val OFFICIAL_SAFETENSORS_DOC_URL = "https://developers.google.com/edge/litert-lm/models/gemma-4#deploy_from_safetensors"
    const val OFFICIAL_CLI_USAGE_DOC_URL = "https://developers.google.com/edge/litert-lm/cli/usage#run_the_gemma4_e2b_model"
    const val HF_LITERT_COMMUNITY_REPO_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"

    // Direct link for official Gemma 4 LiteRT model file (Runs with GPU acceleration on supported hardware & CPU fallback)
    const val DIRECT_GEMMA_4_E2B_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true"
    const val DIRECT_GEMMA_4_E2B_CPU_URL = DIRECT_GEMMA_4_E2B_URL
    const val DIRECT_GEMMA_4_E2B_GPU_URL = DIRECT_GEMMA_4_E2B_URL
    const val DIRECT_GEMMA_4_E2B_TASK_URL = DIRECT_GEMMA_4_E2B_URL
    const val DIRECT_GEMMA_BIN_URL = DIRECT_GEMMA_4_E2B_URL
    const val GOOGLE_AI_EDGE_GALLERY_URL = "https://github.com/google-ai-edge/ai-edge-gallery"

    fun startDirectDownload(
        context: Context,
        url: String = DIRECT_GEMMA_4_E2B_URL,
        fileName: String = "gemma-4-E2B-it.litertlm"
    ): Long {
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Gemma 4 LiteRT Model")
            setDescription("Downloading Gemma 4 on-device model ($fileName)...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Gemma/$fileName")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return downloadManager.enqueue(request)
    }

    fun cancelDownload(context: Context, downloadId: Long) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.remove(downloadId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun observeDownloadProgress(
        context: Context,
        downloadId: Long,
        fileName: String = "gemma-4-E2B-it.litertlm"
    ): Flow<ModelDownloadProgress> = flow {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var downloading = true

        while (downloading) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor: Cursor? = try { downloadManager.query(query) } catch (e: Exception) { null }

            if (cursor != null && cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)

                val status = if (statusIndex >= 0) cursor.getInt(statusIndex) else -1
                val downloaded = if (bytesDownloadedIndex >= 0) cursor.getLong(bytesDownloadedIndex) else 0L
                val total = if (bytesTotalIndex >= 0) cursor.getLong(bytesTotalIndex) else 0L
                val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else 0

                val percent = if (total > 0) (downloaded.toFloat() / total.toFloat()) else 0f

                when (status) {
                    DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                        emit(
                            ModelDownloadProgress(
                                downloadId = downloadId,
                                isDownloading = true,
                                bytesDownloaded = downloaded,
                                totalBytes = total,
                                progressPercent = percent,
                                statusText = if (status == DownloadManager.STATUS_PENDING) "Connecting to Hugging Face..." else "Downloading LiteRT Model (${(downloaded / (1024 * 1024))} MB / ${if (total > 0) "${(total / (1024 * 1024))} MB" else "calculating..."})",
                                isCompleted = false
                            )
                        )
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        downloading = false
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val targetFile = File(downloadsDir, "Gemma/$fileName")
                        
                        val isValidModelFile = targetFile.exists() && targetFile.length() > 50 * 1024 * 1024L // Must be > 50MB
                        
                        if (isValidModelFile) {
                            emit(
                                ModelDownloadProgress(
                                    downloadId = downloadId,
                                    isDownloading = false,
                                    bytesDownloaded = total,
                                    totalBytes = total,
                                    progressPercent = 1.0f,
                                    statusText = "Download Complete (${targetFile.length() / (1024 * 1024)} MB)!",
                                    isCompleted = true,
                                    isSuccess = true,
                                    downloadedFile = targetFile
                                )
                            )
                        } else {
                            emit(
                                ModelDownloadProgress(
                                    downloadId = downloadId,
                                    isDownloading = false,
                                    isCompleted = true,
                                    isSuccess = false,
                                    errorMessage = "Downloaded file is incomplete (${if (targetFile.exists()) targetFile.length() / 1024 else 0} KB). Please re-download or pick a valid model file."
                                )
                            )
                        }
                    }
                    DownloadManager.STATUS_FAILED -> {
                        downloading = false
                        emit(
                            ModelDownloadProgress(
                                downloadId = downloadId,
                                isDownloading = false,
                                isCompleted = true,
                                isSuccess = false,
                                errorMessage = "Download failed (code $reason). Please check network or try again."
                            )
                        )
                    }
                    else -> {
                        // In case status is PAUSED or unknown
                        emit(
                            ModelDownloadProgress(
                                downloadId = downloadId,
                                isDownloading = true,
                                progressPercent = percent,
                                statusText = "Preparing download...",
                                isCompleted = false
                            )
                        )
                    }
                }
                cursor.close()
            } else {
                cursor?.close()
                downloading = false
            }

            if (downloading) {
                delay(800)
            }
        }
    }
}


