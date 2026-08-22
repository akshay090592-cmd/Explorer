package com.example.malaysiaitinerary.ui.briefcase

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.malaysiaitinerary.R
import com.example.malaysiaitinerary.data.local.entity.Document
import com.example.malaysiaitinerary.ui.theme.*
import com.example.malaysiaitinerary.util.PdfUtils
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BriefcaseScreen(viewModel: BriefcaseViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var pendingType by remember { mutableStateOf("TICKET") }
    var pendingDescription by remember { mutableStateOf<String?>(null) }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = it.lastPathSegment?.substringAfterLast('/') ?: "document_${System.currentTimeMillis()}"
            viewModel.uploadDocument(context, it, fileName, pendingType, null, null, description = pendingDescription)
            Toast.makeText(context, "Document added to vault", Toast.LENGTH_SHORT).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importDatabase(context, it) }
    }

    val searchQuery by viewModel.searchQuery.collectAsState()

    fun openDocumentFile(doc: Document) {
        val file = File(doc.filePath)
        val lowerName = doc.fileName.lowercase()
        if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".webp")) {
            try {
                val uri = if (file.exists()) {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                } else {
                    Uri.parse(doc.filePath)
                }
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "View Image"))
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open image file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            PdfUtils.openPdf(context, doc.filePath)
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
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Add File", tint = ExplorerPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = ExplorerPrimary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(64.dp)
                    .padding(bottom = 16.dp, end = 8.dp)
                    .shadow(elevation = 8.dp, shape = CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Document", modifier = Modifier.size(32.dp))
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                "Vault Storage".uppercase(Locale.getDefault()),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                                color = ExplorerSecondary
                            )
                            Text(
                                "Digital Briefcase",
                                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
                                color = ExplorerPrimary
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton(Icons.Default.Share) { 
                                viewModel.exportDatabase(context) { uri -> 
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/zip"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Itinerary Backup"))
                                }
                            }
                            ActionButton(Icons.Default.Upload) { showAddDialog = true }
                            ActionButton(Icons.Default.Download) { importLauncher.launch("*/*") }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Natural Language Vault Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search vault: 'passport', 'flight', 'WOLO hotel'...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ExplorerPrimary) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ExplorerPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    if (searchQuery.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "AI Vault Semantic Search Active",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    if (uiState is BriefcaseUiState.Success) {
                        val state = uiState as BriefcaseUiState.Success
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            StorageSummaryCard(
                                label = if (state.isFiltered) "Matches Found" else "Total Files",
                                value = String.format(Locale.getDefault(), "%02d", if (state.isFiltered) state.documents.size else state.totalFiles),
                                containerColor = ExplorerPrimary,
                                contentColor = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            StorageSummaryCard(
                                label = "Recent Sync",
                                value = state.recentSync,
                                containerColor = ExplorerSecondaryContainer,
                                contentColor = ExplorerOnSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (uiState is BriefcaseUiState.Success) {
                val state = uiState as BriefcaseUiState.Success
                val groupedDocs = state.documents.groupBy { it.type }
                
                item { Spacer(Modifier.height(24.dp)) }
                
                // Flight Section
                item {
                    DocumentSection(
                        title = "Flights",
                        icon = Icons.Default.Flight,
                        documents = groupedDocs["FLIGHT"] ?: emptyList(),
                        onDelete = { viewModel.deleteDocument(it) },
                        onOpen = { openDocumentFile(it) }
                    )
                }
                
                item { Spacer(Modifier.height(16.dp)) }
                
                // Tickets Section
                item {
                    DocumentSection(
                        title = "Tickets & Passes",
                        icon = Icons.Default.ConfirmationNumber,
                        documents = (groupedDocs["TICKET"] ?: emptyList()) + (groupedDocs["ACTIVITY"] ?: emptyList()),
                        onDelete = { viewModel.deleteDocument(it) },
                        onOpen = { openDocumentFile(it) }
                    )
                }
                
                item { Spacer(Modifier.height(16.dp)) }
                
                // IDs Section
                item {
                    DocumentSection(
                        title = "IDs & Passports",
                        icon = Icons.Default.Badge,
                        documents = groupedDocs["ID"] ?: emptyList(),
                        onDelete = { viewModel.deleteDocument(it) },
                        onOpen = { openDocumentFile(it) }
                    )
                }

                item { Spacer(Modifier.height(16.dp)) }

                // Others Section
                item {
                    DocumentSection(
                        title = "Other Documents",
                        icon = Icons.Default.Folder,
                        documents = groupedDocs["OTHERS"] ?: emptyList(),
                        onDelete = { viewModel.deleteDocument(it) },
                        onOpen = { openDocumentFile(it) }
                    )
                }
            } else if (uiState is BriefcaseUiState.Loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ExplorerPrimary)
                    }
                }
            }
            
            item {
                Spacer(Modifier.height(100.dp))
            }
        }
    }

    if (showAddDialog) {
        AddDocumentDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { type, desc ->
                pendingType = type
                pendingDescription = desc
                showAddDialog = false
                fileLauncher.launch("*/*")
            }
        )
    }
}

@Composable
fun ActionButton(icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onClick() },
        color = ExplorerSurfaceContainerLow,
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = ExplorerPrimary)
        }
    }
}

@Composable
fun StorageSummaryCard(label: String, value: String, containerColor: Color, contentColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(label.uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = contentColor.copy(alpha = 0.7f))
            Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun DocumentSection(title: String, icon: ImageVector, documents: List<Document>, onDelete: (Document) -> Unit, onOpen: (Document) -> Unit) {
    var expanded by remember { mutableStateOf(documents.isNotEmpty()) }
    
    Card(
        modifier = Modifier.fillMaxWidth().shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp), spotColor = ExplorerPrimary.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ExplorerSurfaceContainerLowest)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = ExplorerPrimaryContainer.copy(alpha = 0.12f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(imageVector = icon, contentDescription = null, tint = ExplorerPrimary)
                        }
                    }
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ExplorerPrimary)
                        Text("${documents.size} documents", style = MaterialTheme.typography.labelSmall, color = ExplorerOnSurfaceVariant)
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = ExplorerOutline
                )
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (documents.isEmpty()) {
                        Text("No documents attached yet", style = MaterialTheme.typography.bodySmall, color = ExplorerOnSurfaceVariant)
                    } else {
                        documents.forEach { doc ->
                            BriefcaseDocumentItem(doc, onOpen = { onOpen(doc) }, onDelete = { onDelete(doc) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BriefcaseDocumentItem(doc: Document, onOpen: () -> Unit, onDelete: () -> Unit) {
    val isImage = doc.fileName.endsWith(".png", true) || doc.fileName.endsWith(".jpg", true) || doc.fileName.endsWith(".jpeg", true) || doc.fileName.endsWith(".webp", true)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ExplorerSurfaceContainerLow)
            .clickable { onOpen() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = if (isImage) Icons.Default.Image else Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = if (isImage) ExplorerPrimaryContainer else ExplorerError,
                modifier = Modifier.size(26.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.fileName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ExplorerPrimary,
                    maxLines = 1
                )
                Text(
                    text = "${if (isImage) "IMAGE" else "PDF"} • ${doc.type.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }.replace("Others", "Other")}",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = ExplorerOnSurfaceVariant
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = ExplorerError, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun AddDocumentDialog(
    onDismiss: () -> Unit,
    onAdd: (type: String, description: String?) -> Unit
) {
    var selectedType by remember { mutableStateOf("TICKET") }
    var description by remember { mutableStateOf("") }
    val types = listOf("FLIGHT", "TICKET", "ID", "OTHERS")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Document / Ticket", fontWeight = FontWeight.Bold, color = ExplorerPrimary) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Select document type:", style = MaterialTheme.typography.bodyMedium)
                
                types.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedType = type }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedType == type),
                            onClick = { selectedType = type },
                            colors = RadioButtonDefaults.colors(selectedColor = ExplorerPrimary)
                        )
                        Text(type, modifier = Modifier.padding(start = 8.dp), color = ExplorerPrimary, fontWeight = FontWeight.Medium)
                    }
                }
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Notes (Optional)") },
                    placeholder = { Text("e.g. Flight MH123 or Hotel Voucher") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ExplorerPrimary,
                        focusedLabelColor = ExplorerPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(selectedType, if (description.isNotBlank()) description else null) },
                colors = ButtonDefaults.buttonColors(containerColor = ExplorerPrimary)
            ) {
                Text("Select File (PDF / Image)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ExplorerSecondary)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}
