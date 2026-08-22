package com.example.malaysiaitinerary.ui.ai

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.malaysiaitinerary.ai.*
import com.example.malaysiaitinerary.data.repository.*
import com.example.malaysiaitinerary.util.DiscoveredModel
import com.example.malaysiaitinerary.util.ModelScanner
import com.example.malaysiaitinerary.data.repository.TripRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ChatUiMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolExecutions: List<String> = emptyList(),
    val isPending: Boolean = false,
    val isSavedToDatabase: Boolean = false,
    val parsedTitle: String? = null,
    val parsedDate: String? = null,
    val parsedTime: String? = null,
    val parsedLocation: String? = null,
    val hasActionableContent: Boolean = false
)

enum class MessageSender {
    USER,
    AI,
    SYSTEM
}

data class ModelImportState(
    val showDialog: Boolean = false,
    val isImporting: Boolean = false,
    val isCompleted: Boolean = false,
    val isSuccess: Boolean = false,
    val fileName: String = "",
    val currentStep: String = "",
    val progressPercent: Float = 0f,
    val detailMessage: String = "",
    val errorMessage: String? = null
)

class AiAssistantViewModel(
    private val context: Context,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val itineraryRepository: ItineraryRepository,
    private val documentRepository: DocumentRepository,
    private val expenseRepository: ExpenseRepository,
    private val tripRepository: TripRepository? = null
) : ViewModel() {

    private val toolHandler = AiToolHandler(itineraryRepository, documentRepository, expenseRepository, tripRepository)

    private val _messages = MutableStateFlow<List<ChatUiMessage>>(
        listOf(
            ChatUiMessage(
                sender = MessageSender.AI,
                text = "Hello! I am Explorer AI. I can parse travel vouchers, add tickets to your vault, update your schedule offline, or create new trip plans!"
            )
        )
    )
    val messages: StateFlow<List<ChatUiMessage>> = _messages.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _aiCoreUnavailable = MutableStateFlow(false)
    val aiCoreUnavailable: StateFlow<Boolean> = _aiCoreUnavailable.asStateFlow()

    private val _extractedResult = MutableStateFlow<ExtractedDocumentResult?>(null)
    val extractedResult: StateFlow<ExtractedDocumentResult?> = _extractedResult.asStateFlow()

    private val _scannedModels = MutableStateFlow<List<DiscoveredModel>>(emptyList())
    val scannedModels: StateFlow<List<DiscoveredModel>> = _scannedModels.asStateFlow()

    private val _attachedImageUri = MutableStateFlow<Uri?>(null)
    val attachedImageUri: StateFlow<Uri?> = _attachedImageUri.asStateFlow()

    private val _modelImportState = MutableStateFlow(ModelImportState())
    val modelImportState: StateFlow<ModelImportState> = _modelImportState.asStateFlow()

    private val _downloadProgress = MutableStateFlow<com.example.malaysiaitinerary.util.ModelDownloadProgress?>(null)
    val downloadProgress: StateFlow<com.example.malaysiaitinerary.util.ModelDownloadProgress?> = _downloadProgress.asStateFlow()

    fun dismissModelImportDialog() {
        _modelImportState.value = ModelImportState(showDialog = false)
    }

    private var activeDownloadId: Long? = null

    private val _showVariantDialog = MutableStateFlow(false)
    val showVariantDialog: StateFlow<Boolean> = _showVariantDialog.asStateFlow()

    fun showDownloadVariantDialog() {
        _showVariantDialog.value = true
    }

    fun dismissDownloadVariantDialog() {
        _showVariantDialog.value = false
    }

    fun startModelDownload() {
        _showVariantDialog.value = false
        val fileName = "gemma-4-E2B-it.litertlm"
        val downloadId = com.example.malaysiaitinerary.util.ModelDownloader.startDirectDownload(context)
        activeDownloadId = downloadId
        viewModelScope.launch(Dispatchers.IO) {
            com.example.malaysiaitinerary.util.ModelDownloader.observeDownloadProgress(context, downloadId, fileName = fileName).collect { progress ->
                _downloadProgress.value = progress
                if (progress.isCompleted) {
                    activeDownloadId = null
                    if (progress.isSuccess && progress.downloadedFile != null) {
                        selectModelFileFromUri(android.net.Uri.fromFile(progress.downloadedFile))
                    }
                    kotlinx.coroutines.delay(2500)
                    _downloadProgress.value = null
                }
            }
        }
    }

    fun cancelDownload() {
        val id = activeDownloadId ?: _downloadProgress.value?.downloadId
        if (id != null && id != -1L) {
            com.example.malaysiaitinerary.util.ModelDownloader.cancelDownload(context, id)
        }
        activeDownloadId = null
        _downloadProgress.value = null
    }

    fun dismissDownloadProgress() {
        cancelDownload()
    }

    private val _isVoiceTravelModeActive = MutableStateFlow(false)
    val isVoiceTravelModeActive: StateFlow<Boolean> = _isVoiceTravelModeActive.asStateFlow()

    private val _isTtsEnabled = MutableStateFlow(true)
    val isTtsEnabled: StateFlow<Boolean> = _isTtsEnabled.asStateFlow()

    val engineMode = aiPreferencesRepository.engineMode
    val gemmaChoice = aiPreferencesRepository.gemmaModelChoice
    val gemmaPath = aiPreferencesRepository.gemmaModelPath
    val geminiKey = aiPreferencesRepository.geminiApiKey
    val isSearchEnabled = aiPreferencesRepository.isSearchGroundingEnabled

    val hasTrips: StateFlow<Boolean> = itineraryRepository.allItems
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        scanForModelsOnDevice()
    }

    fun setAttachedImageUri(uri: Uri?) {
        _attachedImageUri.value = uri
    }

    fun setVoiceTravelModeActive(active: Boolean) {
        _isVoiceTravelModeActive.value = active
    }

    fun toggleVoiceTravelMode() {
        _isVoiceTravelModeActive.value = !_isVoiceTravelModeActive.value
    }

    fun toggleTtsEnabled() {
        _isTtsEnabled.value = !_isTtsEnabled.value
    }

    fun scanForModelsOnDevice() {
        viewModelScope.launch(Dispatchers.IO) {
            val discovered = ModelScanner.scanForGemmaModels(context)
            _scannedModels.value = discovered

            val currentPath = gemmaPath.first()
            if (currentPath.isBlank() && discovered.isNotEmpty()) {
                val autoModel = discovered.first()
                aiPreferencesRepository.setGemmaModelPath(autoModel.file.absolutePath)
            }
        }
    }

    fun selectDiscoveredModel(model: DiscoveredModel) {
        viewModelScope.launch {
            aiPreferencesRepository.setGemmaModelPath(model.file.absolutePath)
        }
    }

    fun loadDemoItinerary() {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val today = java.time.LocalDate.now()
                val day1 = today.plusDays(1).toString()
                val day2 = today.plusDays(2).toString()
                val day3 = today.plusDays(3).toString()

                val demoItems = listOf(
                    com.example.malaysiaitinerary.data.local.entity.ItineraryItem(
                        title = "Arrival & Petronas Twin Towers Skybridge",
                        locationName = "Kuala Lumpur, Malaysia",
                        date = day1,
                        startTime = "10:00",
                        description = "Check-in at hotel and visit iconic Petronas Twin Towers Skybridge.",
                        type = "ACTIVITY"
                    ),
                    com.example.malaysiaitinerary.data.local.entity.ItineraryItem(
                        title = "Street Food Feast at Jalan Alor",
                        locationName = "Jalan Alor, Bukit Bintang",
                        date = day1,
                        startTime = "19:00",
                        description = "Sample authentic Malaysian satay, char kway teow, and coconut ice cream.",
                        type = "MEAL",
                        vegOptions = "Fried tofu, mango sticky rice, grilled corn",
                        nonVegOptions = "Chicken satay, seafood char kway teow"
                    ),
                    com.example.malaysiaitinerary.data.local.entity.ItineraryItem(
                        title = "Batu Caves & ETS Train to Penang",
                        locationName = "Batu Caves, Selangor",
                        date = day2,
                        startTime = "09:00",
                        description = "Explore colorful Batu Caves temple steps, then catch ETS train to Penang.",
                        type = "TRANSIT"
                    ),
                    com.example.malaysiaitinerary.data.local.entity.ItineraryItem(
                        title = "George Town UNESCO Heritage Walk",
                        locationName = "George Town, Penang",
                        date = day2,
                        startTime = "16:00",
                        description = "Discover famous street art murals, Clan Jetties, and historic heritage houses.",
                        type = "ACTIVITY"
                    ),
                    com.example.malaysiaitinerary.data.local.entity.ItineraryItem(
                        title = "Penang Hill Funicular & Habitat",
                        locationName = "Penang Hill",
                        date = day3,
                        startTime = "09:30",
                        description = "Ride funicular railway to Penang Hill peak for panoramic island views.",
                        type = "ACTIVITY"
                    ),
                    com.example.malaysiaitinerary.data.local.entity.ItineraryItem(
                        title = "Souvenir Shopping & Flight Departure",
                        locationName = "Penang International Airport (PEN)",
                        date = day3,
                        startTime = "17:00",
                        description = "Buy Penang white coffee and nutmeg before departure flight.",
                        type = "TRANSIT"
                    )
                )

                itineraryRepository.insertItems(demoItems)

                val demoExpense = com.example.malaysiaitinerary.data.local.entity.Expense(
                    amount = 45.0,
                    currency = "MYR",
                    convertedAmountMYR = 45.0,
                    convertedAmountINR = 800.0,
                    category = "Food",
                    date = day1,
                    description = "Jalan Alor Street Food Supper"
                )
                expenseRepository.insertExpense(demoExpense)

                val demoDoc = com.example.malaysiaitinerary.data.local.entity.Document(
                    fileName = "MH1142_BoardingPass.pdf",
                    filePath = "",
                    type = "FLIGHT",
                    date = day2,
                    location = "Kuala Lumpur (KUL) -> Penang (PEN)"
                )
                documentRepository.insertDocument(demoDoc)

                _messages.update {
                    it + ChatUiMessage(
                        sender = MessageSender.SYSTEM,
                        text = "3-Day Malaysia Demo Trip successfully created starting tomorrow ($day1)! All tabs unlocked."
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun selectModelFileFromUri(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val fileName = getFileNameFromUri(context, uri) ?: "gemma-4-E2B-it-web.task"
            val lowerName = fileName.lowercase()

            _modelImportState.value = ModelImportState(
                showDialog = true,
                isImporting = true,
                isCompleted = false,
                fileName = fileName,
                currentStep = "Reading selected file...",
                progressPercent = 0.1f,
                detailMessage = "Please stay on this screen while the AI model is being processed."
            )

            _messages.update {
                it + ChatUiMessage(
                    sender = MessageSender.SYSTEM,
                    text = "Importing & processing Gemma 4 model file '${fileName}'... Please stay on screen."
                )
            }

            if (lowerName.endsWith(".safetensors") || lowerName.endsWith(".pth") || lowerName.endsWith(".h5") || lowerName.endsWith(".onnx")) {
                val errorMsg = """
                    Cannot load '${fileName}': .safetensors is raw PyTorch weights.
                    
                    On Mobile Devices, LiteRT-LM requires the compiled .litertlm or .task binary package.
                    
                    Official Google AI Edge Conversion Command (litert-torch):
                    uv tool install litert-torch-nightly
                    litert-torch export_hf \
                      --model=google/gemma-4-E2B-it \
                      --output_dir=/tmp/gemma4_2b \
                      --externalize_embedder \
                      --jinja_chat_template_override=litert-community/gemma-4-E2B-it-litert-lm

                    Official Docs: https://developers.google.com/edge/litert-lm/models/gemma-4#deploy_from_safetensors
                    Download Pre-compiled Model: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
                """.trimIndent()
                _modelImportState.value = ModelImportState(
                    showDialog = true,
                    isImporting = false,
                    isCompleted = true,
                    isSuccess = false,
                    fileName = fileName,
                    currentStep = "Convert Safetensors with litert-torch",
                    progressPercent = 1.0f,
                    detailMessage = errorMsg,
                    errorMessage = "Raw PyTorch safetensors format needs conversion via litert-torch to .litertlm."
                )
                _messages.update { it + ChatUiMessage(sender = MessageSender.SYSTEM, text = errorMsg) }
                return@launch
            }

            // Step 1: Copy stream safely to a temp cache file
            _modelImportState.update {
                it.copy(
                    currentStep = "Copying model file to local storage...",
                    progressPercent = 0.2f,
                    detailMessage = "Streaming model data to app storage. Please stay on this screen..."
                )
            }

            val tempFile = File(context.cacheDir, "temp_model_${System.currentTimeMillis()}_${fileName}")
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (ignored: Exception) {}

                val inputStream = try {
                    context.contentResolver.openInputStream(uri)
                } catch (e: Exception) {
                    val rawPath = uri.path
                    val resolvedPath = when {
                        rawPath == null -> null
                        rawPath.contains("/document/primary:") -> "/storage/emulated/0/" + rawPath.substringAfter("/document/primary:")
                        rawPath.contains("/document/raw:") -> rawPath.substringAfter("/document/raw:")
                        else -> rawPath
                    }
                    if (resolvedPath != null && File(resolvedPath).exists() && File(resolvedPath).canRead()) {
                        File(resolvedPath).inputStream()
                    } else {
                        throw e
                    }
                }

                if (inputStream != null) {
                    inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(1024 * 512) // 512KB buffer for fast streaming
                            var read: Int
                            var totalRead = 0L
                            var lastProgressUpdate = System.currentTimeMillis()
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                totalRead += read
                                val now = System.currentTimeMillis()
                                if (now - lastProgressUpdate > 300) {
                                    lastProgressUpdate = now
                                    val mbRead = totalRead / (1024 * 1024)
                                    _modelImportState.update { state ->
                                        state.copy(
                                            progressPercent = 0.2f + (0.4f * (totalRead.toFloat() / (2000 * 1024 * 1024f)).coerceAtMost(1.0f)),
                                            detailMessage = "Streamed $mbRead MB to app cache..."
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    throw Exception("Could not open file stream for '$fileName'")
                }
            } catch (e: Exception) {
                val errorMsg = "Failed to read selected file stream: ${e.localizedMessage ?: e.message}\n\nTip: You can also use 'Direct Download' in Settings or grant Storage Access permission."
                _modelImportState.value = ModelImportState(
                    showDialog = true,
                    isImporting = false,
                    isCompleted = true,
                    isSuccess = false,
                    fileName = fileName,
                    currentStep = "Import Failed",
                    progressPercent = 1.0f,
                    detailMessage = errorMsg,
                    errorMessage = errorMsg
                )
                _messages.update { it + ChatUiMessage(sender = MessageSender.SYSTEM, text = errorMsg) }
                return@launch
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                val errorMsg = "Selected file is empty (0 bytes). Please re-download the model file."
                _modelImportState.value = ModelImportState(
                    showDialog = true,
                    isImporting = false,
                    isCompleted = true,
                    isSuccess = false,
                    fileName = fileName,
                    currentStep = "Empty File",
                    progressPercent = 1.0f,
                    detailMessage = errorMsg,
                    errorMessage = errorMsg
                )
                _messages.update { it + ChatUiMessage(sender = MessageSender.SYSTEM, text = errorMsg) }
                tempFile.delete()
                return@launch
            }

            // Step 2: Validate file content size/type (< 10 KB)
            _modelImportState.update {
                it.copy(
                    currentStep = "Validating binary model structure...",
                    progressPercent = 0.65f,
                    detailMessage = "Inspecting model headers and file structure..."
                )
            }

            if (tempFile.length() < 1024 * 10) {
                val sampleContent = try { tempFile.readText(Charsets.UTF_8) } catch (e: Exception) { "" }
                if (sampleContent.contains("<!DOCTYPE html>") || sampleContent.contains("<html") || sampleContent.contains("version https://git-lfs.github.com")) {
                    val errorMsg = "The file '${fileName}' is an HTML web page or LFS text pointer, not a valid binary model.\n\nPlease use the direct download link: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-web.task?download=true"
                    _modelImportState.value = ModelImportState(
                        showDialog = true,
                        isImporting = false,
                        isCompleted = true,
                        isSuccess = false,
                        fileName = fileName,
                        currentStep = "Invalid Model Binary",
                        progressPercent = 1.0f,
                        detailMessage = errorMsg,
                        errorMessage = "File is an HTML/LFS pointer rather than a binary model."
                    )
                    _messages.update { it + ChatUiMessage(sender = MessageSender.SYSTEM, text = errorMsg) }
                    tempFile.delete()
                    return@launch
                }
            }

            // Step 3: Fast Atomic File Placement (Preserve .task / .bin archives)
            _modelImportState.update {
                it.copy(
                    currentStep = "Finalizing MediaPipe model placement...",
                    progressPercent = 0.85f,
                    detailMessage = "Optimizing model structure for MediaPipe GenAI engine..."
                )
            }

            val lowerFileName = fileName.lowercase()
            val isAlreadyZip = isZipArchive(tempFile)

            val rawDestName = when {
                !isAlreadyZip -> {
                    if (fileName.contains(".")) fileName.substringBeforeLast(".") + ".litertlm" else "$fileName.litertlm"
                }
                lowerFileName.endsWith(".bin") || lowerFileName.endsWith(".task") || lowerFileName.endsWith(".litertlm") -> {
                    if (lowerFileName.endsWith(".bin")) fileName.substring(0, fileName.length - 4) + ".task" else fileName
                }
                fileName.endsWith(".zip", ignoreCase = true) -> fileName.substring(0, fileName.length - 4) + ".task"
                fileName.contains(".") -> fileName.substringBeforeLast(".") + ".task"
                else -> "$fileName.task"
            }
            val cleanExt = if (rawDestName.contains(".")) "." + rawDestName.substringAfterLast(".") else ".task"
            val cleanBaseName = rawDestName.substringBeforeLast(".").replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val destName = "$cleanBaseName$cleanExt"
            val destFile = File(context.filesDir, destName)

            if (!tempFile.renameTo(destFile)) {
                try {
                    tempFile.copyTo(destFile, overwrite = true)
                    tempFile.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Step 4: Testing Engine
            _modelImportState.update {
                it.copy(
                    currentStep = "Testing MediaPipe AI Engine initialization...",
                    progressPercent = 0.95f,
                    detailMessage = "Initializing model weights on device. Finalizing setup..."
                )
            }

            aiPreferencesRepository.setGemmaModelPath(destFile.absolutePath)
            aiPreferencesRepository.setGemmaModelChoice(GemmaModelChoice.CUSTOM_IMPORTED)
            aiPreferencesRepository.setEngineMode(AiEngineMode.AUTO_ON_DEVICE)
            _aiCoreUnavailable.value = false

            val testEngine = MediaPipeGemmaEngine(context, GemmaModelChoice.CUSTOM_IMPORTED, destFile.absolutePath)

            if (testEngine.initErrorReason != null) {
                val noticeText = "Notice: ${testEngine.initErrorReason}\n\nSwitched to Gemini Cloud Engine so you can continue chatting seamlessly."
                _modelImportState.value = ModelImportState(
                    showDialog = true,
                    isImporting = false,
                    isCompleted = true,
                    isSuccess = false,
                    fileName = destFile.name,
                    currentStep = "Processed with Notice",
                    progressPercent = 1.0f,
                    detailMessage = noticeText,
                    errorMessage = testEngine.initErrorReason
                )
                _messages.update {
                    it + ChatUiMessage(
                        sender = MessageSender.SYSTEM,
                        text = "On-Device Engine Notice: ${testEngine.initErrorReason}\n\nSwitching to Gemini Cloud Engine so you can continue chatting seamlessly."
                    )
                }
                aiPreferencesRepository.setEngineMode(AiEngineMode.GEMINI_CLOUD)
            } else {
                val successText = "Local Gemma 4 AI Model Ready!\nFile: ${destFile.name} (${destFile.length() / (1024 * 1024)} MB)\nEngine: On-Device Gemma 4 LiteRT-LM is initialized and active for offline responses."
                _modelImportState.value = ModelImportState(
                    showDialog = true,
                    isImporting = false,
                    isCompleted = true,
                    isSuccess = true,
                    fileName = destFile.name,
                    currentStep = "Import Complete!",
                    progressPercent = 1.0f,
                    detailMessage = successText
                )
                _messages.update {
                    it + ChatUiMessage(
                        sender = MessageSender.SYSTEM,
                        text = successText
                    )
                }
            }
            scanForModelsOnDevice()
        }
    }

    private fun getFileNameFromUri(context: Context, uri: android.net.Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) name = it.getString(index)
            }
        }
        return name
    }

    private fun isZipArchive(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            file.inputStream().use { input ->
                val buffer = ByteArray(4)
                val bytesRead = input.read(buffer, 0, 4)
                bytesRead == 4 &&
                    buffer[0] == 'P'.code.toByte() &&
                    buffer[1] == 'K'.code.toByte() &&
                    buffer[2] == 0x03.toByte() &&
                    buffer[3] == 0x04.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun packageRawBinaryToMediaPipeTaskZip(rawFile: File, outputFile: File) {
        val tempOutputFile = File(outputFile.parentFile, "temp_${outputFile.name}")
        java.util.zip.ZipOutputStream(tempOutputFile.outputStream().buffered()).use { zipOut ->
            zipOut.setMethod(java.util.zip.ZipOutputStream.STORED)

            // 1. Entry: METADATA (STORED uncompressed)
            val metadataBytes = "MediaPipe LlmInference Model Bundle Metadata".toByteArray(Charsets.UTF_8)
            val metadataCrc = java.util.zip.CRC32().apply { update(metadataBytes) }
            val metadataEntry = java.util.zip.ZipEntry("METADATA").apply {
                method = java.util.zip.ZipEntry.STORED
                size = metadataBytes.size.toLong()
                compressedSize = metadataBytes.size.toLong()
                setCrc(metadataCrc.value)
            }
            zipOut.putNextEntry(metadataEntry)
            zipOut.write(metadataBytes)
            zipOut.closeEntry()

            // 2. Entry: model.tflite (STORED uncompressed for mmap)
            val modelBytesSize = rawFile.length()
            val crc32 = java.util.zip.CRC32()
            rawFile.inputStream().buffered().use { input ->
                val buffer = ByteArray(65536)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    crc32.update(buffer, 0, bytesRead)
                }
            }

            val modelEntry = java.util.zip.ZipEntry("model.tflite").apply {
                method = java.util.zip.ZipEntry.STORED
                size = modelBytesSize
                compressedSize = modelBytesSize
                setCrc(crc32.value)
            }
            zipOut.putNextEntry(modelEntry)
            rawFile.inputStream().buffered().use { input ->
                input.copyTo(zipOut)
            }
            zipOut.closeEntry()

            // 3. Entry: task.json (STORED uncompressed)
            val taskJsonBytes = "{\"task_name\":\"LlmInference\",\"version\":\"1\"}".toByteArray(Charsets.UTF_8)
            val taskCrc32 = java.util.zip.CRC32().apply { update(taskJsonBytes) }
            val taskJsonEntry = java.util.zip.ZipEntry("task.json").apply {
                method = java.util.zip.ZipEntry.STORED
                size = taskJsonBytes.size.toLong()
                compressedSize = taskJsonBytes.size.toLong()
                setCrc(taskCrc32.value)
            }
            zipOut.putNextEntry(taskJsonEntry)
            zipOut.write(taskJsonBytes)
            zipOut.closeEntry()
        }

        if (tempOutputFile.exists() && tempOutputFile.length() > 0) {
            if (outputFile.exists()) outputFile.delete()
            tempOutputFile.renameTo(outputFile)
        }
    }

    private fun extractGzipArchive(inputStream: java.io.InputStream, originalFileName: String): File? {
        val destDir = context.filesDir
        try {
            val gzipStream = java.util.zip.GZIPInputStream(inputStream)
            val buffer = ByteArray(512)
            var extractedFile: File? = null
            var maxExtractedSize = -1L

            var headerBytesRead = 0
            while (headerBytesRead < 512) {
                val r = gzipStream.read(buffer, headerBytesRead, 512 - headerBytesRead)
                if (r < 0) break
                headerBytesRead += r
            }

            if (headerBytesRead == 512 && isTarHeader(buffer)) {
                var currentBuffer = buffer
                while (true) {
                    if (currentBuffer.all { it == 0.toByte() }) break

                    val entryName = String(currentBuffer, 0, 100, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                    val sizeStr = String(currentBuffer, 124, 12, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                    val fileSize = try { sizeStr.toLong(8) } catch (e: Exception) { 0L }
                    val typeFlag = currentBuffer[156]

                    if ((typeFlag == '0'.toByte() || typeFlag == 0.toByte()) && fileSize > 0) {
                        val cleanName = File(entryName).name
                        val lowerName = cleanName.lowercase()
                        val isModel = (lowerName.endsWith(".task") || lowerName.endsWith(".bin") || lowerName.endsWith(".litertlm")) && !lowerName.endsWith(".safetensors") && !lowerName.endsWith(".pth")

                        if (isModel) {
                            val targetName = if (cleanName.isNotBlank() && cleanName.contains(".")) cleanName else "gemma_extracted_model.bin"
                            val destFile = File(destDir, targetName)
                            destFile.outputStream().use { out ->
                                var remaining = fileSize
                                val chunk = ByteArray(8192)
                                while (remaining > 0) {
                                    val toRead = minOf(remaining, chunk.size.toLong()).toInt()
                                    val read = gzipStream.read(chunk, 0, toRead)
                                    if (read < 0) break
                                    out.write(chunk, 0, read)
                                    remaining -= read
                                }
                            }
                            if (destFile.length() > maxExtractedSize) {
                                maxExtractedSize = destFile.length()
                                extractedFile = destFile
                            }
                            val padding = ((512 - (fileSize % 512)) % 512).toInt()
                            if (padding > 0) gzipStream.skip(padding.toLong())
                        } else {
                            val totalSkip = fileSize + ((512 - (fileSize % 512)) % 512)
                            var skipLeft = totalSkip
                            val chunk = ByteArray(8192)
                            while (skipLeft > 0) {
                                val read = gzipStream.read(chunk, 0, minOf(skipLeft, chunk.size.toLong()).toInt())
                                if (read <= 0) break
                                skipLeft -= read
                            }
                        }
                    }

                    var bytesRead = 0
                    currentBuffer = ByteArray(512)
                    while (bytesRead < 512) {
                        val r = gzipStream.read(currentBuffer, bytesRead, 512 - bytesRead)
                        if (r < 0) break
                        bytesRead += r
                    }
                    if (bytesRead < 512) break
                }
                if (extractedFile != null) return extractedFile
            }

            val targetName = when {
                originalFileName.lowercase().endsWith(".tar.gz") -> originalFileName.substring(0, originalFileName.length - 7) + ".bin"
                originalFileName.lowercase().endsWith(".tar.z") -> originalFileName.substring(0, originalFileName.length - 6) + ".bin"
                originalFileName.lowercase().endsWith(".gz") -> originalFileName.substring(0, originalFileName.length - 3)
                originalFileName.lowercase().endsWith(".tgz") -> originalFileName.substring(0, originalFileName.length - 4) + ".bin"
                else -> "gemma_kaggle_model.bin"
            }
            val rawDestFile = File(destDir, targetName)
            rawDestFile.outputStream().use { out ->
                if (headerBytesRead > 0) {
                    out.write(buffer, 0, headerBytesRead)
                }
                gzipStream.copyTo(out)
            }
            if (rawDestFile.exists() && rawDestFile.length() > 0) {
                return rawDestFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun isTarHeader(buffer: ByteArray): Boolean {
        if (buffer.size < 512) return false
        val magic = String(buffer, 257, 5, Charsets.US_ASCII)
        return magic.startsWith("ustar") || buffer[156] == '0'.toByte() || buffer[156] == 0.toByte()
    }

    fun setEngineMode(mode: AiEngineMode) {
        viewModelScope.launch {
            aiPreferencesRepository.setEngineMode(mode)
            _aiCoreUnavailable.value = false
        }
    }

    private suspend fun getActiveEngine(): AiEngine {
        return when (engineMode.first()) {
            AiEngineMode.AUTO_ON_DEVICE -> AutoOnDeviceEngine(
                context = context,
                modelChoice = gemmaChoice.first(),
                customModelPath = gemmaPath.first().takeIf { it.isNotBlank() },
                apiKey = geminiKey.first()
            )
            AiEngineMode.GEMINI_CLOUD -> GeminiCloudEngine(apiKey = geminiKey.first())
            AiEngineMode.ON_DEVICE_GEMMA -> MediaPipeGemmaEngine(
                context = context,
                modelChoice = gemmaChoice.first(),
                customModelPath = gemmaPath.first().takeIf { it.isNotBlank() }
            )
        }
    }

    fun startNewChat() {
        _messages.value = listOf(
            ChatUiMessage(
                sender = MessageSender.AI,
                text = "Hello! I am Explorer AI. I can parse travel vouchers, add tickets to your vault, update your schedule offline, or create new trip plans!"
            )
        )
        _attachedImageUri.value = null
    }

    fun sendMessage(userText: String) {
        val attachedUri = _attachedImageUri.value
        if (userText.isBlank() && attachedUri == null) return

        _attachedImageUri.value = null

        viewModelScope.launch {
            _isProcessing.value = true
            _aiCoreUnavailable.value = false

            val imageBitmap = if (attachedUri != null) {
                try {
                    val inputStream = context.contentResolver.openInputStream(attachedUri)
                    android.graphics.BitmapFactory.decodeStream(inputStream)
                } catch (e: Exception) {
                    null
                }
            } else null

            val finalPrompt = when {
                attachedUri != null ->
                    "[Landmark/Photo Attached]\nUser Question: ${if (userText.isBlank()) "Identify this landmark or photo natively and provide travel details and tips." else userText}"
                else -> userText
            }

            val userMsg = ChatUiMessage(sender = MessageSender.USER, text = if (attachedUri != null) "[Photo Attached] ${if (userText.isBlank()) "Identify this landmark" else userText}" else userText)
            _messages.update { it + userMsg }

            try {
                val lower = userText.lowercase()
                val isTripCreation = (lower.contains("create") || lower.contains("generate") || lower.contains("plan") || lower.contains("make")) &&
                        (lower.contains("itinerary") || lower.contains("trip") || lower.contains("days"))

                if (isTripCreation) {
                    val dest = when {
                        lower.contains("goa") -> "Goa"
                        lower.contains("langkawi") -> "Langkawi"
                        lower.contains("penang") -> "Penang"
                        lower.contains("kuala lumpur") || lower.contains("kl") -> "Kuala Lumpur"
                        lower.contains("melaka") -> "Melaka"
                        else -> userText.split("for", "in", "to").lastOrNull()?.trim()?.take(20) ?: "Malaysia"
                    }
                    val days = Regex("(\\d+)\\s*day").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 3

                    val planResult = generateNewTrip(dest, days, userText)
                    _isProcessing.value = false
                    return@launch
                }

                val engine = getActiveEngine()
                val history = _messages.value.filter { it.sender != MessageSender.SYSTEM }
                    .takeLast(6)
                    .map { (if (it.sender == MessageSender.USER) "User" else "Assistant") to it.text }

                val items = itineraryRepository.allItems.first()
                val expenses = expenseRepository.allExpenses.first()
                val documents = documentRepository.allDocuments.first()

                val totalMYR = expenses.sumOf { it.convertedAmountMYR }
                val totalINR = expenses.sumOf { it.convertedAmountINR }

                val tripSummary = buildString {
                    appendLine("=== TRIP ITINERARY SCHEDULE ===")
                    if (items.isNotEmpty()) {
                        items.take(10).forEach { item ->
                            appendLine("${item.date} ${item.startTime}: ${item.title} (${item.type}) at ${item.locationName}")
                        }
                    } else {
                        appendLine("No schedule items in database.")
                    }

                    appendLine("\n=== RECORDED TRIP EXPENSES & BUDGET ===")
                    if (expenses.isNotEmpty()) {
                        appendLine("Total Spent: MYR ${"%.2f".format(totalMYR)} / INR ${"%.2f".format(totalINR)}")
                        expenses.forEach { exp ->
                            appendLine("- ${exp.date}: ${exp.description} [${exp.category}] = ${exp.amount} ${exp.currency} (MYR ${"%.2f".format(exp.convertedAmountMYR)})")
                        }
                    } else {
                        appendLine("No expenses logged yet in database.")
                    }

                    appendLine("\n=== BRIEFCASE DOCUMENTS & TICKETS ===")
                    if (documents.isNotEmpty()) {
                        documents.forEach { doc ->
                            appendLine("- ${doc.fileName} (${doc.type}) on ${doc.date ?: "N/A"}")
                        }
                    } else {
                        appendLine("No documents stored in vault.")
                    }
                }

                val aiResult = engine.chat(finalPrompt, history, tripSummary, imageBitmap = imageBitmap)

                aiResult.onSuccess { response ->
                    val executedLogs = mutableListOf<String>()
                    response.toolCalls.forEach { toolCall ->
                        val log = toolHandler.executeToolCall(toolCall)
                        executedLogs.add(log)
                    }

                    val aiMsg = ChatUiMessage(
                        sender = MessageSender.AI,
                        text = response.messageText,
                        toolExecutions = executedLogs,
                        hasActionableContent = response.toolCalls.isNotEmpty()
                    )
                    _messages.update { it + aiMsg }
                }.onFailure { err ->
                    val friendlyMsg = "Local Engine Notice (${engine.engineName}): ${err.localizedMessage ?: "Service unavailable."}\n\nSwitched to Gemini Cloud Engine so you can continue chatting seamlessly."
                    val errorMsg = ChatUiMessage(
                        sender = MessageSender.SYSTEM,
                        text = friendlyMsg
                    )
                    _messages.update { it + errorMsg }
                    aiPreferencesRepository.setEngineMode(AiEngineMode.GEMINI_CLOUD)
                }
            } catch (e: Exception) {
                val friendlyMsg = "Error: ${e.localizedMessage ?: "Execution failed."}"
                _messages.update {
                    it + ChatUiMessage(sender = MessageSender.SYSTEM, text = friendlyMsg)
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun sendAudioPrompt(audioBytes: ByteArray, userText: String = "") {
        viewModelScope.launch {
            _isProcessing.value = true

            val prompt = if (userText.isBlank()) "[Native Audio Stream Input Attached]\nPlease process this raw audio input directly and assist the user." else userText

            val userMsg = ChatUiMessage(
                sender = MessageSender.USER,
                text = "[Raw Audio Attached] ${if (userText.isBlank()) "Audio prompt" else userText}"
            )
            _messages.update { it + userMsg }

            try {
                val engine = getActiveEngine()
                val history = _messages.value.filter { it.sender != MessageSender.SYSTEM }
                    .takeLast(6)
                    .map { (if (it.sender == MessageSender.USER) "User" else "Assistant") to it.text }

                val items = itineraryRepository.allItems.first()
                val expenses = expenseRepository.allExpenses.first()
                val documents = documentRepository.allDocuments.first()

                val totalMYR = expenses.sumOf { it.convertedAmountMYR }
                val totalINR = expenses.sumOf { it.convertedAmountINR }

                val tripSummary = buildString {
                    appendLine("=== TRIP ITINERARY SCHEDULE ===")
                    if (items.isNotEmpty()) {
                        items.take(10).forEach { item ->
                            appendLine("${item.date} ${item.startTime}: ${item.title} (${item.type}) at ${item.locationName}")
                        }
                    } else {
                        appendLine("No schedule items in database.")
                    }

                    appendLine("\n=== RECORDED TRIP EXPENSES & BUDGET ===")
                    if (expenses.isNotEmpty()) {
                        appendLine("Total Spent: MYR ${"%.2f".format(totalMYR)} / INR ${"%.2f".format(totalINR)}")
                        expenses.forEach { exp ->
                            appendLine("- ${exp.date}: ${exp.description} [${exp.category}] = ${exp.amount} ${exp.currency} (MYR ${"%.2f".format(exp.convertedAmountMYR)})")
                        }
                    } else {
                        appendLine("No expenses logged yet in database.")
                    }

                    appendLine("\n=== BRIEFCASE DOCUMENTS & TICKETS ===")
                    if (documents.isNotEmpty()) {
                        documents.forEach { doc ->
                            appendLine("- ${doc.fileName} (${doc.type}) on ${doc.date ?: "N/A"}")
                        }
                    } else {
                        appendLine("No documents stored in vault.")
                    }
                }

                val aiResult = engine.chat(prompt, history, tripSummary, audioBytes = audioBytes)

                aiResult.onSuccess { response ->
                    val executedLogs = mutableListOf<String>()
                    response.toolCalls.forEach { toolCall ->
                        val log = toolHandler.executeToolCall(toolCall)
                        executedLogs.add(log)
                    }

                    val aiMsg = ChatUiMessage(
                        sender = MessageSender.AI,
                        text = response.messageText,
                        toolExecutions = executedLogs,
                        hasActionableContent = response.toolCalls.isNotEmpty()
                    )
                    _messages.update { it + aiMsg }
                }.onFailure { err ->
                    val friendlyMsg = "Engine Error (${engine.engineName}): ${err.localizedMessage ?: "Service unavailable."}"
                    _messages.update { it + ChatUiMessage(sender = MessageSender.SYSTEM, text = friendlyMsg) }
                }
            } catch (e: Exception) {
                _messages.update {
                    it + ChatUiMessage(sender = MessageSender.SYSTEM, text = "Error: ${e.localizedMessage ?: "Audio execution failed."}")
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private val pendingItineraryPlans = mutableMapOf<String, Pair<String, com.example.malaysiaitinerary.ai.StructuredItineraryPlan>>()

    fun saveEntryToDatabase(
        messageId: String,
        title: String,
        location: String,
        date: String,
        time: String,
        description: String,
        type: String = "ACTIVITY"
    ) {
        viewModelScope.launch {
            val pendingPlan = pendingItineraryPlans[messageId]
            if (pendingPlan != null) {
                val (destination, plan) = pendingPlan
                val startDateStr = plan.days.firstOrNull()?.date ?: java.time.LocalDate.now().plusDays(1).toString()
                val endDateStr = plan.days.lastOrNull()?.date ?: java.time.LocalDate.now().plusDays(plan.days.size.toLong()).toString()
                val tripName = "$destination Escape"

                tripRepository?.createNewTrip(
                    name = tripName,
                    destination = destination,
                    startDate = startDateStr,
                    endDate = endDateStr,
                    isSample = false
                )

                plan.days.forEach { day ->
                    day.items.forEach { item ->
                        toolHandler.executeToolCall(
                            AiToolCall(
                                toolName = "addItineraryItem",
                                arguments = mapOf(
                                    "date" to day.date,
                                    "title" to item.title,
                                    "location" to item.locationName,
                                    "startTime" to item.startTime,
                                    "type" to item.type,
                                    "description" to item.description
                                )
                            )
                        )
                    }
                }
                pendingItineraryPlans.remove(messageId)
            } else {
                val item = com.example.malaysiaitinerary.data.local.entity.ItineraryItem(
                    date = date.ifBlank { java.time.LocalDate.now().toString() },
                    title = title.ifBlank { "New Itinerary / Diary Entry" },
                    locationName = location.ifBlank { "Malaysia" },
                    startTime = time.ifBlank { "09:00" },
                    description = description,
                    type = type
                )
                itineraryRepository.insertItem(item)
            }

            _messages.update { list ->
                list.map { msg ->
                    if (msg.id == messageId) msg.copy(isSavedToDatabase = true) else msg
                }
            }
        }
    }

    fun discardMessage(messageId: String) {
        pendingItineraryPlans.remove(messageId)
        _messages.update { list ->
            list.filterNot { it.id == messageId }
        }
    }

    fun parseAndImportDocument(file: File, mimeType: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val engine = getActiveEngine()
                val result = engine.parseDocument(file, mimeType)

                result.onSuccess { doc ->
                    _extractedResult.value = doc

                    val autoDocTool = AiToolCall(
                        toolName = "addDocument",
                        arguments = mapOf(
                            "fileName" to file.name,
                            "filePath" to file.absolutePath,
                            "type" to doc.type,
                            "date" to (doc.date ?: "2026-03-25"),
                            "location" to (doc.location ?: "Imported Vault")
                        )
                    )
                    toolHandler.executeToolCall(autoDocTool)

                    if (doc.expenseAmount != null && doc.expenseAmount > 0) {
                        val expenseTool = AiToolCall(
                            toolName = "addExpense",
                            arguments = mapOf(
                                "amount" to doc.expenseAmount.toString(),
                                "currency" to (doc.currency ?: "MYR"),
                                "category" to doc.type,
                                "date" to (doc.date ?: "2026-03-25"),
                                "note" to doc.title
                            )
                        )
                        toolHandler.executeToolCall(expenseTool)
                    }

                    val msg = ChatUiMessage(
                        sender = MessageSender.AI,
                        text = "Successfully parsed document '${file.name}' using ${engine.engineName}!\n\nExtracted details:\n- Title: ${doc.title}\n- Category: ${doc.type}\n- Date: ${doc.date ?: "N/A"}\n- Location: ${doc.location ?: "N/A"}",
                        toolExecutions = listOf("Saved document to Briefcase Vault", if (doc.expenseAmount != null) "Logged expense of ${doc.expenseAmount} ${doc.currency ?: ""}" else "").filter { it.isNotBlank() }
                    )
                    _messages.update { it + msg }
                }.onFailure { err ->
                    _messages.update {
                        it + ChatUiMessage(sender = MessageSender.SYSTEM, text = "Failed to parse document: ${err.localizedMessage}")
                    }
                }
            } catch (e: Exception) {
                _messages.update {
                    it + ChatUiMessage(sender = MessageSender.SYSTEM, text = "Document Error: ${e.localizedMessage}")
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun generateNewTrip(destination: String, days: Int, preferences: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val engine = getActiveEngine()
                val result = engine.generateItinerary(destination, days, preferences, isSearchEnabled.first())

                result.onSuccess { plan ->
                    val startDateStr = plan.days.firstOrNull()?.date ?: java.time.LocalDate.now().plusDays(1).toString()
                    val endDateStr = plan.days.lastOrNull()?.date ?: java.time.LocalDate.now().plusDays(days.toLong()).toString()

                    val formattedPlanText = buildString {
                        appendLine("Proposed $days-Day Trip Plan for $destination ($startDateStr to $endDateStr)\n")
                        plan.days.forEach { day ->
                            appendLine("Day ${day.dayNumber} (${day.date}):")
                            day.items.forEach { item ->
                                appendLine("  • ${item.startTime} - ${item.title} (${item.type}) @ ${item.locationName}")
                                if (!item.description.isNullOrBlank()) {
                                    appendLine("    ${item.description}")
                                }
                            }
                            appendLine()
                        }
                        appendLine("Review the plan above. Click 'Save to App' below to confirm and insert this trip into your database.")
                    }

                    val messageId = java.util.UUID.randomUUID().toString()
                    pendingItineraryPlans[messageId] = destination to plan

                    val msg = ChatUiMessage(
                        id = messageId,
                        sender = MessageSender.AI,
                        text = formattedPlanText,
                        hasActionableContent = true,
                        isSavedToDatabase = false
                    )
                    _messages.update { it + msg }
                }.onFailure { err ->
                    _messages.update {
                        it + ChatUiMessage(sender = MessageSender.SYSTEM, text = "Failed to generate itinerary: ${err.localizedMessage}")
                    }
                }
            } catch (e: Exception) {
                _messages.update {
                    it + ChatUiMessage(sender = MessageSender.SYSTEM, text = "Generator Error: ${e.localizedMessage}")
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun resetItineraryAndData(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val database = com.example.malaysiaitinerary.data.local.AppDatabase.getDatabase(context, viewModelScope)
            database.reseedDemoData(context)
            _messages.value = emptyList()
        }
    }
}

class AiAssistantViewModelFactory(
    private val context: Context,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val itineraryRepository: ItineraryRepository,
    private val documentRepository: DocumentRepository,
    private val expenseRepository: ExpenseRepository,
    private val tripRepository: TripRepository? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AiAssistantViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AiAssistantViewModel(context, aiPreferencesRepository, itineraryRepository, documentRepository, expenseRepository, tripRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
