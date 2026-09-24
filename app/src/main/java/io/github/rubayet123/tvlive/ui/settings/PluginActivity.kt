package io.github.rubayet123.tvlive.ui.settings

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rubayet123.tvlive.scraper.PluginScraperManager
import io.github.rubayet123.tvlive.ui.tv.TvIconButton
import io.github.rubayet123.tvlive.ui.tv.tvFocusable
import io.github.rubayet123.tvlive.util.AppTheme
import io.github.rubayet123.tvlive.util.DeviceUtils
import io.github.rubayet123.tvlive.util.ThemeManager
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PluginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceUtils.setupOrientationForDevice(this)

        setContent {
            PluginSettingsScreen(onBack = { finish() })
        }
    }
}

data class PluginItemData(
    val key: String,
    val title: String,
    val subtitle: String,
    val autoSyncPrefKey: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val currentTheme = remember { ThemeManager.getSelectedTheme(context) }
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("plugin_prefs", Context.MODE_PRIVATE) }

    val plugins = remember {
        listOf(
            PluginItemData(
                key = "redforce",
                title = "Redforce ISP TV",
                subtitle = "High-performance alternative ISP playlist scraper",
                autoSyncPrefKey = "auto_scrape_redforce"
            ),
            PluginItemData(
                key = "splex",
                title = "Splex ISP TV",
                subtitle = "Fast multi-category ISP channel scraper",
                autoSyncPrefKey = "auto_scrape_splex"
            ),
            PluginItemData(
                key = "roarzone",
                title = "Roarzone ISP TV",
                subtitle = "Comprehensive sports & entertainment provider",
                autoSyncPrefKey = "auto_scrape_roarzone"
            ),
            PluginItemData(
                key = "idealtv",
                title = "Ideal TV (172.16.60.2)",
                subtitle = "Sports, Bangla, Hindi & English live TV channel scraper",
                autoSyncPrefKey = "auto_scrape_idealtv"
            ),
            PluginItemData(
                key = "orbittv",
                title = "Orbit TV (172.19.17.3:8090)",
                subtitle = "Sports, Bangla, Hindi, News & English live TV channel scraper",
                autoSyncPrefKey = "auto_scrape_orbittv"
            ),
            PluginItemData(
                key = "local_isp",
                title = "BAS TV (10.99.99.99)",
                subtitle = "Standard local network IPTV stream discovery",
                autoSyncPrefKey = "auto_scrape_local_isp"
            ),
            PluginItemData(
                key = "damitv",
                title = "DAMITV Global Live TV",
                subtitle = "Worldwide Live TV & Sports channels with native TS unwrapper",
                autoSyncPrefKey = "auto_scrape_damitv"
            )
        )
    }

    var activeScrapingKey by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = currentTheme.primaryBg
    ) {
        Scaffold(
            containerColor = currentTheme.primaryBg,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    currentTheme.topBar,
                                    currentTheme.primaryBg
                                )
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            onClick = onBack,
                            size = 42.dp,
                            defaultBgColor = currentTheme.cardBg,
                            focusedBgColor = Color.White,
                            focusedIconColor = Color(0xFF0F172A),
                            shape = CircleShape
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Channel Plugins",
                                color = currentTheme.primaryText,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Manage and sync ISP scraper plugins & live channel catalogs",
                                color = currentTheme.secondaryText,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // Active status banner
                AnimatedVisibility(visible = activeScrapingKey != null || statusMessage != null) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (activeScrapingKey != null) currentTheme.accent.copy(alpha = 0.15f) else currentTheme.cardBg
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .border(
                                width = 1.dp,
                                color = if (activeScrapingKey != null) currentTheme.accent else currentTheme.secondaryText.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(14.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (activeScrapingKey != null) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = currentTheme.accent,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = currentTheme.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = statusMessage ?: "Syncing channels...",
                                color = currentTheme.primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Text(
                    text = "AVAILABLE SCRAPER PROVIDERS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = currentTheme.accent,
                    modifier = Modifier.padding(bottom = 10.dp, start = 2.dp)
                )

                plugins.forEach { plugin ->
                    var isAutoSyncEnabled by remember(plugin.key, refreshTrigger) {
                        mutableStateOf(prefs.getBoolean(plugin.autoSyncPrefKey, false))
                    }
                    val lastSyncMs = remember(plugin.key, refreshTrigger) {
                        PluginScraperManager.getLastScrapeTime(context, plugin.key)
                    }
                    val isScrapingThis = activeScrapingKey == plugin.key

                    PremiumPluginCard(
                        plugin = plugin,
                        isAutoSync = isAutoSyncEnabled,
                        lastSyncMs = lastSyncMs,
                        isScraping = isScrapingThis,
                        isAnyScraping = activeScrapingKey != null,
                        currentTheme = currentTheme,
                        onAutoSyncChange = { checked ->
                            prefs.edit().putBoolean(plugin.autoSyncPrefKey, checked).apply()
                            isAutoSyncEnabled = checked
                            refreshTrigger++
                        },
                        onSyncClick = {
                            activeScrapingKey = plugin.key
                            statusMessage = "Scraping ${plugin.title}..."
                            coroutineScope.launch {
                                try {
                                    val success = PluginScraperManager.scrapeAndSavePlugin(context, plugin.key)
                                    if (success) {
                                        PluginScraperManager.reloadMasterPlaylist(context)
                                        statusMessage = "✓ ${plugin.title} updated successfully!"
                                        Toast.makeText(context, "${plugin.title} sync complete!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        statusMessage = "✗ Failed to sync ${plugin.title}."
                                        Toast.makeText(context, "${plugin.title} sync failed.", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    statusMessage = "Error: ${e.message}"
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    activeScrapingKey = null
                                    refreshTrigger++
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun PremiumPluginCard(
    plugin: PluginItemData,
    isAutoSync: Boolean,
    lastSyncMs: Long,
    isScraping: Boolean,
    isAnyScraping: Boolean,
    currentTheme: AppTheme,
    onAutoSyncChange: (Boolean) -> Unit,
    onSyncClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    // Dynamic border & background colors based on state
    val borderColor = when {
        isFocused -> currentTheme.accent
        isAutoSync -> currentTheme.accent.copy(alpha = 0.6f)
        else -> Color.White.copy(alpha = 0.08f)
    }

    val borderWidth = if (isFocused || isAutoSync) 1.5.dp else 1.dp

    val cardBackground = if (isFocused) {
        currentTheme.cardBg.copy(alpha = 0.95f)
    } else {
        currentTheme.cardBg
    }

    // Format last synced text
    val lastSyncText = if (lastSyncMs <= 0L) {
        "Never synced"
    } else {
        val diffMs = System.currentTimeMillis() - lastSyncMs
        val diffMins = TimeUnit.MILLISECONDS.toMinutes(diffMs)
        when {
            diffMins < 1 -> "Synced just now"
            diffMins < 60 -> "Synced $diffMins min${if (diffMins > 1) "s" else ""} ago"
            else -> {
                val hours = diffMins / 60
                "Synced $hours hr${if (hours > 1) "s" else ""} ago"
            }
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable(
                shape = RoundedCornerShape(18.dp),
                focusedBorderColor = currentTheme.accent,
                unfocusedBorderColor = if (isAutoSync) currentTheme.accent.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f),
                focusedBorderWidth = 3.dp,
                scaleOnFocus = 1.02f
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Title + Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title and description
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plugin.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentTheme.primaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = plugin.subtitle,
                        fontSize = 12.sp,
                        color = currentTheme.secondaryText,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Badge Pill
                StatusBadgePill(
                    isActive = isAutoSync,
                    theme = currentTheme
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub-info: Last Synced indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "• $lastSyncText",
                    fontSize = 12.sp,
                    color = if (lastSyncMs > 0L) currentTheme.secondaryText else currentTheme.secondaryText.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.06f),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Actions: Sync Button & Auto-Sync Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // High-contrast modern Sync Button with TV focus
                Button(
                    onClick = onSyncClick,
                    enabled = !isAnyScraping,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = currentTheme.accent,
                        disabledContainerColor = currentTheme.accent.copy(alpha = 0.4f),
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.6f)
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.tvFocusable(
                        shape = RoundedCornerShape(10.dp),
                        focusedBorderColor = Color.White,
                        focusedBorderWidth = 2.5.dp,
                        scaleOnFocus = 1.08f
                    )
                ) {
                    if (isScraping) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Syncing...",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sync Now",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Modern Switch with Label & TV focus
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onAutoSyncChange(!isAutoSync) }
                        .tvFocusable(
                            shape = RoundedCornerShape(20.dp),
                            focusedBorderColor = Color.White,
                            focusedBorderWidth = 2.dp,
                            scaleOnFocus = 1.08f
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Auto Sync",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isAutoSync) currentTheme.primaryText else currentTheme.secondaryText
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isAutoSync,
                        onCheckedChange = onAutoSyncChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = currentTheme.accent,
                            uncheckedThumbColor = currentTheme.secondaryText,
                            uncheckedTrackColor = currentTheme.secondaryText.copy(alpha = 0.2f),
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadgePill(
    isActive: Boolean,
    theme: AppTheme
) {
    val backgroundColor = if (isActive) {
        Color(0xFF00E676).copy(alpha = 0.15f)
    } else {
        Color.White.copy(alpha = 0.07f)
    }

    val textColor = if (isActive) {
        Color(0xFF00E676)
    } else {
        theme.secondaryText.copy(alpha = 0.7f)
    }

    val dotColor = if (isActive) Color(0xFF00E676) else theme.secondaryText.copy(alpha = 0.5f)

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) Color(0xFF00E676).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (isActive) "ACTIVE" else "INACTIVE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                letterSpacing = 0.5.sp
            )
        }
    }
}
