package com.example.malaysiaitinerary.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.material.icons.filled.CardTravel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Welcome : Screen("welcome", "Welcome", Icons.Default.TravelExplore)
    object Onboarding : Screen("onboarding", "Onboarding", Icons.Default.TravelExplore)
    object Dashboard : Screen("dashboard", "Home", Icons.Default.Home)
    object Itinerary : Screen("itinerary", "Itinerary", Icons.Default.EventNote)
    object MyTrips : Screen("my_trips", "My Trips", Icons.Default.CardTravel)
    object AiAssistant : Screen("ai_assistant", "Gemma", Icons.Default.AutoAwesome)
    object Expense : Screen("expense", "Expenses", Icons.Default.Payments)
    object Briefcase : Screen("briefcase", "Briefcase", Icons.Default.Work)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Emergency : Screen("emergency", "Emergency", Icons.Default.Emergency)

    companion object {
        val items = listOf(Dashboard, Itinerary, AiAssistant, Expense, Briefcase)
    }
}
