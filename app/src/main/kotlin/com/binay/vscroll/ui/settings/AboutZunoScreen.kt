package com.binay.vscroll.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dedicated About ZUNO screen presenting comprehensive product information,
 * core verified features, offline privacy specifications, open-source licenses,
 * support links, and technical build parameters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutZunoScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    var isBuildInfoExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "About ZUNO",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // A. Hero App Identity Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "ZUNO",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Version 1.0 (Build 1)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Created by Binay Kushwaha",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "ZUNO is an offline-first video discovery and playback application that helps you explore, organize, and enjoy your personal video library through an immersive short-form video experience.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 21.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // B. Core Features Section
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    SectionHeader(
                        icon = Icons.Default.Star,
                        title = "Core Features"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val features = listOf(
                        "Immersive Vertical Video Playback" to "Full-screen vertical feed powered by AndroidX Media3 ExoPlayer with loop playback and instant transitions.",
                        "Local Video Discovery" to "Automatic MediaStore indexing scanning all device video formats without cloud dependencies.",
                        "Emoji Reactions & Likes" to "Double-tap animated heart pop and 6 expressive reactions (❤️ 👍 😂 😮 😢 🔥) saved in local Room DB.",
                        "Saved Collections" to "Instagram-inspired collection management with custom playlists, 1:1 square cover art, and multi-collection tagging.",
                        "Private Video Notes" to "Personal offline annotations, study notes, or timestamps attached directly to any local video.",
                        "Daily Viewing Activity" to "7-day interactive bar chart, daily screen-time drilldown, chronological session timeline, and break reminders.",
                        "Customizable Themes" to "5 curated themes (ZUNO, Sunset Glow, Fresh Mint, Lavender Dream, Midnight) following Material 3 guidelines.",
                        "Local Video Organization" to "Physical folder grouping, batch multi-selection, file renaming, and custom collection management.",
                        "Native Video Sharing" to "Direct sharing through Android's system sharesheet without network transmission or external uploads."
                    )

                    features.forEachIndexed { index, (title, desc) ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "•  $title",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(start = 14.dp)
                            )
                        }
                        if (index < features.lastIndex) {
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }

            // C. Privacy and Permissions Section
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    SectionHeader(
                        icon = Icons.Default.Lock,
                        title = "Privacy & Permissions"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val privacyPoints = listOf(
                        "Why Video Access Is Required" to "ZUNO requests READ_MEDIA_VIDEO / READ_EXTERNAL_STORAGE strictly to discover and display videos already stored on your device.",
                        "Read-Only Media Access" to "Local videos are accessed read-only through Android's system MediaStore provider. Original video files are never modified, moved, or deleted when managing collections, bookmarks, reactions, or notes.",
                        "Offline Viewing History" to "Viewing duration and session metrics are recorded strictly offline in your device's local SQLite Room database (vscroll_database.db). No history data ever leaves your device.",
                        "Local Interaction Storage" to "Likes, reactions, bookmarks, private notes, and collections are persisted locally in on-device database tables with full offline privacy.",
                        "100% Offline Architecture" to "ZUNO does not request the INTERNET permission in its Android manifest and contains zero remote analytics, telemetry, or third-party tracking SDKs."
                    )

                    privacyPoints.forEachIndexed { index, (title, desc) ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                        if (index < privacyPoints.lastIndex) {
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }

            // D. Open-Source Licenses & Acknowledgements
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    SectionHeader(
                        icon = Icons.Default.Code,
                        title = "Open-Source Acknowledgements"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val libraries = listOf(
                        "AndroidX Media3 ExoPlayer" to "High-performance video decoding, audio attributes, and surface rendering (Apache 2.0)",
                        "Jetpack Compose & Material 3" to "Modern declarative UI toolkit and dynamic theming architecture (Apache 2.0)",
                        "AndroidX Room Database" to "Local SQLite object-relational mapping and schema migrations (Apache 2.0)",
                        "Kotlinx Coroutines & Flow" to "Asynchronous reactive stream processing and concurrency (Apache 2.0)",
                        "AndroidX DataStore Preferences" to "Type-safe asynchronous key-value preference storage (Apache 2.0)",
                        "AndroidX Core KTX & Lifecycle" to "Lifecycle-aware state observation and Android framework bindings (Apache 2.0)"
                    )

                    libraries.forEachIndexed { index, (name, role) ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = role,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (index < libraries.lastIndex) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            // E. Support & Feedback Section
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    SectionHeader(
                        icon = Icons.Default.VerifiedUser,
                        title = "Support & Open Source"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "ZUNO is an offline-first, independent open-source project created and maintained by Binay Kushwaha.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "For source code, issue tracking, and contributions, visit the official repository:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "https://github.com/binaykushwaha1244/ZUNO",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            // F. Technical & Build Information (Expandable)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isBuildInfoExpanded = !isBuildInfoExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(
                            icon = Icons.Default.Info,
                            title = "Technical Build Parameters"
                        )
                        Icon(
                            imageVector = if (isBuildInfoExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isBuildInfoExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = isBuildInfoExpanded) {
                        Column(modifier = Modifier.padding(top = 14.dp)) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            val technicalParams = listOf(
                                "Application ID" to "com.binay.vscroll",
                                "Version Name" to "1.0",
                                "Version Code" to "1",
                                "Compile SDK" to "37 (Android 15+)",
                                "Target SDK" to "37 (Android 15+)",
                                "Minimum SDK" to "26 (Android 8.0 Oreo)",
                                "Database Schema" to "Version 5 (Room SQLite)",
                                "Network Permission" to "None (Omitted from manifest)",
                                "Physical Device Verified" to "OnePlus Nord CE5 (OxygenOS / API 35)"
                            )

                            technicalParams.forEach { (label, value) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
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
private fun SectionHeader(
    icon: ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
