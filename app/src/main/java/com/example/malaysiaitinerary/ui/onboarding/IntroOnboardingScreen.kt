package com.example.malaysiaitinerary.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.malaysiaitinerary.ui.theme.ExplorerPrimary
import com.example.malaysiaitinerary.ui.theme.ExplorerPrimaryContainer
import kotlinx.coroutines.launch

data class OnboardingSlide(
    val title: String,
    val description: String,
    val exampleTitle: String,
    val exampleContent: String,
    val icon: ImageVector,
    val badgeText: String
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun IntroOnboardingScreen(
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val slides = listOf(
        OnboardingSlide(
            title = "Welcome to Explorer AI",
            description = "Your 100% offline, privacy-first travel companion. Keep all flight tickets, hotel vouchers, and trip plans securely on your device.",
            exampleTitle = "Privacy-First Guarantee",
            exampleContent = "No cloud tracking or registration required. Your personal documents and travel logs remain strictly local.",
            icon = Icons.Default.TravelExplore,
            badgeText = "Offline AI App"
        ),
        OnboardingSlide(
            title = "On-Device Gemma 4 AI",
            description = "Chat naturally with local Gemma 4 (E2B / E4B) models running directly on your phone using MediaPipe GenAI.",
            exampleTitle = "Example Voice Command",
            exampleContent = "\"Gemma, add a dinner activity on March 24 at 8 PM at Jalan Alor Market with street food options.\"",
            icon = Icons.Default.Psychology,
            badgeText = "Gemma 4 Engine"
        ),
        OnboardingSlide(
            title = "Smart Ticket & PDF Ingestion",
            description = "Upload PDF tickets, boarding passes, or hotel screenshots. On-device ML Kit OCR extracts structured dates, times, and costs.",
            exampleTitle = "Example Ingestion",
            exampleContent = "Upload 'Flight_MH123.pdf' -> Gemma automatically creates a Briefcase ticket record and logs a 150 MYR flight expense.",
            icon = Icons.Default.FileUpload,
            badgeText = "Multimodal OCR"
        ),
        OnboardingSlide(
            title = "Itinerary & Location Photos",
            description = "View your trip timeline with automatically fetched location images powered by Wikimedia Commons (100% free, no API keys).",
            exampleTitle = "Example Landmark Lookup",
            exampleContent = "Search 'Petronas Twin Towers' -> View high-resolution photos, dietary recommendations (Veg / Non-Veg), and Google Maps links.",
            icon = Icons.Default.Place,
            badgeText = "Free Media Fetcher"
        ),
        OnboardingSlide(
            title = "Gemini Cloud & Search Grounding",
            description = "Optional Cloud mode allows you to use your personal Gemini API key with live Google Search Grounding for new itineraries.",
            exampleTitle = "Example Trip Generator",
            exampleContent = "Enter 'Penang, 3 Days, Street Food' -> Gemini generates a complete 3-day itinerary saved directly into your local database.",
            icon = Icons.Default.AutoAwesome,
            badgeText = "Search Grounding"
        )
    )

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    TextButton(onClick = onOnboardingComplete) {
                        Text(
                            text = "Skip Intro",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                val slide = slides[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) {
                        Text(
                            text = slide.badgeText,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    // Large Icon
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = slide.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(52.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = slide.title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = slide.description,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Example Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = slide.exampleTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = slide.exampleContent,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Slide Indicator Dots
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                repeat(slides.size) { iteration ->
                    val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(if (pagerState.currentPage == iteration) 12.dp else 8.dp)
                    )
                }
            }

            // Bottom Navigation Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                    ) {
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(80.dp))
                }

                Button(
                    onClick = {
                        if (pagerState.currentPage < slides.size - 1) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onOnboardingComplete()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (pagerState.currentPage == slides.size - 1) "Get Started" else "Next",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        if (pagerState.currentPage == slides.size - 1) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
