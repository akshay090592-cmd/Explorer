package com.example.malaysiaitinerary.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.malaysiaitinerary.data.local.entity.Trip
import com.example.malaysiaitinerary.data.repository.TripRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MyTripsViewModel(private val tripRepository: TripRepository) : ViewModel() {

    val allTrips: StateFlow<List<Trip>> = tripRepository.allTrips.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val activeTrip: StateFlow<Trip?> = tripRepository.activeTrip.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    fun setActiveTrip(tripId: Long) {
        viewModelScope.launch {
            tripRepository.setActiveTrip(tripId)
        }
    }

    fun resumeTripWithNewStartDate(tripId: Long, newStartDate: String) {
        viewModelScope.launch {
            tripRepository.resumeTripWithNewStartDate(tripId, newStartDate)
        }
    }

    fun createNewTrip(name: String, destination: String, startDate: String, endDate: String) {
        viewModelScope.launch {
            tripRepository.createNewTrip(name, destination, startDate, endDate, isSample = false)
        }
    }

    fun updateTripDetails(tripId: Long, name: String, destination: String, startDate: String, endDate: String) {
        viewModelScope.launch {
            tripRepository.updateTripDetails(tripId, name, destination, startDate, endDate)
        }
    }

    fun deleteTrip(tripId: Long) {
        viewModelScope.launch {
            tripRepository.deleteTrip(tripId)
        }
    }
}

class MyTripsViewModelFactory(private val tripRepository: TripRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MyTripsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MyTripsViewModel(tripRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
