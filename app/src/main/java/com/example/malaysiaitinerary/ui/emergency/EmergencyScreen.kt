package com.example.malaysiaitinerary.ui.emergency

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.malaysiaitinerary.R
import com.example.malaysiaitinerary.ui.theme.*
import com.google.android.gms.location.LocationServices
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current

    // Text To Speech state
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var currentlyPlayingText by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val malayLocale = Locale("ms", "MY")
                val result = tts?.setLanguage(malayLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.ENGLISH
                }
                isTtsReady = true
            }
        }
        ttsInstance = tts

        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    // Location State
    var liveLatitude by remember { mutableStateOf(3.1579) }
    var liveLongitude by remember { mutableStateOf(101.7116) }
    var locationName by remember { mutableStateOf("Kuala Lumpur City Centre, 50088 (Default GPS)") }
    var isLiveGpsActive by remember { mutableStateOf(false) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun fetchCurrentLocation() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                    if (loc != null) {
                        liveLatitude = loc.latitude
                        liveLongitude = loc.longitude
                        locationName = String.format(Locale.getDefault(), "Live GPS: %.4f° N, %.4f° E", loc.latitude, loc.longitude)
                        isLiveGpsActive = true
                        Toast.makeText(context, "Location updated successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        // Fallback to LocationManager
                        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                        val gpsLoc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        if (gpsLoc != null) {
                            liveLatitude = gpsLoc.latitude
                            liveLongitude = gpsLoc.longitude
                            locationName = String.format(Locale.getDefault(), "Live GPS: %.4f° N, %.4f° E", gpsLoc.latitude, gpsLoc.longitude)
                            isLiveGpsActive = true
                        } else {
                            Toast.makeText(context, "Using cached coordinates", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Unable to get current location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchCurrentLocation()
        } else {
            Toast.makeText(context, "Location permission required for live GPS coordinates", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine) {
            fetchCurrentLocation()
        }
    }

    val emergencyContacts = listOf(
        EmergencyContact("Police / Ambulance", "999", Icons.Default.Emergency, Color(0xFF832100), Color(0xFFFFDBD1)),
        EmergencyContact("Fire Department", "994", Icons.Default.FireTruck, Color(0xFFC2410C), Color(0xFFFFEDD5))
    )

    val otherContacts = listOf(
        EmergencyContact("Tourist Police", "03-2149 6590", Icons.Default.Policy, ExplorerSecondary, ExplorerSecondaryContainer),
        EmergencyContact("Civil Defence", "991", Icons.Default.Shield, ExplorerPrimary, ExplorerPrimaryContainer),
        EmergencyContact("KL Hospital (HKL)", "03-2615 5555", Icons.Default.LocalHospital, Color(0xFF0369A1), Color(0xFFE0F2FE)),
        EmergencyContact("Indian High Comm.", "03-2093 1010", Icons.Default.AccountBalance, Color(0xFF15803D), Color(0xFFDCFCE7))
    )

    val medicalPhrases = listOf(
        MedicalPhrase("I need help", "Tolong saya", "Help me"),
        MedicalPhrase("I need a doctor", "Saya perlukan doktor", null),
        MedicalPhrase("Call an ambulance", "Sila panggil ambulans", null),
        MedicalPhrase("I am allergic to this", "Saya ada alahan terhadap ini", null),
        MedicalPhrase("Where is the hospital?", "Di manakah hospital?", null)
    )

    val shortPhrases = listOf(
        "HOSPITAL" to "Hospital",
        "PHARMACY" to "Farmasi",
        "PAIN / ALLERGY" to "Sakit / Alergi",
        "MEDICINE" to "Ubat"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Refresh GPS", tint = ExplorerPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // Hero Section
            item {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Text(
                        "Emergency Support",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1).sp
                        ),
                        color = ExplorerPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "One-tap access to local help, live GPS dispatch, and spoken essential phrases in Malaysia.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = ExplorerOnSurfaceVariant
                    )
                }
            }

            // Location Card
            item {
                LiveLocationCard(
                    locationName = locationName,
                    latitude = liveLatitude,
                    longitude = liveLongitude,
                    isLive = isLiveGpsActive,
                    onRequestPermission = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onShareLocation = {
                        val mapsUrl = "https://maps.google.com/?q=$liveLatitude,$liveLongitude"
                        val shareText = "EMERGENCY: I need assistance. My current location is $locationName ($mapsUrl)"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Location with Emergency Contacts"))
                    },
                    onCopyCoords = {
                        clipboardManager.setText(AnnotatedString("$liveLatitude, $liveLongitude"))
                        Toast.makeText(context, "Coordinates copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )
                Spacer(Modifier.height(24.dp))
            }

            // Primary Contacts Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Primary Emergency Contacts",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = ExplorerPrimary
                    )
                }
            }

            // Main Contacts
            items(emergencyContacts) { contact ->
                EmergencyActionCard(contact = contact, onCall = {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.number}"))
                    context.startActivity(dialIntent)
                })
                Spacer(Modifier.height(16.dp))
            }

            // Other Contacts Grid
            item {
                Text(
                    "Tourist & Medical Directory",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ExplorerPrimary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
                )
                val chunks = otherContacts.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    chunks.forEach { chunk ->
                        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            chunk.forEach { contact ->
                                OtherContactCard(
                                    contact = contact,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    onCall = {
                                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.number}"))
                                        context.startActivity(dialIntent)
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }

            // Phrases Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Essential Spoken Phrases (TTS)",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = ExplorerPrimary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Tap to Speak",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            items(medicalPhrases) { phrase ->
                MedicalPhraseCard(
                    phrase = phrase,
                    isPlaying = currentlyPlayingText == phrase.malay,
                    onSpeak = {
                        if (ttsInstance != null && isTtsReady) {
                            currentlyPlayingText = phrase.malay
                            ttsInstance?.speak(phrase.malay, TextToSpeech.QUEUE_FLUSH, null, phrase.malay.hashCode().toString())
                        } else {
                            Toast.makeText(context, "Text-to-speech initializing...", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                Spacer(Modifier.height(14.dp))
            }

            // Short Phrases Grid
            item {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val chunks = shortPhrases.chunked(2)
                    chunks.forEach { chunk ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            chunk.forEach { (label, malay) ->
                                ShortPhraseCard(
                                    label = label,
                                    malay = malay,
                                    modifier = Modifier.weight(1f),
                                    onSpeak = {
                                        if (ttsInstance != null && isTtsReady) {
                                            ttsInstance?.speak(malay, TextToSpeech.QUEUE_FLUSH, null, malay.hashCode().toString())
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(100.dp))
            }
        }
    }
}

data class EmergencyContact(
    val name: String,
    val number: String,
    val icon: ImageVector,
    val iconColor: Color,
    val iconBackground: Color
)

data class MedicalPhrase(
    val english: String,
    val malay: String,
    val literal: String?
)

@Composable
fun EmergencyActionCard(contact: EmergencyContact, onCall: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ExplorerSurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = contact.iconBackground,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = contact.icon,
                        contentDescription = null,
                        tint = contact.iconColor,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    contact.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ExplorerOnSurface
                )
                Text(
                    contact.number,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = contact.iconColor
                )
            }
            IconButton(
                onClick = onCall,
                modifier = Modifier
                    .size(48.dp)
                    .background(if (contact.name.contains("Fire", ignoreCase = true)) Color(0xFFEA580C) else Color(0xFF5C1400), CircleShape)
            ) {
                Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White)
            }
        }
    }
}

@Composable
fun OtherContactCard(contact: EmergencyContact, modifier: Modifier = Modifier, onCall: () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ExplorerSurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = contact.iconBackground,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = contact.icon,
                                contentDescription = null,
                                tint = contact.iconColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        contact.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = ExplorerOnSurface,
                        maxLines = 1
                    )
                }
            }
            Column(modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    contact.number,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = contact.iconColor
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onCall,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = contact.iconBackground, contentColor = contact.iconColor),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("DIAL", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
fun MedicalPhraseCard(phrase: MedicalPhrase, isPlaying: Boolean = false, onSpeak: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ExplorerSurfaceContainerLow
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(ExplorerSecondary))
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        phrase.english.uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
                        color = ExplorerOnSurfaceVariant
                    )
                    IconButton(
                        onClick = onSpeak,
                        modifier = Modifier.size(36.dp).background(ExplorerPrimaryContainer.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "Speak Phrase",
                            tint = ExplorerPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "\"${phrase.malay}\"",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    color = ExplorerPrimary
                )
                phrase.literal?.let {
                    Text(
                        "Meaning: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = ExplorerOnSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ShortPhraseCard(label: String, malay: String, modifier: Modifier = Modifier, onSpeak: () -> Unit = {}) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ExplorerSurfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = ExplorerOnSurfaceVariant
                )
                IconButton(onClick = onSpeak, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = ExplorerPrimary, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "\"$malay\"",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                color = ExplorerPrimary
            )
        }
    }
}

@Composable
fun LiveLocationCard(
    locationName: String,
    latitude: Double,
    longitude: Double,
    isLive: Boolean,
    onRequestPermission: () -> Unit,
    onShareLocation: () -> Unit,
    onCopyCoords: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ExplorerPrimary)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Icon(
                Icons.Default.Map,
                contentDescription = null,
                modifier = Modifier.size(128.dp).align(Alignment.TopEnd).offset(x = 32.dp, y = (-32).dp),
                tint = Color.White.copy(alpha = 0.1f)
            )

            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Your Current Location",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Surface(
                        color = if (isLive) Color(0xFF10B981) else Color(0xFFF59E0B),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isLive) "LIVE GPS" else "GPS STANDBY",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    locationName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF83BAD6)
                )

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onShareLocation,
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("SHARE GPS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }

                    OutlinedButton(
                        onClick = onCopyCoords,
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("COORDS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }
                }
            }
        }
    }
}
