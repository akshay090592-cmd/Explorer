package com.example.malaysiaitinerary.ui.itinerary

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.malaysiaitinerary.R
import com.example.malaysiaitinerary.data.local.entity.ItineraryItem
import com.example.malaysiaitinerary.ui.briefcase.BriefcaseUiState
import com.example.malaysiaitinerary.ui.briefcase.BriefcaseViewModel
import com.example.malaysiaitinerary.ui.theme.*
import com.example.malaysiaitinerary.util.ItineraryConflictDetector
import com.example.malaysiaitinerary.util.PdfUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryScreen(viewModel: ItineraryViewModel, briefcaseViewModel: BriefcaseViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val sharedMapsUrl by viewModel.sharedMapsUrl.collectAsState()
    val fetchingDetails by viewModel.fetchingDetails.collectAsState()
    val fetchedDetails by viewModel.fetchedDetails.collectAsState()
    
    var showAddActivityDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ItineraryItem?>(null) }
    var selectedTimeSlot by remember { mutableStateOf("ALL") }

    val exportStatus by viewModel.exportStatus.collectAsState()
    val context = LocalContext.current
    
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importDatabase(context, it)
        }
    }

    LaunchedEffect(sharedMapsUrl) {
        if (sharedMapsUrl != null) {
            showAddActivityDialog = true
        }
    }

    Scaffold(
        containerColor = ExplorerBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    IconButton(onClick = { showResetConfirmDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Reset Itinerary", tint = Color(0xFFEF4444))
                    }
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Export")
                    }
                    IconButton(onClick = { importLauncher.launch("*/*") }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Import")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddActivityDialog = true },
                containerColor = ExplorerPrimary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(64.dp).padding(bottom = 16.dp, end = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Activity", modifier = Modifier.size(32.dp))
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (val state = uiState) {
                is ItineraryUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ItineraryUiState.Success -> {
                    val allItems = state.groupedItems.values.flatten()
                    val conflictingIds = remember(allItems) {
                        ItineraryConflictDetector.findConflictingItemIds(allItems)
                    }

                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Text(
                            "Your Itinerary",
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
                            color = ExplorerPrimary
                        )
                        
                        Spacer(Modifier.height(18.dp))
                        
                        CategoryTabs(
                            selectedType = selectedType,
                            onTypeSelected = { viewModel.setFilterType(it) },
                            typeCounts = state.typeCounts
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        DateSelector(
                            selectedDate = selectedDate,
                            allDates = state.groupedItems.keys.sorted(),
                            onDateSelected = { 
                                if (selectedDate == it) viewModel.setSelectedDate(null)
                                else viewModel.setSelectedDate(it)
                            }
                        )

                        Spacer(Modifier.height(14.dp))

                        // Time Slot Filter Chips
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                Triple("ALL", "All", Icons.Default.AccessTime),
                                Triple("MORNING", "Morning", Icons.Default.LightMode),
                                Triple("AFTERNOON", "Afternoon", Icons.Default.WbSunny),
                                Triple("EVENING", "Evening", Icons.Default.DarkMode)
                            ).forEach { (key, label, iconVector) ->
                                FilterChip(
                                    selected = selectedTimeSlot == key,
                                    onClick = { selectedTimeSlot = key },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(iconVector, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        ItineraryTimeline(
                            groupedItems = state.groupedItems,
                            selectedDate = selectedDate,
                            timeFilter = selectedTimeSlot,
                            conflictingIds = conflictingIds,
                            viewModel = viewModel,
                            briefcaseViewModel = briefcaseViewModel,
                            onEditClick = { editingItem = it }
                        )
                    }
                }
                is ItineraryUiState.Empty -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EventBusy, contentDescription = null, tint = ExplorerOnSurfaceVariant, modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Itinerary is empty.", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ExplorerPrimary)
                            TextButton(onClick = { showAddActivityDialog = true }) {
                                Text("Add your first activity")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddActivityDialog) {
        AddActivityDialog(
            preFilledUrl = sharedMapsUrl,
            fetchingDetails = fetchingDetails,
            fetchedDetails = fetchedDetails,
            onDismiss = { 
                showAddActivityDialog = false
                viewModel.clearSharedUrl()
            },
            onAdd = { date, title, location, startTime, desc, mapsUrl, imageUrl, ticketUri, type ->
                viewModel.addCustomActivity(context, date, title, location, startTime, desc, mapsUrl, imageUrl, ticketUri)
                showAddActivityDialog = false
            }
        )
    }

    editingItem?.let { itemToEdit ->
        EditActivityDialog(
            item = itemToEdit,
            onDismiss = { editingItem = null },
            onUpdate = { updatedItem ->
                viewModel.updateActivity(updatedItem)
                editingItem = null
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444)) },
            title = { Text("Reset Itinerary & Trip Data?", fontWeight = FontWeight.Bold) },
            text = { Text("This will clear all itinerary activities and reset your trip schedule so you can start fresh.") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        viewModel.resetItinerary(context)
                        android.widget.Toast.makeText(context, "Itinerary reset successfully", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Reset Everything", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showExportDialog) {
        ExportDialog(
            exportStatus = exportStatus,
            onConfirm = { viewModel.exportDatabase(context) },
            onShare = { uri ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share Itinerary Backup"))
                showExportDialog = false
                viewModel.resetExportStatus()
            },
            onDismiss = {
                showExportDialog = false
                viewModel.resetExportStatus()
            }
        )
    }
}

@Composable
fun CategoryTabs(selectedType: String, onTypeSelected: (String) -> Unit, typeCounts: Map<String, Int>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val categories = listOf("Day Plan" to "All", "Flights" to "Flights", "Hotels" to "Hotels", "Activities" to "Activities")
        items(categories) { (label, type) ->
            val isSelected = selectedType == type
            Surface(
                onClick = { onTypeSelected(type) },
                shape = CircleShape,
                color = if (isSelected) ExplorerPrimary else ExplorerSurfaceContainerHigh,
                modifier = Modifier.height(40.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) Color.White else ExplorerOnSurfaceVariant
                    )
                    val count = when(type) {
                        "Flights" -> typeCounts["Flights"] ?: 0
                        "Hotels" -> typeCounts["Hotels"] ?: 0
                        "Activities" -> typeCounts["Activities"] ?: 0
                        else -> 0
                    }
                    if (count > 0) {
                        Spacer(Modifier.width(6.dp))
                        Surface(color = ExplorerSecondaryContainer, shape = CircleShape) {
                            Text("$count", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = ExplorerOnSecondaryContainer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DateSelector(selectedDate: String?, allDates: List<String>, onDateSelected: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
        items(allDates) { dateString ->
            val date = try { LocalDate.parse(dateString) } catch (e: Exception) { LocalDate.now() }
            val isSelected = selectedDate == dateString
            Card(
                onClick = { onDateSelected(dateString) },
                modifier = Modifier.width(64.dp).height(74.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = if (isSelected) ExplorerPrimary else ExplorerSurfaceContainerLow)
            ) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = if (isSelected) Color.White.copy(alpha = 0.8f) else ExplorerOnSurfaceVariant
                    )
                    Text(
                        date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) Color.White else ExplorerOnSurface
                    )
                }
            }
        }
    }
}

@Composable
fun ItineraryTimeline(
    groupedItems: Map<String, List<ItineraryItem>>,
    selectedDate: String?,
    timeFilter: String,
    conflictingIds: Set<Int>,
    viewModel: ItineraryViewModel,
    briefcaseViewModel: BriefcaseViewModel,
    onEditClick: (ItineraryItem) -> Unit
) {
    val listState = rememberLazyListState()
    val sortedDates = groupedItems.keys.sorted().filter { 
        selectedDate == null || it == selectedDate
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        sortedDates.forEach { dateString ->
            val rawItems = groupedItems[dateString] ?: emptyList()
            val filteredItems = rawItems.filter { item ->
                when (timeFilter) {
                    "MORNING" -> {
                        val parsed = ItineraryConflictDetector.parseTime(item.startTime)
                        parsed != null && parsed.hour < 12
                    }
                    "AFTERNOON" -> {
                        val parsed = ItineraryConflictDetector.parseTime(item.startTime)
                        parsed != null && parsed.hour in 12..16
                    }
                    "EVENING" -> {
                        val parsed = ItineraryConflictDetector.parseTime(item.startTime)
                        parsed != null && parsed.hour >= 17
                    }
                    else -> true
                }
            }

            itemsIndexed(filteredItems) { index, item ->
                TimelineItem(
                    item = item,
                    isConflicting = conflictingIds.contains(item.id),
                    briefcaseViewModel = briefcaseViewModel,
                    onEditClick = onEditClick,
                    onDeleteClick = { viewModel.deleteActivity(item) },
                    onAddTicket = { context, uri -> viewModel.addTicketToActivity(context, item.id, uri) },
                    isLast = index == filteredItems.size - 1
                )
            }
        }
    }
}

@Composable
fun TimelineItem(
    item: ItineraryItem,
    isConflicting: Boolean = false,
    briefcaseViewModel: BriefcaseViewModel,
    onEditClick: (ItineraryItem) -> Unit,
    onDeleteClick: () -> Unit,
    onAddTicket: (android.content.Context, Uri) -> Unit,
    isLast: Boolean
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val briefcaseUiState by briefcaseViewModel.uiState.collectAsState()
    val documents = (briefcaseUiState as? BriefcaseUiState.Success)?.documents ?: emptyList()
    val associatedDoc = documents.find { it.itineraryItemId == item.id }

    var expanded by remember { mutableStateOf(false) }
    
    val ticketLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onAddTicket(context, it) }
    }

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(modifier = Modifier.width(32.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(if (isConflicting) Color(0xFFEF4444) else ExplorerPrimary).offset(y = 8.dp))
            if (!isLast) {
                Box(modifier = Modifier.width(2.dp).weight(1f).background(ExplorerSurfaceContainerHighest))
            }
        }
        
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onEditClick(item) }) { Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp)) }
                                IconButton(onClick = onDeleteClick) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ExplorerError, modifier = Modifier.size(20.dp)) }
                                Icon(imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                            }
                        }

                        if (isConflicting) {
                            Surface(
                                color = Color(0xFFFFE4E6),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFBE123C),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Time Conflict detected with another item",
                                        color = Color(0xFFBE123C),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
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
                                if (item.description.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(item.description, style = MaterialTheme.typography.bodySmall, color = ExplorerOnSurfaceVariant)
                                }

                                if (item.googleMapsUrl.isNotEmpty()) {
                                    Spacer(Modifier.height(16.dp))
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
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.startTime.uppercase(Locale.getDefault()),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
                                    color = ExplorerSecondary
                                )
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = ExplorerPrimary
                                )
                                Text(
                                    item.locationName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = ExplorerOnSurfaceVariant
                                )
                                if (item.description.isNotEmpty()) {
                                    Text(item.description, style = MaterialTheme.typography.bodySmall, color = ExplorerOnSurfaceVariant)
                                }

                                if (isConflicting) {
                                    Surface(
                                        color = Color(0xFFFFE4E6),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.padding(top = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = Color(0xFFBE123C),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                "Time Conflict with another activity",
                                                color = Color(0xFFBE123C),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            Row {
                                IconButton(onClick = { onEditClick(item) }) { Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp)) }
                                IconButton(onClick = onDeleteClick) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ExplorerError, modifier = Modifier.size(20.dp)) }
                            }
                        }
                        
                        if (item.imageUrl != null) {
                            Spacer(Modifier.height(12.dp))
                            AsyncImage(
                                model = item.imageUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { 
                                    if (associatedDoc != null) PdfUtils.openPdf(context, associatedDoc.filePath)
                                    else ticketLauncher.launch("application/pdf")
                                },
                                modifier = Modifier.weight(1f),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = ExplorerPrimary)
                            ) {
                                Icon(Icons.Default.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (associatedDoc != null) "View Ticket" else "Upload Ticket", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode("${item.title} ${item.locationName}")}"))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f),
                                shape = CircleShape
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Map", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditActivityDialog(item: ItineraryItem, onDismiss: () -> Unit, onUpdate: (ItineraryItem) -> Unit) {
    var title by remember { mutableStateOf(item.title) }
    var date by remember { mutableStateOf(item.date) }
    var location by remember { mutableStateOf(item.locationName) }
    var time by remember { mutableStateOf(item.startTime) }
    var desc by remember { mutableStateOf(item.description) }
    var mapsUrl by remember { mutableStateOf(item.googleMapsUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Activity", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Time (e.g. 14:00)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mapsUrl, onValueChange = { mapsUrl = it }, label = { Text("Google Maps URL") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { 
                onUpdate(item.copy(title = title, date = date, locationName = location, startTime = time, description = desc, googleMapsUrl = mapsUrl))
            }) { Text("Update") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddActivityDialog(
    preFilledUrl: String? = null,
    fetchingDetails: Boolean = false,
    fetchedDetails: com.example.malaysiaitinerary.util.MapsScraper.MapDetails? = null,
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String, String, String, String?, Uri?, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("2026-03-23") }
    var location by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("12:00") }
    var desc by remember { mutableStateOf("") }
    var mapsUrl by remember { mutableStateOf(preFilledUrl ?: "") }
    var imageUrl by remember { mutableStateOf<String?>(null) }
    var ticketUri by remember { mutableStateOf<Uri?>(null) }
    var activityType by remember { mutableStateOf("ACTIVITY") }

    LaunchedEffect(fetchedDetails) {
        if (fetchedDetails != null) {
            title = fetchedDetails.name ?: ""
            location = fetchedDetails.name ?: ""
            imageUrl = fetchedDetails.imageUrl
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Activity", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Time (e.g. 10:00 AM)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Notes / Description") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mapsUrl, onValueChange = { mapsUrl = it }, label = { Text("Google Maps URL") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(date, title, location, time, desc, mapsUrl, imageUrl, ticketUri, activityType) }) { Text("Add Activity") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ExportDialog(exportStatus: ExportStatus, onConfirm: () -> Unit, onShare: (Uri) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Itinerary", fontWeight = FontWeight.Bold) },
        text = {
            when (exportStatus) {
                is ExportStatus.Idle -> Text("Create a full backup of all itinerary activities, expenses, and attached documents?")
                is ExportStatus.Loading -> Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is ExportStatus.Success -> Text("Export bundle created successfully! Tap Share to send or backup.")
                is ExportStatus.Error -> Text("Error exporting: ${exportStatus.message}")
            }
        },
        confirmButton = {
            if (exportStatus is ExportStatus.Idle) Button(onClick = onConfirm) { Text("Export") }
            if (exportStatus is ExportStatus.Success) Button(onClick = { onShare(exportStatus.uri) }) { Text("Share Backup") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
