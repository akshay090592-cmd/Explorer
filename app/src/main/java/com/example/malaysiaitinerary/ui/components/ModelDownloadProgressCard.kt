package com.example.malaysiaitinerary.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.malaysiaitinerary.util.ModelDownloadProgress
import com.example.malaysiaitinerary.ui.theme.ExplorerPrimaryContainer

@Composable
fun ModelDownloadProgressCard(
    progress: ModelDownloadProgress?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (progress == null) return

    val animatedPercent by animateFloatAsState(
        targetValue = progress.progressPercent.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
        label = "download_progress"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = when {
                    progress.isCompleted && progress.isSuccess -> Color(0xFF10B981).copy(alpha = 0.5f)
                    progress.isCompleted && !progress.isSuccess -> Color(0xFFEF4444).copy(alpha = 0.5f)
                    else -> ExplorerPrimaryContainer.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        color = when {
            progress.isCompleted && progress.isSuccess -> Color(0xFFF0FDF4)
            progress.isCompleted && !progress.isSuccess -> Color(0xFFFEF2F2)
            else -> MaterialTheme.colorScheme.surface
        },
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                progress.isCompleted && progress.isSuccess -> Color(0xFF10B981).copy(alpha = 0.15f)
                                progress.isCompleted && !progress.isSuccess -> Color(0xFFEF4444).copy(alpha = 0.15f)
                                else -> ExplorerPrimaryContainer.copy(alpha = 0.15f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (progress.isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(42.dp),
                            color = ExplorerPrimaryContainer,
                            strokeWidth = 3.dp
                        )
                    }

                    Icon(
                        imageVector = when {
                            progress.isCompleted && progress.isSuccess -> Icons.Default.CheckCircle
                            progress.isCompleted && !progress.isSuccess -> Icons.Default.ErrorOutline
                            else -> Icons.Default.Download
                        },
                        contentDescription = null,
                        tint = when {
                            progress.isCompleted && progress.isSuccess -> Color(0xFF10B981)
                            progress.isCompleted && !progress.isSuccess -> Color(0xFFEF4444)
                            else -> ExplorerPrimaryContainer
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            progress.isCompleted && progress.isSuccess -> "Model Downloaded!"
                            progress.isCompleted && !progress.isSuccess -> "Download Failed"
                            else -> "Downloading Gemma 4 Model..."
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = progress.statusText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (progress.isDownloading) {
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Cancel", fontSize = 12.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                    }
                } else {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (progress.isDownloading) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { animatedPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = ExplorerPrimaryContainer,
                    trackColor = ExplorerPrimaryContainer.copy(alpha = 0.2f)
                )
            }

            if (progress.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = progress.errorMessage,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFFDC2626))
                )
            }
        }
    }
}
