package com.example.malaysiaitinerary.ui.ai

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.malaysiaitinerary.ui.components.ModelImportProgressDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.malaysiaitinerary.data.repository.AiEngineMode
import com.example.malaysiaitinerary.ui.theme.*


import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    viewModel: AiAssistantViewModel,
    onNavigateToSettings: () -> Unit,
    onExploreSampleTrip: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    val isProcessing by viewModel.isProcessing.collectAsState(initial = false)
    val engineMode by viewModel.engineMode.collectAsState(initial = AiEngineMode.ON_DEVICE_GEMMA)
    val gemmaChoice by viewModel.gemmaChoice.collectAsState(initial = com.example.malaysiaitinerary.data.repository.GemmaModelChoice.GEMMA_4_1B_INT4)
    val gemmaPath by viewModel.gemmaPath.collectAsState(initial = "")
    val scannedModels by viewModel.scannedModels.collectAsState(initial = emptyList())

    val hasTrips by viewModel.hasTrips.collectAsState(initial = false)

    var inputText by remember { mutableStateOf("") }
    var expandedCardIndex by remember { mutableStateOf(0) }
    var showGenerateDialog by remember { mutableStateOf(false) }
    var showEngineMenu by remember { mutableStateOf(false) }
    var showModelGuideDialog by remember { mutableStateOf(false) }
    var showModelNotConfiguredDialog by remember { mutableStateOf(false) }
    var showAttachInfoDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var modifyMessage by remember { mutableStateOf<ChatUiMessage?>(null) }

    val listState = rememberLazyListState()

    val isLocalModelReady = remember(gemmaChoice, gemmaPath) {
        val targetFile = if (gemmaPath.isNotBlank()) {
            java.io.File(gemmaPath)
        } else {
            java.io.File(context.filesDir, gemmaChoice.filename)
        }
        targetFile.exists() && targetFile.length() > 1000
    }

    val trySendMessage: (String) -> Unit = { prompt ->
        if (prompt.isNotBlank()) {
            if ((engineMode == AiEngineMode.AUTO_ON_DEVICE || engineMode == AiEngineMode.ON_DEVICE_GEMMA) && !isLocalModelReady) {
                showModelNotConfiguredDialog = true
            } else {
                viewModel.sendMessage(prompt)
                if (inputText == prompt) {
                    inputText = ""
                }
            }
        }
    }

    val modelFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectModelFileFromUri(uri)
        }
    }

    val modelImportState by viewModel.modelImportState.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val showVariantDialog by viewModel.showVariantDialog.collectAsState()

    ModelImportProgressDialog(
        importState = modelImportState,
        onDismiss = { viewModel.dismissModelImportDialog() }
    )

    if (showVariantDialog) {
        com.example.malaysiaitinerary.ui.components.Gemma4ModelDownloadDialog(
            onDismiss = { viewModel.dismissDownloadVariantDialog() },
            onConfirmDownload = {
                viewModel.startModelDownload()
            }
        )
    }

    val attachedImageUri by viewModel.attachedImageUri.collectAsState()
    val isVoiceTravelModeActive by viewModel.isVoiceTravelModeActive.collectAsState()
    val isTtsEnabled by viewModel.isTtsEnabled.collectAsState()
    var showPhotoOptionsDialog by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setAttachedImageUri(uri)
            Toast.makeText(context, "Photo attached for Landmark Assistant!", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: android.graphics.Bitmap? ->
        if (bitmap != null) {
            try {
                val file = java.io.File(context.cacheDir, "captured_landmark_${System.currentTimeMillis()}.jpg")
                val fos = java.io.FileOutputStream(file)
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, fos)
                fos.flush()
                fos.close()
                val uri = Uri.fromFile(file)
                viewModel.setAttachedImageUri(uri)
                Toast.makeText(context, "Landmark photo captured!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission is required to capture landmark photos.", Toast.LENGTH_SHORT).show()
        }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            photoPickerLauncher.launch("image/*")
        } else {
            // Even if denied or skipped, launch system picker on modern Android
            photoPickerLauncher.launch("image/*")
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "application/pdf"
            val file = File(context.cacheDir, "imported_${System.currentTimeMillis()}")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            viewModel.parseAndImportDocument(file, mimeType)
        }
    }

    var isRecordingAudio by remember { mutableStateOf(false) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            OnDeviceAudioRecorder.startRecording(context)
            isRecordingAudio = true
        } else {
            Toast.makeText(context, "Microphone permission required for voice input.", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleAudioRecording() {
        if (!isRecordingAudio) {
            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        } else {
            isRecordingAudio = false
            val audioBytes = OnDeviceAudioRecorder.stopRecording()
            if (audioBytes != null && audioBytes.isNotEmpty()) {
                viewModel.sendAudioPrompt(audioBytes)
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Explorer AI Assistant",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showEngineMenu = true }
                                .padding(vertical = 2.dp, horizontal = 4.dp)
                        ) {
                            Surface(
                                color = ExplorerPrimaryContainer.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (engineMode == AiEngineMode.GEMINI_CLOUD) "Gemini Cloud" else "Gemma 4",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                        color = ExplorerPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "Switch Engine",
                                        tint = ExplorerPrimaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = showEngineMenu,
                            onDismissRequest = { showEngineMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Gemma 4 (On-Device)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        Text("100% Offline Local Inference", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    viewModel.setEngineMode(AiEngineMode.AUTO_ON_DEVICE)
                                    showEngineMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Memory, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Gemini Cloud API", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        Text("Web-Grounded Search Engine", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    viewModel.setEngineMode(AiEngineMode.GEMINI_CLOUD)
                                    showEngineMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) }
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.startNewChat() }) {
                        Icon(
                            imageVector = Icons.Default.AddComment,
                            contentDescription = "New Chat",
                            tint = ExplorerPrimary
                        )
                    }
                    IconButton(onClick = { viewModel.toggleVoiceTravelMode() }) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Hands-Free Voice Travel Mode",
                            tint = ExplorerPrimary
                        )
                    }
                    IconButton(onClick = { showResetConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Reset Itinerary",
                            tint = Color(0xFFEF4444)
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8FAFC))
        ) {
            // Explore Sample Trip Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { onExploreSampleTrip() }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TravelExplore,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Explore app with a sample itinerary",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0369A1),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Live Download Loader Card (When downloading)
            com.example.malaysiaitinerary.ui.components.ModelDownloadProgressCard(
                progress = downloadProgress,
                onDismiss = { viewModel.dismissDownloadProgress() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Model Warning Banners if model missing
            if ((engineMode == AiEngineMode.AUTO_ON_DEVICE || engineMode == AiEngineMode.ON_DEVICE_GEMMA) && gemmaPath.isBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Offline Gemma 4 Model Required",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF92400E)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Download the official LiteRT model binary or import an existing file to enable offline AI responses.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB45309)
                        )

                        if (scannedModels.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Discovered models on phone (tap to activate):",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF92400E)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                items(scannedModels) { model ->
                                    Button(
                                        onClick = { viewModel.selectDiscoveredModel(model) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("${model.name} (${model.sizeMb}MB)", fontSize = 12.sp)
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.showDownloadVariantDialog() },
                                    colors = ButtonDefaults.buttonColors(containerColor = ExplorerPrimaryContainer),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Direct Download", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { modelFilePickerLauncher.launch(arrayOf("*/*")) },
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(1.dp, ExplorerPrimaryContainer)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = ExplorerPrimaryContainer)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Import File", fontSize = 12.sp, color = ExplorerPrimaryContainer)
                                }
                            }
                        }
                    }
                }
            } else if ((engineMode == AiEngineMode.AUTO_ON_DEVICE || engineMode == AiEngineMode.ON_DEVICE_GEMMA) && gemmaPath.isNotBlank() && !gemmaPath.lowercase().endsWith(".safetensors") && !gemmaPath.lowercase().endsWith(".pth")) {
                // Active Model Banner with Change Model option
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Memory,
                                contentDescription = null,
                                tint = ExplorerPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = File(gemmaPath).name,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val fileMb = try { File(gemmaPath).length() / (1024 * 1024) } catch (e: Exception) { 0L }
                                Text(
                                    text = if (fileMb > 0) "$fileMb MB • Active Model" else "Active Model",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { modelFilePickerLauncher.launch(arrayOf("*/*")) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ExplorerPrimaryContainer)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp), tint = ExplorerPrimaryContainer)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Change Model", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ExplorerPrimaryContainer)
                        }
                    }
                }
            }

            val hasUserMessages = remember(messages) { messages.any { it.sender == MessageSender.USER } }

            // Scrollable Content
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // If user hasn't sent any messages yet, display Greeting & Expandable Cards matching reference design
                if (!hasUserMessages) {
                    item {
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Text(
                                text = "Hi, Explorer",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F172A)
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "I'm Explorer AI — your travel assistant powered by Gemma 4 & On-Device AI. How can I assist you today?",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFF64748B),
                                    lineHeight = 20.sp
                                )
                            )
                        }
                    }

                    // Accordion Suggestion Cards
                    item {
                        CategorizedPromptCard(
                            icon = Icons.Default.Place,
                            iconBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            title = "Itinerary & Attractions",
                            subtitle = "Plan day-by-day activities & places",
                            isExpanded = expandedCardIndex == 0,
                            onToggleExpand = { expandedCardIndex = if (expandedCardIndex == 0) -1 else 0 },
                            subPrompts = listOf(
                                "What are top things to do in Kuala Lumpur?",
                                "Create a 3-day itinerary for Penang",
                                "Suggest a family day trip in Melaka"
                            ),
                            onSelectPrompt = { prompt -> trySendMessage(prompt) }
                        )
                    }

                    item {
                        CategorizedPromptCard(
                            icon = Icons.Default.Hotel,
                            iconBgColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            title = "Hotels & Local Dining",
                            subtitle = "Food recommendations & stays",
                            isExpanded = expandedCardIndex == 1,
                            onToggleExpand = { expandedCardIndex = if (expandedCardIndex == 1) -1 else 1 },
                            subPrompts = listOf(
                                "Best places for Nasi Lemak in Bukit Bintang",
                                "Where to stay in Langkawi near beach?",
                                "Recommend halal food spots in Ipoh"
                            ),
                            onSelectPrompt = { prompt -> trySendMessage(prompt) }
                        )
                    }

                    item {
                        CategorizedPromptCard(
                            icon = Icons.Default.Payments,
                            iconBgColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            title = "Expenses & Document Vault",
                            subtitle = "Track trip budget & flight passes",
                            isExpanded = expandedCardIndex == 2,
                            onToggleExpand = { expandedCardIndex = if (expandedCardIndex == 2) -1 else 2 },
                            subPrompts = listOf(
                                "Log a RM 45 lunch expense",
                                "Summarize my trip expenses & budget",
                                "Import my travel ticket voucher"
                            ),
                            onSelectPrompt = { prompt ->
                                if (prompt.contains("Import my travel ticket")) {
                                    documentPickerLauncher.launch("*/*")
                                } else {
                                    trySendMessage(prompt)
                                }
                            }
                        )
                    }
                } else {
                    // Chat Messages
                    items(items = messages, key = { it.id }) { msg ->
                        ChatBubble(
                            msg = msg,
                            onNavigateToSettings = onNavigateToSettings,
                            onShowGuide = { showModelGuideDialog = true },
                            onSaveToDatabase = { targetMsg ->
                                viewModel.saveEntryToDatabase(
                                    messageId = targetMsg.id,
                                    title = targetMsg.parsedTitle ?: targetMsg.text.lines().firstOrNull()?.take(40) ?: "New Diary / Itinerary Entry",
                                    location = targetMsg.parsedLocation ?: "Kuala Lumpur, Malaysia",
                                    date = targetMsg.parsedDate ?: java.time.LocalDate.now().toString(),
                                    time = targetMsg.parsedTime ?: "09:00",
                                    description = targetMsg.text
                                )
                                Toast.makeText(context, "Saved entry to Database & Itinerary!", Toast.LENGTH_SHORT).show()
                            },
                            onModifyEntry = { targetMsg ->
                                modifyMessage = targetMsg
                            },
                            onDiscardMessage = { msgId ->
                                viewModel.discardMessage(msgId)
                                Toast.makeText(context, "Entry discarded", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                if (isProcessing) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = ExplorerPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI is thinking & executing actions...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Redesigned Docked Input Bar matching reference screenshot
            Surface(
                color = Color.White,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Attached Landmark/Photo Preview Chip
                    attachedImageUri?.let { uri ->
                        Surface(
                            color = ExplorerPrimaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = ExplorerPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Photo attached for Landmark/AI Assistant",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { viewModel.setAttachedImageUri(null) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = Color.Gray)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = if (isRecordingAudio) Color(0xFFF8FAFC) else Color(0xFFF1F5F9),
                            border = if (isRecordingAudio) BorderStroke(1.5.dp, Color(0xFF4285F4)) else null,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                if (!isRecordingAudio) {
                                    IconButton(
                                        onClick = { showPhotoOptionsDialog = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddAPhoto,
                                            contentDescription = "Landmark Assistant Photo",
                                            tint = ExplorerPrimary
                                        )
                                    }

                                    IconButton(
                                        onClick = { showAttachInfoDialog = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AttachFile,
                                            contentDescription = "Attach Document",
                                            tint = Color(0xFF64748B)
                                        )
                                    }

                                    BasicTextField(
                                        value = inputText,
                                        onValueChange = { inputText = it },
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 6.dp),
                                        textStyle = TextStyle(
                                            fontSize = 15.sp,
                                            color = Color(0xFF1E293B)
                                        ),
                                        decorationBox = { innerTextField ->
                                            if (inputText.isEmpty() && attachedImageUri == null) {
                                                Text(
                                                    text = "Ask Gemma AI or snap a landmark...",
                                                    style = TextStyle(
                                                        fontSize = 14.sp,
                                                        color = Color(0xFF94A3B8)
                                                    )
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )

                                    IconButton(
                                        onClick = { toggleAudioRecording() },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "Record Audio for Gemma 4",
                                            tint = ExplorerPrimary
                                        )
                                    }
                                } else {
                                    // Gemini Live Style Audio Equalizer Bar
                                    GeminiLiveEqualizerBar(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    )

                                    // Transform Mic icon into a Stop Recording Button
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFDC2626))
                                            .clickable { toggleAudioRecording() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = "Stop Recording & Send to Gemma 4",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(if ((inputText.isNotBlank() || attachedImageUri != null) && !isProcessing) Color(0xFF2563EB) else Color(0xFFCBD5E1))
                                .clickable(enabled = (inputText.isNotBlank() || attachedImageUri != null) && !isProcessing) {
                                    val userText = inputText
                                    inputText = ""
                                    trySendMessage(userText)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
        }
    }
    }

    if (isVoiceTravelModeActive) {
        VoiceTravelModeBottomSheet(
            onDismiss = { viewModel.setVoiceTravelModeActive(false) },
            onSpeakClick = { toggleAudioRecording() },
            latestAiText = messages.lastOrNull { it.sender == MessageSender.AI }?.text,
            isProcessing = isProcessing
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444)) },
            title = { Text("Reset Itinerary & Trip Data?", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Text(
                    "This will clear all itinerary activities, expenses, and travel documents so you can start over with a fresh itinerary.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        viewModel.resetItineraryAndData(context)
                        Toast.makeText(context, "Trip data reset successfully", Toast.LENGTH_SHORT).show()
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

    if (showAttachInfoDialog) {
        AlertDialog(
            onDismissRequest = { showAttachInfoDialog = false },
            icon = { Icon(Icons.Default.AttachFile, contentDescription = null, tint = ExplorerPrimaryContainer) },
            title = { Text("Attach Travel Document or Voucher", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column {
                    Text(
                        "You can attach travel documents such as PDF flight tickets, hotel vouchers, booking confirmations, or receipt images.",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Gemma 4 & On-Device ML will analyze the file, extract details, save it to your Briefcase Vault, and automatically log expenses!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            showAttachInfoDialog = false
                            documentPickerLauncher.launch("application/pdf")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ExplorerPrimaryContainer)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Choose PDF Document")
                    }
                    Button(
                        onClick = {
                            showAttachInfoDialog = false
                            documentPickerLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ExplorerSecondaryContainer)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Choose Image / Voucher")
                    }
                    TextButton(
                        onClick = { showAttachInfoDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }


    if (showGenerateDialog) {
        var queryText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showGenerateDialog = false },
            title = { Text("Generate Trip Itinerary") },
            text = {
                Column {
                    Text("Enter target destination and style:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        placeholder = { Text("e.g., 3 days in Penang focusing on beaches") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (queryText.isNotBlank()) {
                            viewModel.generateNewTrip(queryText, 3, "search and explore")
                            showGenerateDialog = false
                        }
                    }
                ) {
                    Text("Generate Trip")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGenerateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (modifyMessage != null) {
        val targetMsg = modifyMessage!!
        var titleText by remember { mutableStateOf(targetMsg.parsedTitle ?: targetMsg.text.lines().firstOrNull()?.take(40) ?: "Travel Entry") }
        var locationText by remember { mutableStateOf(targetMsg.parsedLocation ?: "Kuala Lumpur, Malaysia") }
        var dateText by remember { mutableStateOf(targetMsg.parsedDate ?: java.time.LocalDate.now().toString()) }
        var timeText by remember { mutableStateOf(targetMsg.parsedTime ?: "10:00") }
        var descriptionText by remember { mutableStateOf(targetMsg.text) }

        AlertDialog(
            onDismissRequest = { modifyMessage = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = ExplorerPrimaryContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Modify Entry Before Saving", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        label = { Text("Title / Summary") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dateText,
                            onValueChange = { dateText = it },
                            label = { Text("Date (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = timeText,
                            onValueChange = { timeText = it },
                            label = { Text("Time (HH:mm)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = locationText,
                        onValueChange = { locationText = it },
                        label = { Text("Location") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = descriptionText,
                        onValueChange = { descriptionText = it },
                        label = { Text("Notes / Description") },
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveEntryToDatabase(
                            messageId = targetMsg.id,
                            title = titleText,
                            location = locationText,
                            date = dateText,
                            time = timeText,
                            description = descriptionText
                        )
                        modifyMessage = null
                        Toast.makeText(context, "Entry saved to App & Database!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExplorerPrimaryContainer)
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save to Database")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { modifyMessage = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showModelNotConfiguredDialog) {
        AlertDialog(
            onDismissRequest = { showModelNotConfiguredDialog = false },
            icon = { Icon(Icons.Default.Memory, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(32.dp)) },
            title = {
                Text(
                    "Local Gemma Model Required",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "On-Device AI mode is active, but no model file is loaded. Download the Gemma 4 model or import a file to start chatting offline.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            showModelNotConfiguredDialog = false
                            viewModel.showDownloadVariantDialog()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ExplorerPrimaryContainer),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            showModelNotConfiguredDialog = false
                            modelFilePickerLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import", fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showModelNotConfiguredDialog = false
                        viewModel.setEngineMode(AiEngineMode.GEMINI_CLOUD)
                    }
                ) {
                    Text("Switch to Cloud API")
                }
            }
        )
    }

    if (showPhotoOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoOptionsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = ExplorerPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Attach Landmark Photo", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose how you want to attach a photo for landmark identification or menu parsing:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            showPhotoOptionsDialog = false
                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ExplorerPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Take Photo (Camera)")
                    }
                    OutlinedButton(
                        onClick = {
                            showPhotoOptionsDialog = false
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                mediaPermissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
                            } else {
                                mediaPermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose from Gallery")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoOptionsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showModelGuideDialog) {
        AiModelSetupGuideDialog(
            onDismiss = { showModelGuideDialog = false },
            onPickFileFromDownloads = {
                modelFilePickerLauncher.launch(arrayOf("*/*"))
            },
            onDirectDownload = {
                viewModel.showDownloadVariantDialog()
            },
            onSaveApiKey = { key ->
                viewModel.setEngineMode(AiEngineMode.GEMINI_CLOUD)
                Toast.makeText(context, "Saved Gemini API key & switched engine!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModelSetupGuideDialog(
    onDismiss: () -> Unit,
    onPickFileFromDownloads: () -> Unit,
    onDirectDownload: () -> Unit = {},
    onSaveApiKey: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var showAdvancedCommands by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = ExplorerPrimaryContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gemma 4 Local Setup", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Download the compiled Gemma 4 model binary or import a local file to run on-device AI.",
                    style = MaterialTheme.typography.bodySmall
                )

                // Action 1: Direct Download
                Button(
                    onClick = {
                        onDirectDownload()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ExplorerPrimaryContainer)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Direct Download Model")
                }

                // Action 2: Import Local File
                OutlinedButton(
                    onClick = {
                        onPickFileFromDownloads()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, ExplorerPrimaryContainer)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp), tint = ExplorerPrimaryContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Local File", color = ExplorerPrimaryContainer)
                }

                // Advanced litert-torch collapsible
                TextButton(
                    onClick = { showAdvancedCommands = !showAdvancedCommands },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = if (showAdvancedCommands) "Hide Advanced CLI Instructions ▲" else "Advanced: Convert Safetensors CLI ▼",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = showAdvancedCommands) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "Convert safetensors with litert-torch CLI:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "uv tool install litert-torch-nightly\nlitert-torch export_hf --model=google/gemma-4-E2B-it --output_dir=/tmp/gemma4_2b --externalize_embedder --jinja_chat_template_override=litert-community/gemma-4-E2B-it-litert-lm",
                                fontSize = 10.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = ExplorerPrimary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun ChatBubble(
    msg: ChatUiMessage,
    onNavigateToSettings: () -> Unit = {},
    onShowGuide: () -> Unit = {},
    onSaveToDatabase: (ChatUiMessage) -> Unit = {},
    onModifyEntry: (ChatUiMessage) -> Unit = {},
    onDiscardMessage: (String) -> Unit = {}
) {
    val isUser = msg.sender == MessageSender.USER
    val isSystem = msg.sender == MessageSender.SYSTEM

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = when {
            isUser -> Alignment.CenterEnd
            isSystem -> Alignment.Center
            else -> Alignment.CenterStart
        }
    ) {
        if (isSystem) {
            val isErrorSystemMsg = msg.text.contains("not available", ignoreCase = true) ||
                    msg.text.contains("failed", ignoreCase = true) ||
                    msg.text.contains("Error", ignoreCase = true) ||
                    msg.text.contains("Requires", ignoreCase = true)

            if (isErrorSystemMsg) {
                Surface(
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AI Service Notice",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF991B1B)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = msg.text,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = Color(0xFF7F1D1D)
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onNavigateToSettings,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Switch Engine / Key", fontSize = 11.sp, color = Color.White)
                            }
                            OutlinedButton(
                                onClick = onShowGuide,
                                border = BorderStroke(1.dp, Color(0xFFDC2626)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Setup Guide", fontSize = 11.sp, color = Color(0xFFDC2626))
                            }
                        }
                    }
                }
            } else {
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = msg.text,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = Color(0xFF475569)
                        )
                    }
                }
            }
        } else {
            Surface(
                color = if (isUser) ExplorerPrimaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                modifier = Modifier.widthIn(max = 310.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    FormattedMarkdownText(
                        text = msg.text,
                        isUser = isUser
                    )

                    // Tool Execution Cards
                    if (msg.toolExecutions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        msg.toolExecutions.forEach { log ->
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = ExplorerPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = log,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                                    )
                                }
                            }
                        }
                    }

                    // Interactive Action Row for AI Generated Entries / Responses
                    if (!isUser && msg.hasActionableContent) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))

                        if (msg.isSavedToDatabase) {
                            Surface(
                                color = Color(0xFFDCFCE7),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF166534),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Saved to App & Database",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF166534)
                                        )
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { onSaveToDatabase(msg) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ExplorerPrimaryContainer),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save to App", fontSize = 10.sp)
                                }

                                OutlinedButton(
                                    onClick = { onModifyEntry(msg) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp),
                                    border = BorderStroke(1.dp, ExplorerPrimaryContainer)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp), tint = ExplorerPrimaryContainer)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Modify", fontSize = 10.sp, color = ExplorerPrimaryContainer)
                                }

                                TextButton(
                                    onClick = { onDiscardMessage(msg.id) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFFEF4444))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Discard", fontSize = 10.sp, color = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategorizedPromptCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBgColor: Color,
    title: String,
    subtitle: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    subPrompts: List<String>,
    onSelectPrompt: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onToggleExpand() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subPrompts.forEach { prompt ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPrompt(prompt) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = prompt,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceTravelModeBottomSheet(
    onDismiss: () -> Unit,
    onSpeakClick: () -> Unit,
    latestAiText: String?,
    isProcessing: Boolean
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = ExplorerPrimary,
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.clickable { onSpeakClick() }) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Tap to Speak",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isProcessing) "Gemma AI Thinking..." else "Hands-Free Voice Travel Mode",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            Text(
                text = "Tap mic icon or say prompt aloud while walking or driving.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Latest Response:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ExplorerSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = latestAiText ?: "Say 'What should I visit in Kuala Lumpur?' or 'Where is my boarding pass?'",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Text("Close Voice Mode")
                }
                Button(
                    onClick = onSpeakClick,
                    colors = ButtonDefaults.buttonColors(containerColor = ExplorerPrimary)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Speak Now")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun FormattedMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    isUser: Boolean = false
) {
    val lines = text.lines()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("---") || trimmed.startsWith("***") -> {
                    HorizontalDivider(
                        color = if (isUser) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        text = parseBoldMarkdown(trimmed.removePrefix("# ").trim()),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (isUser) Color.White else ExplorerPrimary
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = parseBoldMarkdown(trimmed.removePrefix("## ").trim()),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isUser) Color.White else ExplorerPrimary
                    )
                }
                trimmed.startsWith("### ") -> {
                    Text(
                        text = parseBoldMarkdown(trimmed.removePrefix("### ").trim()),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isUser) Color.White else ExplorerSecondary
                    )
                }
                trimmed.startsWith("* ") || trimmed.startsWith("- ") -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "• ",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isUser) Color.White else ExplorerPrimary
                        )
                        Text(
                            text = parseBoldMarkdown(trimmed.substring(2).trim()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                trimmed.isNotEmpty() -> {
                    Text(
                        text = parseBoldMarkdown(trimmed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

fun parseBoldMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var remaining = text
        while (remaining.isNotEmpty()) {
            val startIndex = remaining.indexOf("**")
            if (startIndex == -1) {
                append(remaining)
                break
            } else {
                append(remaining.substring(0, startIndex))
                val afterFirst = remaining.substring(startIndex + 2)
                val endIndex = afterFirst.indexOf("**")
                if (endIndex == -1) {
                    append("**")
                    append(afterFirst)
                    break
                } else {
                    val boldText = afterFirst.substring(0, endIndex)
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
                    append(boldText)
                    pop()
                    remaining = afterFirst.substring(endIndex + 2)
                }
            }
        }
    }
}

@Composable
fun GeminiLiveEqualizerBar(
    modifier: Modifier = Modifier
) {
    val barColors = listOf(
        Color(0xFF4285F4), // Google Blue
        Color(0xFFEA4335), // Google Red
        Color(0xFFFBBC05), // Google Yellow
        Color(0xFF34A853), // Google Green
        Color(0xFFA855F7)  // Gemini Purple
    )

    val infiniteTransition = rememberInfiniteTransition(label = "GeminiEqualizer")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        barColors.forEachIndexed { index, color ->
            val duration = 350 + (index * 110)
            val minHeight = 6.dp
            val maxHeight = 22.dp
            val heightFraction by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = duration, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(minHeight + (maxHeight - minHeight) * heightFraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "textPulse"
        )

        Text(
            text = "Listening to voice...",
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1E293B).copy(alpha = pulseAlpha)
            )
        )
    }
}

object OnDeviceAudioRecorder {
    private var isRecording = false
    private var audioRecord: android.media.AudioRecord? = null
    private var recordingThread: Thread? = null
    private var pcmFile: File? = null

    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT

    fun startRecording(context: android.content.Context) {
        try {
            val minBufferSize = android.media.AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = maxOf(minBufferSize, 2048)

            @Suppress("MissingPermission")
            audioRecord = android.media.AudioRecord(
                android.media.MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            pcmFile = File(context.cacheDir, "gemma_16k_pcm_${System.currentTimeMillis()}.raw")
            isRecording = true
            audioRecord?.startRecording()

            recordingThread = Thread {
                val data = ByteArray(bufferSize)
                pcmFile?.outputStream()?.use { output ->
                    while (isRecording) {
                        val read = audioRecord?.read(data, 0, data.size) ?: 0
                        if (read > 0) {
                            output.write(data, 0, read)
                        }
                    }
                }
            }.apply { start() }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopRecording(): ByteArray? {
        return try {
            isRecording = false
            recordingThread?.join(1000)
            audioRecord?.apply {
                stop()
                release()
            }
            audioRecord = null

            val pcmBytes = pcmFile?.readBytes()
            pcmFile?.delete()
            pcmFile = null

            if (pcmBytes != null && pcmBytes.isNotEmpty()) {
                createWavBuffer(pcmBytes, SAMPLE_RATE, 1, 16)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            audioRecord = null
            pcmFile = null
            null
        }
    }

    private fun createWavBuffer(pcmData: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmData.size and 0xff).toByte()
        header[41] = (pcmData.size shr 8 and 0xff).toByte()
        header[42] = (pcmData.size shr 16 and 0xff).toByte()
        header[43] = (pcmData.size shr 24 and 0xff).toByte()

        val wavBuffer = ByteArray(44 + pcmData.size)
        System.arraycopy(header, 0, wavBuffer, 0, 44)
        System.arraycopy(pcmData, 0, wavBuffer, 44, pcmData.size)
        return wavBuffer
    }
}


