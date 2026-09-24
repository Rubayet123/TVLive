package io.github.rubayet123.tvlive.ui.settings

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rubayet123.tvlive.data.StreamHealthConfig
import io.github.rubayet123.tvlive.data.StreamHealthManager
import io.github.rubayet123.tvlive.util.AppTheme
import io.github.rubayet123.tvlive.util.DeviceUtils
import io.github.rubayet123.tvlive.util.ThemeManager

class StreamHealthSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceUtils.setupOrientationForDevice(this)

        setContent {
            StreamHealthSettingsScreen(onBack = { finish() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamHealthSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val currentTheme = remember { ThemeManager.getSelectedTheme(context) }

    var autoSwitch by remember { mutableStateOf(StreamHealthConfig.isAutoSwitchEnabled(context)) }
    var failoverMode by remember { mutableStateOf(StreamHealthConfig.getFailoverMode(context)) }
    var fallbackStrategy by remember { mutableStateOf(StreamHealthConfig.getFallbackStrategy(context)) }
    var failoverHud by remember { mutableStateOf(StreamHealthConfig.getFailoverHudMode(context)) }
    var autoSkipDead by remember { mutableStateOf(StreamHealthConfig.isAutoSkipDeadEnabled(context)) }

    var watchdogEnabled by remember { mutableStateOf(StreamHealthConfig.isWatchdogEnabled(context)) }
    var stallTimeoutSec by remember { mutableStateOf(StreamHealthConfig.getStallTimeoutSec(context)) }

    var autoReconnect by remember { mutableStateOf(StreamHealthConfig.isAutoReconnectEnabled(context)) }
    var failureAction by remember { mutableStateOf(StreamHealthConfig.getFailureAction(context)) }

    var bufferProfile by remember { mutableStateOf(StreamHealthConfig.getBufferProfile(context)) }
    var blacklistCooldownMin by remember { mutableStateOf(StreamHealthConfig.getBlacklistCooldownMin(context)) }

    // Dialog selection states
    var showFailoverModeDialog by remember { mutableStateOf(false) }
    var showStrategyDialog by remember { mutableStateOf(false) }
    var showHudDialog by remember { mutableStateOf(false) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showActionDialog by remember { mutableStateOf(false) }
    var showBufferDialog by remember { mutableStateOf(false) }
    var showCooldownDialog by remember { mutableStateOf(false) }

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
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(currentTheme.cardBg)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = currentTheme.primaryText
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Stream Health & Failover",
                                color = currentTheme.primaryText,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Configure smart auto-switching, watchdog timers, and buffer profiles",
                                color = currentTheme.secondaryText,
                                fontSize = 13.sp
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Preset Quick-Profiles
                Text(
                    text = "Quick Presets",
                    color = currentTheme.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetChip(
                        title = "Fast Zapping",
                        subtitle = "3s timeout",
                        currentTheme = currentTheme,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            StreamHealthConfig.applyPreset(context, "FAST_ZAPPING")
                            autoSwitch = StreamHealthConfig.isAutoSwitchEnabled(context)
                            watchdogEnabled = StreamHealthConfig.isWatchdogEnabled(context)
                            stallTimeoutSec = StreamHealthConfig.getStallTimeoutSec(context)
                            bufferProfile = StreamHealthConfig.getBufferProfile(context)
                            autoSkipDead = StreamHealthConfig.isAutoSkipDeadEnabled(context)
                            Toast.makeText(context, "Fast Zapping profile applied", Toast.LENGTH_SHORT).show()
                        }
                    )
                    PresetChip(
                        title = "Balanced",
                        subtitle = "Recommended",
                        currentTheme = currentTheme,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            StreamHealthConfig.applyPreset(context, "BALANCED")
                            autoSwitch = StreamHealthConfig.isAutoSwitchEnabled(context)
                            watchdogEnabled = StreamHealthConfig.isWatchdogEnabled(context)
                            stallTimeoutSec = StreamHealthConfig.getStallTimeoutSec(context)
                            bufferProfile = StreamHealthConfig.getBufferProfile(context)
                            autoSkipDead = StreamHealthConfig.isAutoSkipDeadEnabled(context)
                            Toast.makeText(context, "Balanced profile applied", Toast.LENGTH_SHORT).show()
                        }
                    )
                    PresetChip(
                        title = "Anti-Stutter",
                        subtitle = "Weak Wi-Fi",
                        currentTheme = currentTheme,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            StreamHealthConfig.applyPreset(context, "HIGH_STABILITY")
                            autoSwitch = StreamHealthConfig.isAutoSwitchEnabled(context)
                            watchdogEnabled = StreamHealthConfig.isWatchdogEnabled(context)
                            stallTimeoutSec = StreamHealthConfig.getStallTimeoutSec(context)
                            bufferProfile = StreamHealthConfig.getBufferProfile(context)
                            autoSkipDead = StreamHealthConfig.isAutoSkipDeadEnabled(context)
                            Toast.makeText(context, "Anti-Stutter profile applied", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // Section 1: Auto-Failover
                SettingSectionCard(title = "Multi-Source Auto-Failover", currentTheme = currentTheme) {
                    SettingToggleRow(
                        title = "Auto-Switch on Error",
                        subtitle = "Automatically failover to alternative stream sources when playback fails",
                        checked = autoSwitch,
                        onCheckedChange = {
                            autoSwitch = it
                            StreamHealthConfig.setAutoSwitchEnabled(context, it)
                        },
                        currentTheme = currentTheme
                    )

                    HorizontalDivider(color = currentTheme.primaryText.copy(alpha = 0.08f))

                    SettingClickableRow(
                        title = "Failover Checking Mode",
                        value = when (failoverMode) {
                            "CONCURRENT" -> "Concurrent Multi-Link Probe (Ultra-Fast)"
                            else -> "Sequential ExoPlayer Retry (Highly Reliable)"
                        },
                        onClick = { showFailoverModeDialog = true },
                        currentTheme = currentTheme
                    )

                    HorizontalDivider(color = currentTheme.primaryText.copy(alpha = 0.08f))

                    SettingClickableRow(
                        title = "Preferred Fallback Strategy",
                        value = when (fallbackStrategy) {
                            "QUALITY" -> "Match Highest Quality (4K/1080p first)"
                            "PROVIDER_ORDER" -> "Provider Priority Order"
                            else -> "Balanced"
                        },
                        onClick = { showStrategyDialog = true },
                        currentTheme = currentTheme
                    )

                    HorizontalDivider(color = currentTheme.primaryText.copy(alpha = 0.08f))

                    SettingClickableRow(
                        title = "Failover HUD Notifications",
                        value = when (failoverHud) {
                            "BANNER" -> "Subtle Toast / Banner"
                            "SILENT" -> "Silent (No HUD Alerts)"
                            else -> "Subtle Toast / Banner"
                        },
                        onClick = { showHudDialog = true },
                        currentTheme = currentTheme
                    )

                    HorizontalDivider(color = currentTheme.primaryText.copy(alpha = 0.08f))

                    SettingToggleRow(
                        title = "Auto-Skip Dead Channels",
                        subtitle = "Skip channels during zapping if all their stream sources are blacklisted",
                        checked = autoSkipDead,
                        onCheckedChange = {
                            autoSkipDead = it
                            StreamHealthConfig.setAutoSkipDeadEnabled(context, it)
                        },
                        currentTheme = currentTheme
                    )
                }

                // Section 2: Watchdog & Stall Detection
                SettingSectionCard(title = "Buffer Watchdog & Stall Detection", currentTheme = currentTheme) {
                    SettingToggleRow(
                        title = "Buffer Stall Watchdog",
                        subtitle = "Detects and recovers from silent stream freezes and endless buffering",
                        checked = watchdogEnabled,
                        onCheckedChange = {
                            watchdogEnabled = it
                            StreamHealthConfig.setWatchdogEnabled(context, it)
                        },
                        currentTheme = currentTheme
                    )

                    HorizontalDivider(color = currentTheme.primaryText.copy(alpha = 0.08f))

                    SettingClickableRow(
                        title = "Stall Timeout Threshold",
                        value = "$stallTimeoutSec seconds",
                        onClick = { showTimeoutDialog = true },
                        currentTheme = currentTheme
                    )
                }

                // Section 3: Single-Stream Reconnect
                SettingSectionCard(title = "Single-Stream Recovery & Reconnection", currentTheme = currentTheme) {
                    SettingToggleRow(
                        title = "Auto-Reconnect on Disconnect",
                        subtitle = "Automatically attempt a fast reconnect for single-source channels on network drop",
                        checked = autoReconnect,
                        onCheckedChange = {
                            autoReconnect = it
                            StreamHealthConfig.setAutoReconnectEnabled(context, it)
                        },
                        currentTheme = currentTheme
                    )

                    HorizontalDivider(color = currentTheme.primaryText.copy(alpha = 0.08f))

                    SettingClickableRow(
                        title = "Action on Total Stream Failure",
                        value = when (failureAction) {
                            "NEXT_CHANNEL" -> "Auto-Zap to Next Channel"
                            "EXIT" -> "Return to Channel Grid / Home"
                            else -> "Show Error Toast / Dialog"
                        },
                        onClick = { showActionDialog = true },
                        currentTheme = currentTheme
                    )
                }

                // Section 4: Buffer Engine & Blacklist
                SettingSectionCard(title = "Buffer Engine & Health Memory", currentTheme = currentTheme) {
                    SettingClickableRow(
                        title = "Playback Buffer Profile",
                        value = when (bufferProfile) {
                            "FAST_ZAPPING" -> "Fast Zapping (Low Latency / 0.5s)"
                            "HIGH_STABILITY" -> "High Stability (5.0s Buffer)"
                            else -> "Balanced (2.0s Buffer)"
                        },
                        onClick = { showBufferDialog = true },
                        currentTheme = currentTheme
                    )

                    HorizontalDivider(color = currentTheme.primaryText.copy(alpha = 0.08f))

                    SettingClickableRow(
                        title = "Dead Stream Cooldown",
                        value = if (blacklistCooldownMin == 0) "Disabled" else "$blacklistCooldownMin Minutes",
                        onClick = { showCooldownDialog = true },
                        currentTheme = currentTheme
                    )

                    HorizontalDivider(color = currentTheme.primaryText.copy(alpha = 0.08f))

                    Button(
                        onClick = {
                            StreamHealthManager.resetAllStats()
                            Toast.makeText(context, "Stream health & blacklists cleared", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = currentTheme.cardBg),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = currentTheme.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Health & Stream Stats", color = currentTheme.accent, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Dialog: Failover Mode
    if (showFailoverModeDialog) {
        val options = listOf(
            "SEQUENTIAL" to "Sequential ExoPlayer Retry (Recommended for Live TV - Maximum Compatibility)",
            "CONCURRENT" to "Concurrent Multi-Link Probe (Fast Parallel Probe)"
        )
        SingleChoiceDialog(
            title = "Failover Checking Mode",
            options = options,
            selectedKey = failoverMode,
            onSelect = {
                failoverMode = it
                StreamHealthConfig.setFailoverMode(context, it)
                showFailoverModeDialog = false
            },
            onDismiss = { showFailoverModeDialog = false },
            currentTheme = currentTheme
        )
    }

    // Dialog: Fallback Strategy
    if (showStrategyDialog) {
        val options = listOf(
            "PROVIDER_ORDER" to "Provider Priority Order (Default)",
            "QUALITY" to "Match Highest Quality (4K / 1080p first)"
        )
        SingleChoiceDialog(
            title = "Fallback Strategy",
            options = options,
            selectedKey = fallbackStrategy,
            onSelect = {
                fallbackStrategy = it
                StreamHealthConfig.setFallbackStrategy(context, it)
                showStrategyDialog = false
            },
            onDismiss = { showStrategyDialog = false },
            currentTheme = currentTheme
        )
    }

    // Dialog: Failover HUD
    if (showHudDialog) {
        val options = listOf(
            "BANNER" to "Subtle Toast / Banner",
            "SILENT" to "Silent (No HUD Alerts)"
        )
        SingleChoiceDialog(
            title = "Failover HUD Mode",
            options = options,
            selectedKey = failoverHud,
            onSelect = {
                failoverHud = it
                StreamHealthConfig.setFailoverHudMode(context, it)
                showHudDialog = false
            },
            onDismiss = { showHudDialog = false },
            currentTheme = currentTheme
        )
    }

    // Dialog: Stall Timeout
    if (showTimeoutDialog) {
        val options = listOf(
            3 to "3 Seconds (Aggressive / Fast Failover)",
            6 to "6 Seconds (Recommended / Balanced)",
            10 to "10 Seconds (Forgiving / Slow Wi-Fi)",
            15 to "15 Seconds (High Latency Feeds)"
        )
        SingleChoiceIntDialog(
            title = "Stall Timeout Threshold",
            options = options,
            selectedValue = stallTimeoutSec,
            onSelect = {
                stallTimeoutSec = it
                StreamHealthConfig.setStallTimeoutSec(context, it)
                showTimeoutDialog = false
            },
            onDismiss = { showTimeoutDialog = false },
            currentTheme = currentTheme
        )
    }

    // Dialog: Action on Total Failure
    if (showActionDialog) {
        val options = listOf(
            "DIALOG" to "Show Error Toast / Dialog",
            "NEXT_CHANNEL" to "Auto-Zap to Next Channel",
            "EXIT" to "Return to Channel Grid / Home"
        )
        SingleChoiceDialog(
            title = "Action on Total Failure",
            options = options,
            selectedKey = failureAction,
            onSelect = {
                failureAction = it
                StreamHealthConfig.setFailureAction(context, it)
                showActionDialog = false
            },
            onDismiss = { showActionDialog = false },
            currentTheme = currentTheme
        )
    }

    // Dialog: Buffer Profile
    if (showBufferDialog) {
        val options = listOf(
            "FAST_ZAPPING" to "Fast Zapping (Low Latency / 0.5s)",
            "BALANCED" to "Balanced (2.0s Buffer)",
            "HIGH_STABILITY" to "High Stability (5.0s Buffer)"
        )
        SingleChoiceDialog(
            title = "Playback Buffer Profile",
            options = options,
            selectedKey = bufferProfile,
            onSelect = {
                bufferProfile = it
                StreamHealthConfig.setBufferProfile(context, it)
                showBufferDialog = false
            },
            onDismiss = { showBufferDialog = false },
            currentTheme = currentTheme
        )
    }

    // Dialog: Cooldown
    if (showCooldownDialog) {
        val options = listOf(
            0 to "Disabled",
            1 to "1 Minute",
            5 to "5 Minutes (Recommended)",
            15 to "15 Minutes"
        )
        SingleChoiceIntDialog(
            title = "Dead Stream Cooldown",
            options = options,
            selectedValue = blacklistCooldownMin,
            onSelect = {
                blacklistCooldownMin = it
                StreamHealthConfig.setBlacklistCooldownMin(context, it)
                showCooldownDialog = false
            },
            onDismiss = { showCooldownDialog = false },
            currentTheme = currentTheme
        )
    }
}

@Composable
fun PresetChip(
    title: String,
    subtitle: String,
    currentTheme: AppTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) currentTheme.accent else currentTheme.primaryText.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .focusable(),
        colors = CardDefaults.cardColors(containerColor = if (isFocused) currentTheme.accent.copy(alpha = 0.2f) else currentTheme.cardBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = currentTheme.primaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = currentTheme.secondaryText,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun SettingSectionCard(
    title: String,
    currentTheme: AppTheme,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = currentTheme.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = currentTheme.cardBg),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    currentTheme: AppTheme
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onCheckedChange(!checked) }
            .focusable()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isFocused) currentTheme.accent else currentTheme.primaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = currentTheme.secondaryText,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = currentTheme.accent,
                uncheckedThumbColor = currentTheme.secondaryText,
                uncheckedTrackColor = currentTheme.cardBg
            )
        )
    }
}

@Composable
fun SettingClickableRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    currentTheme: AppTheme
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .focusable()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = if (isFocused) currentTheme.accent else currentTheme.primaryText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = currentTheme.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SingleChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    currentTheme: AppTheme
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = currentTheme.cardBg,
        title = {
            Text(text = title, color = currentTheme.primaryText, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (key, label) ->
                    val isSelected = (key == selectedKey)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) currentTheme.accent.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { onSelect(key) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(key) },
                            colors = RadioButtonDefaults.colors(selectedColor = currentTheme.accent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            color = if (isSelected) currentTheme.accent else currentTheme.primaryText,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = currentTheme.accent)
            }
        }
    )
}

@Composable
fun SingleChoiceIntDialog(
    title: String,
    options: List<Pair<Int, String>>,
    selectedValue: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    currentTheme: AppTheme
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = currentTheme.cardBg,
        title = {
            Text(text = title, color = currentTheme.primaryText, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (value, label) ->
                    val isSelected = (value == selectedValue)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) currentTheme.accent.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { onSelect(value) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(value) },
                            colors = RadioButtonDefaults.colors(selectedColor = currentTheme.accent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            color = if (isSelected) currentTheme.accent else currentTheme.primaryText,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = currentTheme.accent)
            }
        }
    )
}
