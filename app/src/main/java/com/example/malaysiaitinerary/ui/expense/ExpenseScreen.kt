package com.example.malaysiaitinerary.ui.expense

import android.content.Intent
import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.malaysiaitinerary.R
import com.example.malaysiaitinerary.data.local.entity.Expense
import com.example.malaysiaitinerary.ui.theme.*
import com.example.malaysiaitinerary.util.ReceiptParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(viewModel: ExpenseViewModel) {
    val expenses by viewModel.expenses.collectAsState()
    val totalSpentINR by viewModel.totalSpentINR.collectAsState()
    val totalSpentMYR by viewModel.totalSpentMYR.collectAsState()
    val categorySummaries by viewModel.categorySummaries.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showInsightsSheet by remember { mutableStateOf(false) }
    var isScanningReceipt by remember { mutableStateOf(false) }

    // Pre-filled values for Add Dialog
    var initialAmount by remember { mutableStateOf("") }
    var initialCurrency by remember { mutableStateOf("MYR") }
    var initialCategory by remember { mutableStateOf("Food") }
    var initialDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var initialDesc by remember { mutableStateOf("") }

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // OCR Receipt Image Picker
    val receiptPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isScanningReceipt = true
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    recognizer.process(inputImage)
                        .addOnSuccessListener { visionText ->
                            isScanningReceipt = false
                            val parsed = ReceiptParser.parse(visionText.text)
                            initialAmount = parsed.amount?.toString() ?: ""
                            initialCurrency = parsed.currency
                            initialCategory = parsed.category
                            initialDate = parsed.date ?: LocalDate.now().toString()
                            initialDesc = parsed.merchant
                            showAddDialog = true
                            Toast.makeText(context, "Receipt scanned! Review extracted details.", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            isScanningReceipt = false
                            Toast.makeText(context, "Failed to read receipt: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    isScanningReceipt = false
                    Toast.makeText(context, "Could not load selected image", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                isScanningReceipt = false
                Toast.makeText(context, "Error processing image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
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
                    IconButton(onClick = {
                        val csv = viewModel.generateCsvExport()
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, csv)
                            putExtra(Intent.EXTRA_SUBJECT, "Explorer Expenses Export")
                            type = "text/csv"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Export Expenses CSV"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export CSV", tint = ExplorerPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Receipt Scan OCR Button
                ExtendedFloatingActionButton(
                    onClick = { receiptPickerLauncher.launch("image/*") },
                    containerColor = ExplorerPrimaryContainer,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                    icon = {
                        if (isScanningReceipt) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.DocumentScanner, contentDescription = "Scan Receipt")
                        }
                    },
                    text = {
                        Text("Scan Receipt", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                )

                // Add Manual Button
                FloatingActionButton(
                    onClick = {
                        initialAmount = ""
                        initialCurrency = "MYR"
                        initialCategory = "Food"
                        initialDate = LocalDate.now().toString()
                        initialDesc = ""
                        showAddDialog = true
                    },
                    containerColor = ExplorerPrimary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Expense", modifier = Modifier.size(28.dp))
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // Dashboard Summary
            item {
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Main Total Card
                    Card(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ExplorerPrimary)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "Total Spent (INR)".uppercase(Locale.getDefault()),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = Color(0xFF83BAD6)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "₹${String.format(Locale.getDefault(), "%,.2f", totalSpentINR ?: 0.0)}",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                color = Color.White
                            )
                            Spacer(Modifier.height(16.dp))
                            Surface(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = CircleShape
                            ) {
                                Text(
                                    "Base Currency: INR (₹)",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                // Conversion & Budget Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Total Spent (MYR)".uppercase(Locale.getDefault()),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "RM ${String.format(Locale.getDefault(), "%,.2f", totalSpentMYR ?: 0.0)}",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            // Spending vs Budget Indicator
                            val estimatedBudgetINR = 60000.0
                            val currentSpentINR = totalSpentINR ?: 0.0
                            val budgetFraction = ((currentSpentINR / estimatedBudgetINR).coerceIn(0.0, 1.0)).toFloat()
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "Trip Budget (₹60k)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { budgetFraction },
                                    modifier = Modifier.width(100.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = if (budgetFraction > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${(budgetFraction * 100).toInt()}% Used",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(24.dp), 
                                    shape = CircleShape, 
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) { 
                                        Text("₹", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) 
                                    }
                                }
                                Surface(
                                    modifier = Modifier.size(24.dp).offset(x = (-4).dp), 
                                    shape = CircleShape, 
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) { 
                                        Text("RM", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary) 
                                    }
                                }
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Dual-Currency Sync", 
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), 
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "${expenses.size} entries", 
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), 
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Transaction Header & Insights Button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent Activity",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = ExplorerPrimary
                    )
                    TextButton(onClick = { showInsightsSheet = true }) {
                        Icon(Icons.Default.PieChart, contentDescription = null, modifier = Modifier.size(18.dp), tint = ExplorerPrimaryContainer)
                        Spacer(Modifier.width(6.dp))
                        Text("View Insights", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = ExplorerPrimaryContainer)
                    }
                }
            }

            // Group by Date
            val groupedExpenses = expenses.groupBy { it.date }
            if (expenses.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ExplorerSurfaceContainerLowest)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = ExplorerOnSurfaceVariant, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No expenses logged yet", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ExplorerPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text("Tap + to add or scan a receipt with camera/gallery", style = MaterialTheme.typography.bodySmall, color = ExplorerOnSurfaceVariant)
                        }
                    }
                }
            }

            groupedExpenses.forEach { (date, dateExpenses) ->
                item {
                    Surface(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = ExplorerPrimaryContainer.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = date,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = ExplorerPrimaryContainer
                        )
                    }
                }

                items(dateExpenses) { expense ->
                    ExpenseCard(expense = expense, onDelete = { viewModel.deleteExpense(it) })
                    Spacer(Modifier.height(10.dp))
                }
            }

            item {
                Spacer(Modifier.height(120.dp))
            }
        }
    }

    if (showInsightsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInsightsSheet = false },
            sheetState = sheetState
        ) {
            ExpenseInsightsContent(
                summaries = categorySummaries,
                totalMYR = totalSpentMYR ?: 0.0,
                totalINR = totalSpentINR ?: 0.0
            )
        }
    }

    if (showAddDialog) {
        AddExpenseDialog(
            initialAmount = initialAmount,
            initialCurrency = initialCurrency,
            initialCategory = initialCategory,
            initialDate = initialDate,
            initialDesc = initialDesc,
            onDismiss = { showAddDialog = false },
            onAdd = { amount, currency, category, date, desc ->
                viewModel.addExpense(context, amount.toDoubleOrNull() ?: 0.0, currency, category, date, desc)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ExpenseCard(expense: Expense, onDelete: (Expense) -> Unit) {
    val categoryIcon = getCategoryIcon(expense.category)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ExplorerSurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = ExplorerPrimaryContainer.copy(alpha = 0.12f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(categoryIcon, contentDescription = null, tint = ExplorerPrimaryContainer, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = expense.description,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = ExplorerPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = expense.category,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = ExplorerOnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.wrapContentWidth(Alignment.End)
                ) {
                    Text(
                        text = "₹${String.format(Locale.getDefault(), "%,.2f", expense.convertedAmountINR)}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        color = ExplorerPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "RM ${String.format(Locale.getDefault(), "%,.2f", expense.convertedAmountMYR)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ExplorerOnSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(modifier = Modifier.fillMaxWidth(), color = ExplorerOutlineVariant.copy(alpha = 0.5f), thickness = 1.dp)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ExplorerSecondary))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Original: ${expense.amount} ${expense.currency}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                        color = ExplorerOnSurfaceVariant
                    )
                }
                IconButton(onClick = { onDelete(expense) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = ExplorerError, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun ExpenseInsightsContent(
    summaries: List<CategorySummary>,
    totalMYR: Double,
    totalINR: Double
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 36.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Spending Insights",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = ExplorerPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Category breakdown & budget distribution",
            style = MaterialTheme.typography.bodyMedium,
            color = ExplorerOnSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        // Total Overview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ExplorerSurfaceContainerLow)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("TOTAL EXPENDITURE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ExplorerOnSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("₹${String.format(Locale.getDefault(), "%,.2f", totalINR)}", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = ExplorerPrimary)
                    Text("RM ${String.format(Locale.getDefault(), "%,.2f", totalMYR)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ExplorerPrimaryContainer)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Breakdown by Category", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ExplorerPrimary)
        Spacer(Modifier.height(14.dp))

        if (summaries.isEmpty()) {
            Text("No expense categories found.", style = MaterialTheme.typography.bodyMedium, color = ExplorerOnSurfaceVariant)
        } else {
            summaries.forEach { item ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(getCategoryIcon(item.category), contentDescription = null, tint = ExplorerPrimaryContainer, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(item.category, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = ExplorerPrimary)
                            Text(" (${item.count})", style = MaterialTheme.typography.bodySmall, color = ExplorerOnSurfaceVariant)
                        }
                        Text(
                            "RM ${String.format(Locale.getDefault(), "%,.2f", item.totalMYR)} (${String.format(Locale.getDefault(), "%.1f", item.percentage)}%)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = ExplorerPrimary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (item.percentage / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = ExplorerPrimaryContainer,
                        trackColor = ExplorerOutlineVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

@Composable
fun AddExpenseDialog(
    initialAmount: String = "",
    initialCurrency: String = "MYR",
    initialCategory: String = "Food",
    initialDate: String = "",
    initialDesc: String = "",
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String, String) -> Unit
) {
    var amount by remember { mutableStateOf(initialAmount) }
    var currency by remember { mutableStateOf(initialCurrency) }
    var category by remember { mutableStateOf(initialCategory) }
    var date by remember { mutableStateOf(if (initialDate.isBlank()) LocalDate.now().toString() else initialDate) }
    var desc by remember { mutableStateOf(initialDesc) }

    val categories = listOf("Food", "Transport", "Hotel", "Activities", "Shopping", "Misc")
    var expandedCategory by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Expense", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    placeholder = { Text("e.g. 45.50") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it.uppercase() },
                        label = { Text("Currency") },
                        placeholder = { Text("MYR / INR / USD") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        modifier = Modifier.weight(1.2f),
                        singleLine = true
                    )
                }

                // Category Quick Chips
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.take(4).forEach { cat ->
                        FilterChip(
                            selected = category.equals(cat, ignoreCase = true),
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description / Merchant") },
                    placeholder = { Text("e.g. Jalan Alor Hawker Dinner") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(amount, currency, category, date, desc) },
                colors = ButtonDefaults.buttonColors(containerColor = ExplorerPrimaryContainer)
            ) {
                Text("Save Expense", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun getCategoryIcon(category: String): ImageVector {
    val lower = category.lowercase()
    return when {
        lower.contains("food") || lower.contains("dining") || lower.contains("restaurant") -> Icons.Default.Restaurant
        lower.contains("transport") || lower.contains("flight") || lower.contains("grab") || lower.contains("taxi") -> Icons.Default.DirectionsCar
        lower.contains("hotel") || lower.contains("lodging") || lower.contains("stay") -> Icons.Default.Hotel
        lower.contains("shopping") || lower.contains("store") || lower.contains("mall") -> Icons.Default.ShoppingBag
        lower.contains("activity") || lower.contains("sight") || lower.contains("ticket") -> Icons.Default.Attractions
        else -> Icons.Default.Payments
    }
}
