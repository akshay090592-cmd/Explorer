package com.example.malaysiaitinerary.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.example.malaysiaitinerary.data.local.entity.Document
import com.example.malaysiaitinerary.data.local.entity.ItineraryItem
import com.example.malaysiaitinerary.ui.theme.*
import com.example.malaysiaitinerary.util.PdfUtils
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.malaysiaitinerary.R
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToItinerary: () -> Unit,
    onNavigateToAiAssistant: () -> Unit = {},
    onNavigateToMyTrips: () -> Unit = {},
    onNavigateToBriefcase: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val isMealSheetVisible by viewModel.isMealSheetVisible.collectAsState()
    val selectedMeal by viewModel.selectedMeal.collectAsState()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    val scrollState = rememberScrollState()

    // Refresh data when screen is resumed
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        containerColor = ExplorerBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color = ExplorerPrimary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Text(
                            "Explorer",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1).sp
                            ),
                            color = ExplorerPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onNavigateToMyTrips()
                        }
                    ) {
                        Icon(Icons.Default.CardTravel, contentDescription = "My Trips", tint = ExplorerPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (val state = uiState) {
                is DashboardUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is DashboardUiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        DashboardContent(
                            state = state,
                            onDocClick = { PdfUtils.openPdf(context, it.filePath) },
                            onViewMealOptions = { viewModel.showMealOptions(it) },
                            onNextDay = { viewModel.nextDay() },
                            onResetDate = { viewModel.resetDate() },
                            onNavigateToAiAssistant = onNavigateToAiAssistant,
                            onNavigateToBriefcase = onNavigateToBriefcase
                        )
                    }
                }
                is DashboardUiState.Empty -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No data available.")
                    }
                }
            }
        }
    }

    if (isMealSheetVisible && selectedMeal != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissMealOptions() },
            sheetState = sheetState
        ) {
            MealOptionsContent(selectedMeal!!)
        }
    }
}

@Composable
fun DashboardContent(
    state: DashboardUiState.Success,
    onDocClick: (Document) -> Unit,
    onViewMealOptions: (ItineraryItem) -> Unit,
    onNextDay: () -> Unit,
    onResetDate: () -> Unit,
    onNavigateToAiAssistant: () -> Unit = {},
    onNavigateToBriefcase: () -> Unit = {}
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }

    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        // Plan First Trip CTA Banner
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ExplorerPrimaryContainer.copy(alpha = 0.12f)),
            border = BorderStroke(1.dp, ExplorerPrimaryContainer.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .clickable { onNavigateToAiAssistant() }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = ExplorerPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ready to start planning your first trip?",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = ExplorerPrimaryContainer
                    )
                    Text(
                        text = "Click here to generate your custom itinerary with Gemma AI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = ExplorerPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Hero Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = "https://lh3.googleusercontent.com/aida-public/AB6AXuD8DTKtIdMtDHOsG14-XJvKpiFVZhTgSZmCUGLrxOc0zvB52dj8o0h0oUBy2aKtydUO50M89KSASpC2hmrnSbgTqo12OV_2f6FLiDxQL-tYV5YGVyyQ5MivNAbi9u2-JY6Sn6Qm4XGUEzNaBmVEGHsEq0H7AdR5AJpOasvYyB8nOWg1Cw63mDp1cSGBLAuOmfbQxCDQ_Cj8lYLxe0DbgJM2P7dDrZ1nauwr_KwrXb6KtvMEuWHbWsaE9eYsUtUqOVFTJpvMtU1IjxQ",
                    contentDescription = "Travel",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, ExplorerPrimary.copy(alpha = 0.9f)),
                                startY = 300f
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(24.dp)
                ) {
                    Text(
                        "DESTINATION",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                        color = ExplorerSecondaryContainer
                    )
                    Text(
                        state.tripDestination,
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black, fontSize = 48.sp, letterSpacing = (-2).sp),
                        color = Color.White
                    )
                    Text(
                        "${state.tripStartDate.format(dateFormatter)} - ${state.tripEndDate.format(dateFormatter)}",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(24.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "DEPARTURE IN",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                val message = state.countdownMessage ?: "0 days"
                                val value = message.split(" ").firstOrNull() ?: "0"
                                val unit = if (message.contains("day", ignoreCase = true)) " days" else " hours"
                                
                                Text(value, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                Text(unit, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 6.dp, start = 4.dp))
                                Spacer(Modifier.width(12.dp))
                                IconButton(onClick = onNextDay) {
                                    Icon(Icons.Default.SkipNext, contentDescription = "Next Day", tint = Color.White)
                                }
                                if (state.daysOffset != 0L) {
                                    IconButton(onClick = onResetDate) {
                                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }



        // Smart Shortcuts
        if (state.relevantDocuments.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Smart Shortcuts", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = ExplorerPrimary)
                TextButton(onClick = { onNavigateToBriefcase() }) {
                    Text("Manage", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = ExplorerSecondary)
                }
            }

            LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.relevantDocuments) { doc ->
                    ShortcutCard(doc.fileName, onDocClick = { onDocClick(doc) })
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // Activities
        val upcomingOnText = "Upcoming on ${state.selectedDate.format(DateTimeFormatter.ofPattern("MMM dd"))}"
        Text(upcomingOnText, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = ExplorerPrimary)
        Spacer(Modifier.height(16.dp))
        
        state.currentActivity?.let { activity ->
            DashboardActivityCardStyled(activity, onDocClick, onViewMealOptions, state.relevantDocuments)
            Spacer(Modifier.height(16.dp))
        }

        state.upcomingActivities.forEach { activity ->
            DashboardActivityCardStyled(activity, onDocClick, onViewMealOptions, state.relevantDocuments)
            Spacer(Modifier.height(16.dp))
        }
        
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
fun ShortcutCard(name: String, onDocClick: () -> Unit) {
    Card(
        modifier = Modifier.width(180.dp).clickable { onDocClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ExplorerSurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = ExplorerPrimaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = ExplorerSecondaryContainer)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(name, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ExplorerPrimary, maxLines = 1)
                Text("Tap to view", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = ExplorerOnSurfaceVariant)
            }
        }
    }
}

@Composable
fun DashboardActivityCardStyled(
    item: ItineraryItem,
    onDocClick: (Document) -> Unit,
    onViewMealOptions: (ItineraryItem) -> Unit,
    relevantDocuments: List<Document>
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val associatedDoc = relevantDocuments.find { it.itineraryItemId == item.id }
    
    var expanded by remember { mutableStateOf(false) }

    if (item.type == "MEAL") {
        Card(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ExplorerSurfaceContainerLow)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Restaurant, contentDescription = null, tint = ExplorerPrimary)
                        Column {
                            Text(item.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ExplorerPrimary)
                            Text(item.startTime, style = MaterialTheme.typography.labelSmall, color = ExplorerOnSurfaceVariant)
                        }
                    }
                    Icon(imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
                AnimatedVisibility(visible = expanded) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        if (item.locationName.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(14.dp), tint = ExplorerOnSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Text(item.locationName, style = MaterialTheme.typography.bodySmall, color = ExplorerOnSurfaceVariant)
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        if (item.imageUrl != null) {
                            AsyncImage(
                                model = item.imageUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(12.dp))
                        }

                        if (item.vegOptions != null) {
                            Text("Veg Options:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ExplorerSecondary)
                            Text(item.vegOptions, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                        }
                        if (item.nonVegOptions != null) {
                            Text("Non-Veg Options:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ExplorerTertiary)
                            Text(item.nonVegOptions, style = MaterialTheme.typography.bodySmall)
                        }
                        
                        if (item.googleMapsUrl.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { uriHandler.openUri(item.googleMapsUrl) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = ExplorerSecondaryContainer, contentColor = ExplorerOnSecondaryContainer)
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Maps", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { onViewMealOptions(item) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = ExplorerPrimary)
                        ) {
                            Text("View Full Menu", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ExplorerSurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column {
                if (item.imageUrl != null) {
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp).padding(4.dp)) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Surface(
                            modifier = Modifier.padding(12.dp),
                            color = ExplorerPrimary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                item.type.uppercase(Locale.getDefault()),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = ExplorerPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = ExplorerOnSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(item.startTime, style = MaterialTheme.typography.bodyMedium, color = ExplorerOnSurfaceVariant)
                    }
                    if (item.description.isNotEmpty()) {
                        Text(item.description, style = MaterialTheme.typography.bodySmall, color = ExplorerOnSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { associatedDoc?.let { onDocClick(it) } },
                            enabled = associatedDoc != null,
                            modifier = Modifier.weight(1f),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = ExplorerPrimary)
                        ) {
                            Icon(Icons.Default.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Ticket", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Button(
                            onClick = { if (item.googleMapsUrl.isNotEmpty()) uriHandler.openUri(item.googleMapsUrl) },
                            enabled = item.googleMapsUrl.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = ExplorerSecondaryContainer, contentColor = ExplorerOnSecondaryContainer)
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Maps", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MealOptionsContent(item: ItineraryItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = "Meal Options for ${item.title}",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 16.dp),
            color = ExplorerPrimary
        )
        
        if (item.vegOptions != null) {
            Text(
                "Veg Options:",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = ExplorerSecondary
            )
            Text(item.vegOptions, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
        }
        
        if (item.nonVegOptions != null) {
            Text(
                "Non-Veg Options:",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = ExplorerTertiary
            )
            Text(item.nonVegOptions, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
        }

        if (item.vegOptions == null && item.nonVegOptions == null) {
            Text("No specific meal options listed for this activity.")
        }
    }
}
