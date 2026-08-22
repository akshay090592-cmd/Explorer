package com.example.malaysiaitinerary.ui.itinerary

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri
import kotlinx.coroutines.flow.first
import java.io.FileOutputStream
import java.io.FileInputStream
import com.example.malaysiaitinerary.util.ZipUtil
import androidx.core.content.FileProvider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.malaysiaitinerary.data.local.entity.ItineraryItem
import com.example.malaysiaitinerary.data.local.entity.Document
import com.example.malaysiaitinerary.data.repository.ItineraryRepository
import com.example.malaysiaitinerary.data.repository.DocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.example.malaysiaitinerary.util.MapsScraper
import com.example.malaysiaitinerary.util.FileUtil

import com.example.malaysiaitinerary.data.repository.TripRepository

class ItineraryViewModel(
    private val repository: ItineraryRepository,
    private val documentRepository: DocumentRepository,
    private val tripRepository: TripRepository? = null
) : ViewModel() {

    private val _selectedType = MutableStateFlow("All")
    val selectedType: StateFlow<String> = _selectedType.asStateFlow()

    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()

    private val _uiState = MutableStateFlow<ItineraryUiState>(ItineraryUiState.Loading)
    val uiState: StateFlow<ItineraryUiState> = _uiState.asStateFlow()

    private val _sharedMapsUrl = MutableStateFlow<String?>(null)
    val sharedMapsUrl: StateFlow<String?> = _sharedMapsUrl.asStateFlow()

    private val _fetchingDetails = MutableStateFlow(false)
    val fetchingDetails: StateFlow<Boolean> = _fetchingDetails.asStateFlow()

    private val _fetchedDetails = MutableStateFlow<MapsScraper.MapDetails?>(null)
    val fetchedDetails: StateFlow<MapsScraper.MapDetails?> = _fetchedDetails.asStateFlow()
    
    // Import/Export status
    private val _importStatus = MutableStateFlow<ImportStatus>(ImportStatus.Idle)
    val importStatus: StateFlow<ImportStatus> = _importStatus.asStateFlow()

    private val _exportStatus = MutableStateFlow<ExportStatus>(ExportStatus.Idle)
    val exportStatus: StateFlow<ExportStatus> = _exportStatus.asStateFlow()

    init {
        loadItinerary()
    }

    fun resetItinerary(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val database = com.example.malaysiaitinerary.data.local.AppDatabase.getDatabase(context, viewModelScope)
            database.reseedDemoData(context)
        }
    }

    private fun loadItinerary() {
        viewModelScope.launch {
            combine(repository.allItems, _selectedType) { items, type ->
                if (items.isEmpty()) {
                    ItineraryUiState.Empty
                } else {
                    val counts = calculateTypeCounts(items)
                    val filteredItems = if (type == "All") {
                        items
                    } else {
                        items.filter { 
                            when (type) {
                                "Flights" -> it.type == "TRANSIT"
                                "Hotels" -> it.type == "ACCOMMODATION"
                                "Activities" -> it.type == "ACTIVITY"
                                else -> true
                            }
                        }
                    }
                    val groupedItems = filteredItems.groupBy { it.date }
                    ItineraryUiState.Success(groupedItems, counts)
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun calculateTypeCounts(items: List<ItineraryItem>): Map<String, Int> {
        return mapOf(
            "Flights" to items.count { it.type == "TRANSIT" },
            "Hotels" to items.count { it.type == "ACCOMMODATION" },
            "Activities" to items.count { it.type == "ACTIVITY" }
        )
    }

    fun setFilterType(type: String) {
        _selectedType.value = type
    }

    fun setSelectedDate(date: String?) {
        _selectedDate.value = date
    }

    fun handleSharedUrl(url: String) {
        _sharedMapsUrl.value = url
        fetchDetails(url)
    }

    fun clearSharedUrl() {
        _sharedMapsUrl.value = null
        _fetchedDetails.value = null
    }

    private fun fetchDetails(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _fetchingDetails.value = true
            val details = MapsScraper.fetchDetails(url)
            _fetchedDetails.value = details
            _fetchingDetails.value = false
        }
    }

    fun addCustomActivity(
        context: Context,
        date: String,
        title: String,
        location: String,
        startTime: String,
        description: String,
        googleMapsUrl: String,
        imageUrl: String? = null,
        ticketUri: Uri? = null
    ) {
        viewModelScope.launch {
            val activeTripId = tripRepository?.getActiveTripSync()?.id ?: 1L
            val localImageUrl = if (imageUrl != null && imageUrl.startsWith("http")) {
                withContext(Dispatchers.IO) {
                    FileUtil.downloadImage(context, imageUrl)
                }
            } else {
                imageUrl
            }

            val newItem = ItineraryItem(
                date = date,
                title = title,
                locationName = location,
                startTime = startTime,
                description = description,
                type = "ACTIVITY",
                googleMapsUrl = googleMapsUrl,
                imageUrl = localImageUrl ?: "https://images.unsplash.com/photo-1596422846543-75c6fc18a593",
                tripId = activeTripId
            )
            val itemId = repository.insertItem(newItem).toInt()

            if (ticketUri != null) {
                withContext(Dispatchers.IO) {
                    val contentResolver = context.contentResolver
                    val fileName = "ticket_${System.currentTimeMillis()}" // Simple naming
                    val internalFile = File(context.filesDir, fileName)
                    
                    contentResolver.openInputStream(ticketUri)?.use { input ->
                        FileOutputStream(internalFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    val document = Document(
                        fileName = fileName,
                        filePath = internalFile.absolutePath,
                        type = "TICKET",
                        date = date,
                        location = location,
                        itineraryItemId = itemId,
                        tripId = activeTripId
                    )
                    documentRepository.insertDocument(document)
                }
            }
            
            clearSharedUrl()
        }
    }

    fun updateActivity(item: ItineraryItem) {
        viewModelScope.launch {
            repository.updateItem(item)
        }
    }

    fun addTicketToActivity(context: Context, itemId: Int, ticketUri: Uri) {
        viewModelScope.launch {
            val activeTripId = tripRepository?.getActiveTripSync()?.id ?: 1L
            withContext(Dispatchers.IO) {
                try {
                    val contentResolver = context.contentResolver
                    val fileName = "ticket_${System.currentTimeMillis()}.pdf"
                    val internalFile = File(context.filesDir, fileName)
                    
                    contentResolver.openInputStream(ticketUri)?.use { input ->
                        FileOutputStream(internalFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    val document = Document(
                        fileName = fileName,
                        filePath = internalFile.absolutePath,
                        type = "TICKET",
                        date = null,
                        location = null,
                        itineraryItemId = itemId,
                        tripId = activeTripId
                    )
                    documentRepository.insertDocument(document)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun deleteActivity(item: ItineraryItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    fun resetExportStatus() {
        _exportStatus.value = ExportStatus.Idle
    }

    fun exportDatabase(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _exportStatus.value = ExportStatus.Loading
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
                    _exportStatus.value = ExportStatus.Success(uri)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _exportStatus.value = ExportStatus.Error(e.message ?: "Unknown error")
                }
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
}

sealed class ImportStatus {
    object Idle : ImportStatus()
    object Loading : ImportStatus()
    object Success : ImportStatus()
    data class Error(val message: String) : ImportStatus()
}

sealed class ExportStatus {
    object Idle : ExportStatus()
    object Loading : ExportStatus()
    data class Success(val uri: Uri) : ExportStatus()
    data class Error(val message: String) : ExportStatus()
}

sealed class ItineraryUiState {
    object Loading : ItineraryUiState()
    object Empty : ItineraryUiState()
    data class Success(
        val groupedItems: Map<String, List<ItineraryItem>>,
        val typeCounts: Map<String, Int>
    ) : ItineraryUiState()
}

class ItineraryViewModelFactory(
    private val repository: ItineraryRepository,
    private val documentRepository: DocumentRepository,
    private val tripRepository: TripRepository? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ItineraryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ItineraryViewModel(repository, documentRepository, tripRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
