package com.example.malaysiaitinerary.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.malaysiaitinerary.data.local.entity.Document
import com.example.malaysiaitinerary.data.local.entity.ItineraryItem
import com.example.malaysiaitinerary.data.repository.DocumentRepository
import com.example.malaysiaitinerary.data.repository.ItineraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.Duration
import java.time.format.DateTimeFormatter

import com.example.malaysiaitinerary.data.repository.TripRepository

class DashboardViewModel(
    private val itineraryRepository: ItineraryRepository,
    private val documentRepository: DocumentRepository,
    private val tripRepository: TripRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // State for Meal Options Bottom Sheet
    private val _selectedMeal = MutableStateFlow<ItineraryItem?>(null)
    val selectedMeal: StateFlow<ItineraryItem?> = _selectedMeal.asStateFlow()

    private val _isMealSheetVisible = MutableStateFlow(false)
    val isMealSheetVisible: StateFlow<Boolean> = _isMealSheetVisible.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(Unit)

    private val _daysOffset = MutableStateFlow(0L)
    val daysOffset: StateFlow<Long> = _daysOffset.asStateFlow()


    // Ticker flow that emits every minute to refresh the UI (timer, current activity)
    private val tickerFlow = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(60_000) // Update every minute
        }
    }

    init {
        loadDashboardData()
    }

    private fun loadDashboardData() {
        viewModelScope.launch {
            val activeTripFlow = tripRepository?.activeTrip ?: kotlinx.coroutines.flow.flowOf(null)

            val dataFlow = combine(activeTripFlow, itineraryRepository.allItems, documentRepository.allDocuments) { activeTrip, allItems, allDocs ->
                Triple(activeTrip, allItems, allDocs)
            }

            combine(
                dataFlow,
                tickerFlow,
                _refreshTrigger,
                _daysOffset
            ) { (activeTrip, allItems, allDocs), _, _, offset ->
                val activeTripId = activeTrip?.id ?: 1L
                val items = allItems.filter { it.tripId == activeTripId }
                val docs = allDocs.filter { it.tripId == activeTripId }

                if (items.isEmpty()) {
                    DashboardUiState.Empty
                } else {
                    val now = LocalDateTime.now().plusDays(offset)
                    val nowDate = now.toLocalDate()
                    
                    
                    // Find trip start date and first item
                    val sortedItems = items.sortedWith(compareBy({ it.date }, { it.startTime }))
                    val firstItem = sortedItems.first()
                    val tripStartDate = LocalDate.parse(firstItem.date)
                    val tripStartDateTime = LocalDateTime.of(tripStartDate, LocalTime.parse(firstItem.startTime))
                    
                    val lastItem = sortedItems.last()
                    val tripEndDate = LocalDate.parse(lastItem.date)
                    
                    val effectiveDate = if (nowDate.isBefore(tripStartDate)) {
                        tripStartDate
                    } else if (nowDate.isAfter(tripEndDate)) {
                        tripEndDate
                    } else {
                        nowDate
                    }

                    val dateString = effectiveDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val todayItems = items.filter { it.date == dateString }
                    
                    // Calculate countdown if before trip start
                    var countdownMessage: String? = null
                    if (now.isBefore(tripStartDateTime)) {
                        val duration = Duration.between(now, tripStartDateTime)
                        val days = duration.toDays()
                        val hours = duration.toHours() % 24
                        val minutes = duration.toMinutes() % 60
                        
                        countdownMessage = if (days > 0) {
                            "$days days and $hours hours left"
                        } else if (hours > 0) {
                            "$hours hours and $minutes minutes left"
                        } else {
                            "$minutes minutes left"
                        }
                    }

                    // If before trip start date, show all items of the first day as upcoming
                    val currentTime = if (nowDate.isBefore(tripStartDate)) {
                        LocalTime.MIN
                    } else if (nowDate.isAfter(tripEndDate)) {
                        LocalTime.MAX
                    } else {
                        now.toLocalTime()
                    }
                    
                    var currentActivity: ItineraryItem? = null
                    val upcomingActivities = mutableListOf<ItineraryItem>()
                    val previousActivities = mutableListOf<ItineraryItem>()

                    todayItems.forEach { item ->
                        val startTime = try { LocalTime.parse(item.startTime) } catch (e: Exception) { LocalTime.MIN }
                        val endTime = item.endTime?.let { try { LocalTime.parse(it) } catch (e: Exception) { startTime.plusHours(2) } } ?: startTime.plusHours(2)

                        if (currentTime.isAfter(startTime) && currentTime.isBefore(endTime)) {
                            currentActivity = item
                        } else if (currentTime.isBefore(startTime)) {
                            upcomingActivities.add(item)
                        } else {
                            previousActivities.add(item)
                        }
                    }

                    // Relevant documents for today (Flights, Hotels, or linked to current activity) + All IDs
                    val relevantDocs = docs.filter { doc ->
                        doc.date == dateString || 
                        (currentActivity != null && doc.itineraryItemId == currentActivity?.id) ||
                        doc.type == "ID"
                    }

                    DashboardUiState.Success(
                        previousActivities = previousActivities,
                        currentActivity = currentActivity,
                        upcomingActivities = upcomingActivities,
                        relevantDocuments = relevantDocs,
                        selectedDate = effectiveDate,
                        tripStartDate = tripStartDate,
                        tripEndDate = tripEndDate,
                        countdownMessage = countdownMessage,
                        daysOffset = offset,
                        tripDestination = activeTrip?.destination ?: "Malaysia",
                        tripName = activeTrip?.name ?: "Malaysia Trip"
                    )
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }


    fun refresh() {
        _refreshTrigger.value = Unit
    }

    fun showMealOptions(item: ItineraryItem) {
        if (item.type == "MEAL") {
            _selectedMeal.value = item
            _isMealSheetVisible.value = true
        }
    }

    fun dismissMealOptions() {
        _isMealSheetVisible.value = false
    }

    fun nextDay() = viewModelScope.launch {
        _daysOffset.value += 1
    }

    fun resetDate() = viewModelScope.launch {
        _daysOffset.value = 0
    }
}

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    object Empty : DashboardUiState()
    data class Success(
        val previousActivities: List<ItineraryItem>,
        val currentActivity: ItineraryItem?,
        val upcomingActivities: List<ItineraryItem>,
        val relevantDocuments: List<Document> = emptyList(),
        val selectedDate: LocalDate,
        val tripStartDate: LocalDate,
        val tripEndDate: LocalDate,
        val countdownMessage: String? = null,
        val daysOffset: Long = 0,
        val tripDestination: String = "Malaysia",
        val tripName: String = "Malaysia Trip"
    ) : DashboardUiState()
}

class DashboardViewModelFactory(
    private val itineraryRepository: ItineraryRepository,
    private val documentRepository: DocumentRepository,
    private val tripRepository: TripRepository? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(itineraryRepository, documentRepository, tripRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
