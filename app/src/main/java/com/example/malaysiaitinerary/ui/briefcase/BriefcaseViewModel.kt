package com.example.malaysiaitinerary.ui.briefcase

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.malaysiaitinerary.data.local.entity.Document
import com.example.malaysiaitinerary.data.repository.DocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import com.example.malaysiaitinerary.util.ZipUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import com.example.malaysiaitinerary.data.repository.ItineraryRepository
import androidx.core.content.FileProvider
import com.example.malaysiaitinerary.data.local.AppDatabase
import com.example.malaysiaitinerary.util.SyncManager
import kotlinx.coroutines.withContext

class BriefcaseViewModel(
    private val repository: DocumentRepository,
    private val itineraryRepository: ItineraryRepository,
    private val database: AppDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow<BriefcaseUiState>(BriefcaseUiState.Loading)
    val uiState: StateFlow<BriefcaseUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var rawDocuments: List<Document> = emptyList()

    private val _importStatus = MutableStateFlow<ImportStatus>(ImportStatus.Idle)
    val importStatus: StateFlow<ImportStatus> = _importStatus.asStateFlow()

    init {
        loadDocuments()
    }

    private var hasLinkedOnce = false

    private var lastUpdateTime: Long = System.currentTimeMillis()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        applySearchFilter()
    }

    private fun loadDocuments() {
        viewModelScope.launch {
            repository.allDocuments.collect { docs ->
                rawDocuments = docs
                applySearchFilter()
                linkExistingDocuments(docs)
            }
        }
    }

    private fun applySearchFilter() {
        val q = _searchQuery.value.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            rawDocuments
        } else {
            rawDocuments.filter { doc ->
                doc.fileName.lowercase().contains(q) ||
                doc.type.lowercase().contains(q) ||
                (doc.location?.lowercase()?.contains(q) == true) ||
                (doc.date?.lowercase()?.contains(q) == true) ||
                fuzzyMatch(doc.fileName, q) ||
                fuzzyMatch(doc.type, q)
            }
        }
        _uiState.value = BriefcaseUiState.Success(
            documents = filtered,
            totalFiles = rawDocuments.size,
            recentSync = getFormattedRecentSync(),
            isFiltered = q.isNotEmpty()
        )
    }

    private fun getFormattedRecentSync(): String {
        val diff = System.currentTimeMillis() - lastUpdateTime
        val minutes = (diff / (1000 * 60)).toInt()
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            else -> "${minutes / 1440}d ago"
        }
    }

    private suspend fun linkExistingDocuments(documents: List<Document>) {
        if (hasLinkedOnce) return
        val unlinked = documents.filter { it.itineraryItemId == null && it.date != null && it.location != null && it.type != "OTHERS" }
        if (unlinked.isEmpty()) return
        
        hasLinkedOnce = true
        unlinked.forEach { doc ->
            val date = doc.date!!
            val location = doc.location!!
            val items = itineraryRepository.getItemsByDate(date).first()
            val matchedItem = items.find { 
                it.locationName.equals(location, ignoreCase = true) || it.title.equals(location, ignoreCase = true) 
            }
            if (matchedItem != null) {
                repository.insertDocument(doc.copy(itineraryItemId = matchedItem.id))
            }
        }
    }

    private fun fuzzyMatch(s1: String, s2: String): Boolean {
        val clean1 = s1.lowercase().replace(Regex("[^a-z0-9]"), "")
        val clean2 = s2.lowercase().replace(Regex("[^a-z0-9]"), "")
        if (clean1.isEmpty() || clean2.isEmpty()) return false
        return clean1.contains(clean2) || clean2.contains(clean1)
    }

    fun uploadDocument(context: Context, uri: Uri, fileName: String, type: String, date: String?, location: String?, itineraryItemId: Int? = null, description: String? = null) {
        viewModelScope.launch {
            try {
                val activeTrip = database.tripDao().getActiveTripSync()
                val activeTripId = activeTrip?.id ?: 1L

                val finalFileName = if (type == "OTHERS" && description != null) {
                    "$description.pdf"
                } else {
                    fileName
                }
                
                val inputStream = context.contentResolver.openInputStream(uri)
                val file = File(context.filesDir, finalFileName)
                val outputStream = FileOutputStream(file)
                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                
                var matchedItemId: Int? = itineraryItemId
                if (matchedItemId == null && date != null && location != null) {
                    val items = itineraryRepository.getItemsByDate(date).first()
                    matchedItemId = items.find { 
                        fuzzyMatch(it.title, location) || fuzzyMatch(it.locationName, location)
                    }?.id
                }

                val document = Document(
                    fileName = finalFileName,
                    filePath = file.absolutePath,
                    type = type,
                    date = date,
                    location = location,
                    itineraryItemId = matchedItemId,
                    tripId = activeTripId
                )
                repository.insertDocument(document)
                lastUpdateTime = System.currentTimeMillis()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch {
            val file = File(document.filePath)
            if (file.exists()) {
                file.delete()
            }
            repository.deleteDocument(document)
            lastUpdateTime = System.currentTimeMillis()
        }
    }

    fun exportDatabase(context: Context, onExportReady: (Uri) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbFile = context.getDatabasePath("itinerary_database")
                val shmFile = File(dbFile.absolutePath + "-shm")
                val walFile = File(dbFile.absolutePath + "-wal")
                val imagesDir = File(context.filesDir, "images")
                
                val filesToZip = mutableListOf<File>()
                if (dbFile.exists()) filesToZip.add(dbFile)
                if (shmFile.exists()) filesToZip.add(shmFile)
                if (walFile.exists()) filesToZip.add(walFile)
                if (imagesDir.exists()) filesToZip.add(imagesDir)
                
                // Add all files in filesDir (tickets, etc)
                context.filesDir.listFiles()?.forEach { 
                    if (it.isFile && it.name != "images") filesToZip.add(it)
                }

                val zipFile = File(context.cacheDir, "MalaysianItinerary_Backup.mitinerary")
                ZipUtil.zip(filesToZip, zipFile)
                
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
                withContext(Dispatchers.Main) {
                    onExportReady(uri)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun importDatabase(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _importStatus.value = ImportStatus.Loading
            try {
                val tempZip = File(context.cacheDir, "import_temp.zip")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempZip).use { output ->
                        input.copyTo(output)
                    }
                }

                val tempDir = File(context.cacheDir, "extracted")
                tempDir.deleteRecursively()
                tempDir.mkdirs()
                
                ZipUtil.unzip(tempZip, tempDir)
                
                // Overwrite DB files
                val dbFile = context.getDatabasePath("itinerary_database")
                val shmFile = File(dbFile.absolutePath + "-shm")
                val walFile = File(dbFile.absolutePath + "-wal")
                
                // Close DB connection (handled by app restart ideally, but here we just overwrite)
                // In a real app, you should close the Room instance first.
                
                copyFile(File(tempDir, "itinerary_database"), dbFile)
                copyFile(File(tempDir, "itinerary_database-shm"), shmFile)
                copyFile(File(tempDir, "itinerary_database-wal"), walFile)
                
                // Overwrite Media
                val imagesDir = File(context.filesDir, "images")
                val tempImages = File(tempDir, "images")
                if (tempImages.exists()) {
                    imagesDir.deleteRecursively()
                    tempImages.copyRecursively(imagesDir, overwrite = true)
                }
                
                // Overwrite other files in filesDir (tickets)
                tempDir.listFiles()?.forEach { 
                    if (it.isFile && !it.name.startsWith("itinerary_database")) {
                        copyFile(it, File(context.filesDir, it.name))
                    }
                }

                _importStatus.value = ImportStatus.Success
            } catch (e: Exception) {
                e.printStackTrace()
                _importStatus.value = ImportStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun copyFile(src: File, dst: File) {
        if (!src.exists()) return
        FileInputStream(src).use { input ->
            FileOutputStream(dst).use { output ->
                input.copyTo(output)
            }
        }
    }

    fun exportSyncData(context: Context) {
        viewModelScope.launch {
            SyncManager.exportSyncPackage(context, database)
        }
    }

    fun importSyncData(context: Context, jsonString: String) {
        viewModelScope.launch {
            SyncManager.importSyncPackage(context, jsonString, database)
            _importStatus.value = ImportStatus.Success
        }
    }
}

sealed class ImportStatus {
    object Idle : ImportStatus()
    object Loading : ImportStatus()
    object Success : ImportStatus()
    data class Error(val message: String) : ImportStatus()
}

sealed class BriefcaseUiState {
    object Loading : BriefcaseUiState()
    data class Success(
        val documents: List<Document>,
        val totalFiles: Int,
        val recentSync: String,
        val isFiltered: Boolean = false
    ) : BriefcaseUiState()
}

class BriefcaseViewModelFactory(
    private val repository: DocumentRepository,
    private val itineraryRepository: ItineraryRepository,
    private val database: AppDatabase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BriefcaseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BriefcaseViewModel(repository, itineraryRepository, database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
