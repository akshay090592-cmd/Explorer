package com.example.malaysiaitinerary.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.malaysiaitinerary.data.repository.AiEngineMode
import com.example.malaysiaitinerary.data.repository.GemmaModelChoice
import com.example.malaysiaitinerary.ui.theme.ExplorerPrimaryContainer

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.platform.LocalContext
import java.io.File

import com.example.malaysiaitinerary.ui.components.ModelImportProgressDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    aiViewModel: com.example.malaysiaitinerary.ui.ai.AiAssistantViewModel? = null,
    onNavigateBack: (() -> Unit)? = null,
    onReplayOnboarding: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engineMode by viewModel.engineMode.collectAsState(initial = AiEngineMode.AUTO_ON_DEVICE)
    val gemmaChoice by viewModel.gemmaModelChoice.collectAsState(initial = GemmaModelChoice.GEMMA_4_1B_INT4)
    val modelPath by viewModel.gemmaModelPath.collectAsState(initial = "")
    val apiKey by viewModel.geminiApiKey.collectAsState(initial = "")
    val isSearchEnabled by viewModel.isSearchGroundingEnabled.collectAsState(initial = true)

    var apiKeyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var modelPathInput by remember(modelPath) { mutableStateOf(modelPath) }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    var showFallbackVariantDialog by remember { mutableStateOf(false) }

    val modelImportState by aiViewModel?.modelImportState?.collectAsState() ?: remember { mutableStateOf(com.example.malaysiaitinerary.ui.ai.ModelImportState()) }
    val downloadProgress by aiViewModel?.downloadProgress?.collectAsState() ?: remember { mutableStateOf(null) }
    val showVariantDialog by aiViewModel?.showVariantDialog?.collectAsState() ?: remember { mutableStateOf(false) }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            if (aiViewModel != null) {
                aiViewModel.selectModelFileFromUri(uri)
            } else {
                viewModel.setGemmaModelPath(uri.path ?: "")
            }
        }
    }

    if (showFallbackVariantDialog) {
        com.example.malaysiaitinerary.ui.components.Gemma4ModelDownloadDialog(
            onDismiss = { showFallbackVariantDialog = false },
            onConfirmDownload = {
                showFallbackVariantDialog = false
                com.example.malaysiaitinerary.util.ModelDownloader.startDirectDownload(context)
            }
        )
    }

    if (aiViewModel != null) {
        ModelImportProgressDialog(
            importState = modelImportState,
            onDismiss = { aiViewModel.dismissModelImportDialog() }
        )

        if (showVariantDialog) {
            com.example.malaysiaitinerary.ui.components.Gemma4ModelDownloadDialog(
                onDismiss = { aiViewModel.dismissDownloadVariantDialog() },
                onConfirmDownload = {
                    aiViewModel.startModelDownload()
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AI Engine & Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Privacy",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Privacy-First AI Architecture",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "On-Device Gemma runs completely offline with zero data sent to external servers.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = "AI Engine Mode",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = engineMode == AiEngineMode.AUTO_ON_DEVICE || engineMode == AiEngineMode.ON_DEVICE_GEMMA,
                    onClick = { viewModel.setEngineMode(AiEngineMode.AUTO_ON_DEVICE) },
                    label = { Text("On-Device Gemma 4") },
                    leadingIcon = { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = engineMode == AiEngineMode.GEMINI_CLOUD,
                    onClick = { viewModel.setEngineMode(AiEngineMode.GEMINI_CLOUD) },
                    label = { Text("Gemini Cloud API") },
                    leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            if (engineMode == AiEngineMode.AUTO_ON_DEVICE || engineMode == AiEngineMode.ON_DEVICE_GEMMA) {
                com.example.malaysiaitinerary.ui.components.ModelDownloadProgressCard(
                    progress = downloadProgress,
                    onDismiss = { aiViewModel?.dismissDownloadProgress() }
                )

                val scannedModels by aiViewModel?.scannedModels?.collectAsState() ?: remember { mutableStateOf(emptyList()) }

                // Gather imported files in app storage or scanned
                val filesDirModels = context.filesDir.listFiles()?.filter { f ->
                    f.isFile && (f.name.endsWith(".task") || f.name.endsWith(".bin") || f.name.endsWith(".litertlm")) && f.length() > 1000
                } ?: emptyList()

                val customFile = if (modelPath.isNotBlank()) File(modelPath) else null
                val isCustomValid = customFile != null && customFile.exists() && customFile.length() > 1000

                // Combine all unique valid imported model files
                val allImportedFiles = (filesDirModels + scannedModels.map { it.file } + listOfNotNull(if (isCustomValid) customFile else null)).distinctBy { it.absolutePath }

                if (allImportedFiles.isEmpty()) {
                    // No local models imported: Show clear, friendly download & setup steps
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = ExplorerPrimaryContainer,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Local AI Model Setup Pending",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Download the pre-compiled Gemma 4 model binary or import a local .task / .litertlm file to enable offline AI.",
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Step 1: Direct Download
                            Button(
                                onClick = {
                                    if (aiViewModel != null) {
                                        aiViewModel.showDownloadVariantDialog()
                                    } else {
                                        showFallbackVariantDialog = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ExplorerPrimaryContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("1. Direct Download Gemma 4 Model", fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Step 2: Pick File from Storage
                            OutlinedButton(
                                onClick = { modelPickerLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("2. Import Model File (.task / .litertlm / .bin)")
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Step 3: Open HuggingFace
                            TextButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(com.example.malaysiaitinerary.util.ModelDownloader.HF_LITERT_COMMUNITY_REPO_URL))
                                    context.startActivity(intent)
                                }
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Browse Hugging Face Gemma 4 Models", fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    // Models ARE available: Show ONLY the imported models cleanly
                    Text(
                        text = "Imported Local Models (${allImportedFiles.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    allImportedFiles.forEach { file ->
                        val isSelected = modelPath == file.absolutePath || (modelPath.isBlank() && file.name.contains("gemma", ignoreCase = true))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) ExplorerPrimaryContainer else MaterialTheme.colorScheme.outlineVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (aiViewModel != null) {
                                        aiViewModel.selectDiscoveredModel(com.example.malaysiaitinerary.util.DiscoveredModel(file))
                                    } else {
                                        viewModel.setGemmaModelPath(file.absolutePath)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        if (aiViewModel != null) {
                                            aiViewModel.selectDiscoveredModel(com.example.malaysiaitinerary.util.DiscoveredModel(file))
                                        } else {
                                            viewModel.setGemmaModelPath(file.absolutePath)
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF16A34A),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Ready (${file.length() / (1024 * 1024)} MB)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF16A34A)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Option to import or download another model
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { modelPickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import File", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                if (aiViewModel != null) {
                                    aiViewModel.showDownloadVariantDialog()
                                } else {
                                    showFallbackVariantDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download Model", fontSize = 12.sp)
                        }
                    }
                }

            } else {
                // Gemini API Key Input
                Text(
                    text = "Google Gemini API Key",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = {
                        apiKeyInput = it
                        viewModel.setGeminiApiKey(it)
                    },
                    label = { Text("Enter your Gemini API Key") },
                    placeholder = { Text("AIzaSy...") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                            Icon(
                                imageVector = if (isApiKeyVisible) Icons.Default.Lock else Icons.Default.Key,
                                contentDescription = "Toggle key"
                            )
                        }
                    },
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Google Search Grounding", fontWeight = FontWeight.Medium)
                    }
                    Switch(
                        checked = isSearchEnabled,
                        onCheckedChange = { viewModel.setSearchGroundingEnabled(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // App Intro Walkthrough Replay
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "App Features Guide",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Replay the interactive onboarding walkthrough describing app pages, Gemma 4 features, and examples.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onReplayOnboarding,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Replay Onboarding Walkthrough")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
