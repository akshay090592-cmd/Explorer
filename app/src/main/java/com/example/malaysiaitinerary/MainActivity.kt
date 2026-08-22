package com.example.malaysiaitinerary

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.malaysiaitinerary.data.local.AppDatabase
import com.example.malaysiaitinerary.data.repository.DocumentRepository
import com.example.malaysiaitinerary.data.repository.ItineraryRepository
import com.example.malaysiaitinerary.data.repository.ExpenseRepository
import com.example.malaysiaitinerary.ui.briefcase.BriefcaseScreen
import com.example.malaysiaitinerary.ui.briefcase.BriefcaseViewModel
import com.example.malaysiaitinerary.ui.briefcase.BriefcaseViewModelFactory
import com.example.malaysiaitinerary.ui.expense.ExpenseScreen
import com.example.malaysiaitinerary.ui.expense.ExpenseViewModel
import com.example.malaysiaitinerary.ui.expense.ExpenseViewModelFactory
import com.example.malaysiaitinerary.ui.emergency.EmergencyScreen
import com.example.malaysiaitinerary.ui.dashboard.DashboardScreen
import com.example.malaysiaitinerary.ui.dashboard.DashboardViewModel
import com.example.malaysiaitinerary.ui.dashboard.DashboardViewModelFactory
import com.example.malaysiaitinerary.ui.itinerary.ItineraryScreen
import com.example.malaysiaitinerary.ui.itinerary.ItineraryViewModel
import com.example.malaysiaitinerary.ui.itinerary.ItineraryViewModelFactory
import com.example.malaysiaitinerary.data.repository.AiPreferencesRepository
import com.example.malaysiaitinerary.ui.ai.AiAssistantScreen
import com.example.malaysiaitinerary.ui.ai.AiAssistantViewModel
import com.example.malaysiaitinerary.ui.ai.AiAssistantViewModelFactory
import com.example.malaysiaitinerary.ui.settings.SettingsScreen
import com.example.malaysiaitinerary.ui.settings.SettingsViewModel
import com.example.malaysiaitinerary.ui.settings.SettingsViewModelFactory
import com.example.malaysiaitinerary.ui.navigation.Screen
import com.example.malaysiaitinerary.ui.theme.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.malaysiaitinerary.ui.welcome.WelcomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val database = AppDatabase.getDatabase(applicationContext, MainScope())
        val tripRepository = com.example.malaysiaitinerary.data.repository.TripRepository(
            database.tripDao(),
            database.itineraryDao(),
            database.expenseDao(),
            database.documentDao()
        )
        val itineraryRepository = ItineraryRepository(database.itineraryDao(), database.tripDao())
        val documentRepository = DocumentRepository(database.documentDao(), database.tripDao())
        val expenseRepository = ExpenseRepository(database.expenseDao(), database.tripDao())
        val aiPreferencesRepository = AiPreferencesRepository(applicationContext)

        MainScope().launch(Dispatchers.IO) {
            val totalCount = database.itineraryDao().getItemCount()
            if (totalCount == 0) {
                database.reseedDemoData(applicationContext)
            }
        }

        val myTripsViewModelFactory = com.example.malaysiaitinerary.ui.trips.MyTripsViewModelFactory(tripRepository)
        val itineraryViewModelFactory = ItineraryViewModelFactory(itineraryRepository, documentRepository, tripRepository)
        val briefcaseViewModelFactory = BriefcaseViewModelFactory(documentRepository, itineraryRepository, database)
        val expenseViewModelFactory = ExpenseViewModelFactory(expenseRepository, tripRepository)
        val dashboardViewModelFactory = DashboardViewModelFactory(itineraryRepository, documentRepository, tripRepository)
        val aiAssistantViewModelFactory = AiAssistantViewModelFactory(applicationContext, aiPreferencesRepository, itineraryRepository, documentRepository, expenseRepository, tripRepository)
        val settingsViewModelFactory = SettingsViewModelFactory(aiPreferencesRepository)

        setContent {
            val itineraryViewModel: ItineraryViewModel = viewModel(factory = itineraryViewModelFactory)
            val briefcaseViewModel: BriefcaseViewModel = viewModel(factory = briefcaseViewModelFactory)
            val expenseViewModel: ExpenseViewModel = viewModel(factory = expenseViewModelFactory)
            
            val isFirstTimeUser by aiPreferencesRepository.isFirstTimeUser.collectAsState(initial = false)
            val nonDemoCountState by itineraryRepository.getNonDemoCountFlow().collectAsState(initial = 0)
            var isCreatingNewTrip by remember { mutableStateOf(false) }
            var currentIntent by remember { mutableStateOf(intent) }

            LaunchedEffect(currentIntent) {
                handleIntent(currentIntent, itineraryViewModel, briefcaseViewModel)
            }
            
            this.onIntentReceived = { newIntent ->
                currentIntent = newIntent
            }

            MalaysiaItineraryTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                val showBottomBar = currentDestination?.route != Screen.Welcome.route && 
                    currentDestination?.route != Screen.Onboarding.route &&
                    !(currentDestination?.route == Screen.AiAssistant.route && isCreatingNewTrip && nonDemoCountState == 0)

                val haptic = LocalHapticFeedback.current

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface,
                                shape = androidx.compose.ui.graphics.RectangleShape
                            ) {
                                Column {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), 
                                        thickness = 1.dp
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                            .navigationBarsPadding(),
                                        horizontalArrangement = Arrangement.SpaceAround,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Screen.items.forEach { screen ->
                                            val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                                            val scale by animateFloatAsState(
                                                targetValue = if (isSelected) 1.05f else 1.0f,
                                                animationSpec = tween(200),
                                                label = "nav_scale"
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .graphicsLayer(scaleX = scale, scaleY = scale)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) 
                                                        else Color.Transparent
                                                    )
                                                    .clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        navController.navigate(screen.route) {
                                                            popUpTo(navController.graph.findStartDestination().id) {
                                                                saveState = true
                                                            }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(
                                                        imageVector = screen.icon,
                                                        contentDescription = null,
                                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                    Text(
                                                        text = screen.title,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 11.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                        ),
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController, 
                        startDestination = if (isFirstTimeUser) Screen.Onboarding.route else Screen.Welcome.route,
                        modifier = Modifier.padding(bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp)
                    ) {
                        composable(Screen.Onboarding.route) {
                            com.example.malaysiaitinerary.ui.onboarding.IntroOnboardingScreen(
                                onOnboardingComplete = {
                                    MainScope().launch {
                                        aiPreferencesRepository.setFirstTimeUserCompleted(true)
                                    }
                                    navController.navigate(Screen.Welcome.route) {
                                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(Screen.Welcome.route) {
                            WelcomeScreen(
                                onCreateTripWithAi = {
                                    isCreatingNewTrip = true
                                    navController.navigate(Screen.AiAssistant.route)
                                },
                                onExploreSampleTrip = {
                                    isCreatingNewTrip = false
                                    navController.navigate(Screen.Dashboard.route) {
                                        popUpTo(Screen.Welcome.route) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(Screen.Dashboard.route) {
                            val dashboardViewModel: DashboardViewModel = viewModel(
                                factory = dashboardViewModelFactory
                            )
                            DashboardScreen(
                                viewModel = dashboardViewModel,
                                onNavigateToItinerary = {
                                    navController.navigate(Screen.Itinerary.route)
                                },
                                onNavigateToAiAssistant = {
                                    isCreatingNewTrip = true
                                    navController.navigate(Screen.AiAssistant.route)
                                },
                                onNavigateToMyTrips = {
                                    navController.navigate(Screen.MyTrips.route)
                                },
                                onNavigateToBriefcase = {
                                    navController.navigate(Screen.Briefcase.route)
                                }
                            )
                        }
                        composable(Screen.Itinerary.route) {
                            ItineraryScreen(
                                viewModel = itineraryViewModel,
                                briefcaseViewModel = briefcaseViewModel,
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable(Screen.MyTrips.route) {
                            val myTripsViewModel: com.example.malaysiaitinerary.ui.trips.MyTripsViewModel = viewModel(
                                factory = myTripsViewModelFactory
                            )
                            com.example.malaysiaitinerary.ui.trips.MyTripsScreen(
                                viewModel = myTripsViewModel,
                                onCreateNewTrip = {
                                    isCreatingNewTrip = true
                                    navController.navigate(Screen.AiAssistant.route)
                                },
                                onOpenActiveTrip = {
                                    navController.navigate(Screen.Dashboard.route)
                                }
                            )
                        }
                        composable(Screen.AiAssistant.route) {
                            val aiAssistantViewModel: AiAssistantViewModel = viewModel(
                                factory = aiAssistantViewModelFactory
                            )
                            AiAssistantScreen(
                                viewModel = aiAssistantViewModel,
                                onNavigateToSettings = {
                                    navController.navigate(Screen.Settings.route)
                                },
                                onExploreSampleTrip = {
                                    isCreatingNewTrip = false
                                    navController.navigate(Screen.Dashboard.route)
                                }
                            )
                        }
                        composable(Screen.Settings.route) {
                            val settingsViewModel: SettingsViewModel = viewModel(
                                factory = settingsViewModelFactory
                            )
                            val aiAssistantViewModel: AiAssistantViewModel = viewModel(
                                factory = aiAssistantViewModelFactory
                            )
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                aiViewModel = aiAssistantViewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable(Screen.Briefcase.route) {
                            BriefcaseScreen(viewModel = briefcaseViewModel)
                        }
                        composable(Screen.Expense.route) {
                            ExpenseScreen(viewModel = expenseViewModel)
                        }
                        composable(Screen.Emergency.route) {
                            EmergencyScreen()
                        }
                    }
                }
            }
        }
    }

    var onIntentReceived: ((Intent) -> Unit)? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        onIntentReceived?.invoke(intent)
    }

    private fun handleIntent(intent: Intent?, itineraryViewModel: ItineraryViewModel, briefcaseViewModel: BriefcaseViewModel) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_SEND -> {
                if ("text/plain" == intent.type) {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (sharedText != null) {
                        itineraryViewModel.handleSharedUrl(sharedText)
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                val data: Uri? = intent.data
                if (data != null) {
                    if (data.toString().endsWith(".json")) {
                        contentResolver.openInputStream(data)?.bufferedReader()?.use { reader ->
                            briefcaseViewModel.importSyncData(this, reader.readText())
                        }
                    } else {
                        briefcaseViewModel.importDatabase(this, data)
                    }
                }
            }
        }
    }
}
