package io.github.rubayet123.tvlive.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rubayet123.tvlive.data.SourceRepository
import io.github.rubayet123.tvlive.model.Source
import io.github.rubayet123.tvlive.ui.tv.TvIconButton
import io.github.rubayet123.tvlive.ui.tv.tvFocusable
import io.github.rubayet123.tvlive.util.DeviceUtils
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ProviderSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceUtils.setupOrientationForDevice(this)

        setContent {
            ProviderSettingsScreen(onBack = { finish() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { SourceRepository(context) }
    val scope = rememberCoroutineScope()
    
    var sources by remember { mutableStateOf<List<Source>>(emptyList()) }
    var isSyncingAll by remember { mutableStateOf(false) }
    var syncingSourceUrl by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showGlobalSyncDialog by remember { mutableStateOf(false) }
    var sourceToEdit by remember { mutableStateOf<Source?>(null) }
    var sourceToDelete by remember { mutableStateOf<Source?>(null) }
    var sourceForInterval by remember { mutableStateOf<Source?>(null) }
    var groupDuplicatesEnabled by remember {
        mutableStateOf(io.github.rubayet123.tvlive.util.ChannelDeduplicator.isGroupDuplicatesEnabled(context))
    }

    fun refreshData() {
        sources = repository.getSources()
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F1117)
    ) {
        Scaffold(
            containerColor = Color(0xFF0F1117),
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF161A23),
                                    Color(0xFF0F1117)
                                )
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 12.dp)
                        ) {
                            TvIconButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                onClick = onBack,
                                size = 42.dp,
                                defaultBgColor = Color(0xFF1E2330),
                                focusedBgColor = Color.White,
                                focusedIconColor = Color(0xFF0F172A),
                                shape = CircleShape
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Providers",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Playlists & IPTV Sources",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Sync All Button
                            Button(
                                onClick = {
                                    if (!isSyncingAll) {
                                        isSyncingAll = true
                                        scope.launch {
                                            Toast.makeText(context, "Syncing all active providers...", Toast.LENGTH_SHORT).show()
                                            val (success, fail) = repository.syncAllSources()
                                            refreshData()
                                            isSyncingAll = false
                                            if (fail == 0 && success > 0) {
                                                Toast.makeText(context, "All $success providers updated successfully", Toast.LENGTH_SHORT).show()
                                            } else if (success > 0) {
                                                Toast.makeText(context, "$success updated, $fail failed", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Sync completed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.tvFocusable(
                                    shape = RoundedCornerShape(10.dp),
                                    focusedBorderColor = Color.White,
                                    focusedBorderWidth = 3.dp,
                                    scaleOnFocus = 1.08f
                                )
                            ) {
                                if (isSyncingAll) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color(0xFF10B981),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Sync All",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isSyncingAll) "Syncing..." else "Sync All",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Add Button
                            Button(
                                onClick = { showAddDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.tvFocusable(
                                    shape = RoundedCornerShape(10.dp),
                                    focusedBorderColor = Color.White,
                                    focusedBorderWidth = 3.dp,
                                    scaleOnFocus = 1.08f
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Provider",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Add",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (sources.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Providers Added",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add an M3U playlist link, local file, or Xtream Codes login to start watching channels.",
                            color = Color(0xFF9CA3AF),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.tvFocusable(
                                shape = RoundedCornerShape(12.dp),
                                focusedBorderColor = Color.White,
                                scaleOnFocus = 1.08f
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Your First Provider")
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Global Playlist Auto-Sync Schedule Banner
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161A23)),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFF2A3142)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvFocusable(
                                        shape = RoundedCornerShape(14.dp),
                                        focusedBorderColor = Color(0xFF10B981),
                                        unfocusedBorderColor = Color(0xFF2A3142),
                                        focusedBorderWidth = 3.dp,
                                        scaleOnFocus = 1.02f
                                    )
                                    .clickable {
                                        showGlobalSyncDialog = true
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = null,
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Auto-Sync Schedule",
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Set background update frequency for M3U playlists",
                                                color = Color(0xFF9CA3AF),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF1F2432),
                                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = "Configure",
                                            color = Color(0xFF10B981),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Smart Deduplication Banner
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161A23)),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFF2A3142)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvFocusable(
                                        shape = RoundedCornerShape(14.dp),
                                        focusedBorderColor = Color(0xFF60A5FA),
                                        unfocusedBorderColor = Color(0xFF2A3142),
                                        focusedBorderWidth = 3.dp,
                                        scaleOnFocus = 1.02f
                                    )
                                    .clickable {
                                        val newState = !groupDuplicatesEnabled
                                        groupDuplicatesEnabled = newState
                                        io.github.rubayet123.tvlive.util.ChannelDeduplicator.setGroupDuplicatesEnabled(context, newState)
                                        Toast.makeText(
                                            context,
                                            if (newState) "Channel Deduplication Enabled" else "Channel Deduplication Disabled",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFF60A5FA).copy(alpha = 0.15f),
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Share,
                                                    contentDescription = null,
                                                    tint = Color(0xFF60A5FA),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Smart Channel Deduplication",
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Merge duplicate channels into multi-source streams",
                                                color = Color(0xFF9CA3AF),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = groupDuplicatesEnabled,
                                        onCheckedChange = { isChecked ->
                                            groupDuplicatesEnabled = isChecked
                                            io.github.rubayet123.tvlive.util.ChannelDeduplicator.setGroupDuplicatesEnabled(context, isChecked)
                                            Toast.makeText(
                                                context,
                                                if (isChecked) "Channel Deduplication Enabled" else "Channel Deduplication Disabled",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFF10B981)
                                        ),
                                        modifier = Modifier.tvFocusable(
                                            shape = RoundedCornerShape(20.dp),
                                            focusedBorderColor = Color.White,
                                            focusedBorderWidth = 2.dp,
                                            scaleOnFocus = 1.1f
                                        )
                                    )
                                }
                            }
                        }

                        itemsIndexed(sources) { index, source ->
                            ProviderCardItem(
                                source = source,
                                isFirst = index == 0,
                                isLast = index == sources.size - 1,
                                isSyncing = syncingSourceUrl == source.url,
                                onSyncNow = {
                                    if (syncingSourceUrl == null) {
                                        syncingSourceUrl = source.url
                                        scope.launch {
                                            Toast.makeText(context, "Syncing ${source.name}...", Toast.LENGTH_SHORT).show()
                                            val success = repository.syncSource(source)
                                            refreshData()
                                            syncingSourceUrl = null
                                            if (success) {
                                                Toast.makeText(context, "${source.name} synced successfully", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Failed to sync ${source.name}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                onToggleActive = {
                                    val updated = source.copy(isActive = !source.isActive)
                                    repository.updateSource(updated)
                                    refreshData()
                                },
                                onMoveUp = {
                                    repository.moveSourceUp(source)
                                    refreshData()
                                },
                                onMoveDown = {
                                    repository.moveSourceDown(source)
                                    refreshData()
                                },
                                onChangeInterval = {
                                    sourceForInterval = source
                                },
                                onEdit = {
                                    sourceToEdit = source
                                },
                                onDelete = {
                                    sourceToDelete = source
                                }
                            )
                        }
                    }
                }
            }
        }

        // Add Provider Sheet
        if (showAddDialog) {
            AddOrEditProviderModalSheet(
                existingSource = null,
                onDismiss = { showAddDialog = false },
                onSave = { _, newSource ->
                    repository.addSource(newSource)
                    refreshData()
                    showAddDialog = false
                    Toast.makeText(context, "Provider added successfully", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Edit Provider Sheet
        sourceToEdit?.let { target ->
            AddOrEditProviderModalSheet(
                existingSource = target,
                onDismiss = { sourceToEdit = null },
                onSave = { oldUrl, updatedSource ->
                    if (oldUrl != null) {
                        repository.updateSource(oldUrl, updatedSource)
                        Toast.makeText(context, "Provider updated", Toast.LENGTH_SHORT).show()
                    }
                    refreshData()
                    sourceToEdit = null
                }
            )
        }

        // Delete Confirmation Dialog
        sourceToDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { sourceToDelete = null },
                containerColor = Color(0xFF161A23),
                titleContentColor = Color.White,
                textContentColor = Color(0xFF9CA3AF),
                title = { Text("Delete Provider", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to remove '${target.name}'? This cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            repository.removeSource(target)
                            refreshData()
                            sourceToDelete = null
                            Toast.makeText(context, "Provider removed", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { sourceToDelete = null }) {
                        Text("Cancel", color = Color.White)
                    }
                }
            )
        }

        // Global Sync Schedule Dialog
        if (showGlobalSyncDialog) {
            val intervals = listOf(
                Pair("Manual Only (Default)", 0),
                Pair("On App Launch", -1),
                Pair("Every 1 Hour", 1),
                Pair("Every 2 Hours", 2),
                Pair("Every 6 Hours", 6),
                Pair("Every 12 Hours", 12),
                Pair("Every 24 Hours", 24),
                Pair("Every 48 Hours", 48),
                Pair("Every 7 Days (Weekly)", 168)
            )

            AlertDialog(
                onDismissRequest = { showGlobalSyncDialog = false },
                containerColor = Color(0xFF161A23),
                titleContentColor = Color.White,
                title = { Text("Global Playlist Auto-Sync", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Column {
                        Text(
                            text = "Apply this sync interval to all active playlist providers:",
                            color = Color(0xFF9CA3AF),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        intervals.forEach { (label, hours) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        repository.setAllSourcesInterval(hours)
                                        refreshData()
                                        showGlobalSyncDialog = false
                                        Toast.makeText(context, "All providers set to: $label", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGlobalSyncDialog = false }) {
                        Text("Close", color = Color.White)
                    }
                }
            )
        }

        // Refresh Interval Picker Dialog (Per-Source)
        sourceForInterval?.let { target ->
            val intervals = listOf(
                Pair("Manual Only (Default)", 0),
                Pair("On App Launch", -1),
                Pair("Every 1 Hour", 1),
                Pair("Every 2 Hours", 2),
                Pair("Every 6 Hours", 6),
                Pair("Every 12 Hours", 12),
                Pair("Every 24 Hours", 24),
                Pair("Every 48 Hours", 48),
                Pair("Every 7 Days (Weekly)", 168)
            )
            val currentHours = target.refreshIntervalHours

            AlertDialog(
                onDismissRequest = { sourceForInterval = null },
                containerColor = Color(0xFF161A23),
                titleContentColor = Color.White,
                title = { Text("Auto-Sync for '${target.name}'", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Column {
                        intervals.forEach { (label, hours) ->
                            val isSelected = currentHours == hours
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val updated = target.copy(refreshIntervalHours = hours)
                                        repository.updateSource(updated)
                                        if (hours != 0) {
                                            repository.triggerAppStartRefreshes()
                                        }
                                        refreshData()
                                        sourceForInterval = null
                                        Toast.makeText(context, "Refresh set to: $label", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = label,
                                    color = if (isSelected) Color(0xFF10B981) else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { sourceForInterval = null }) {
                        Text("Close", color = Color.White)
                    }
                }
            )
        }
    }
}

@Composable
private fun ProviderCardItem(
    source: Source,
    isFirst: Boolean,
    isLast: Boolean,
    isSyncing: Boolean = false,
    onSyncNow: () -> Unit,
    onToggleActive: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onChangeInterval: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable(
                shape = RoundedCornerShape(16.dp),
                focusedBorderColor = Color(0xFF10B981),
                unfocusedBorderColor = Color(0xFF2B3245),
                focusedBorderWidth = 3.dp,
                scaleOnFocus = 1.02f
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1F2C)),
        border = BorderStroke(1.dp, Color(0xFF2B3245))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Icon + Title + Status Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = if (source.url.startsWith("/")) Icons.Default.List else Icons.Default.List
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = source.name,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = if (source.isActive) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF374151),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(
                                    0.5.dp,
                                    if (source.isActive) Color(0xFF10B981) else Color(0xFF4B5563)
                                )
                            ) {
                                Text(
                                    text = if (source.isActive) "ACTIVE" else "INACTIVE",
                                    color = if (source.isActive) Color(0xFF10B981) else Color(0xFF9CA3AF),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = source.url,
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        // Last Sync Info & Interval Tag
                        if (source.url.startsWith("http")) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFF6B7280),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Synced: ${source.getFormattedLastRefreshed()} • Auto-sync: ${source.getIntervalLabel()}",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = source.isActive,
                    onCheckedChange = { onToggleActive() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF10B981),
                        uncheckedThumbColor = Color(0xFF9CA3AF),
                        uncheckedTrackColor = Color(0xFF1F2432)
                    ),
                    modifier = Modifier.tvFocusable(
                        shape = RoundedCornerShape(20.dp),
                        focusedBorderColor = Color.White,
                        focusedBorderWidth = 2.5.dp,
                        scaleOnFocus = 1.15f
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFF2B3245), thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Action Row: Interval Chip, Reorder Buttons, Sync Now, Edit, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Refresh interval selector pill for all HTTP sources
                if (source.url.startsWith("http")) {
                    Surface(
                        onClick = onChangeInterval,
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF1F2432),
                        border = BorderStroke(1.dp, Color(0xFF2B3245)),
                        modifier = Modifier.tvFocusable(
                            shape = RoundedCornerShape(20.dp),
                            focusedBorderColor = Color.White,
                            focusedBorderWidth = 2.dp,
                            scaleOnFocus = 1.08f
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = source.getIntervalLabel(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Sync Now Button
                    if (source.url.startsWith("http")) {
                        TvIconButton(
                            icon = Icons.Default.Refresh,
                            contentDescription = "Sync Now",
                            onClick = onSyncNow,
                            size = 38.dp,
                            defaultBgColor = Color(0xFF10B981).copy(alpha = 0.15f),
                            defaultTintColor = Color(0xFF10B981),
                            focusedBgColor = Color(0xFF10B981),
                            focusedIconColor = Color.Black,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Segmented reorder group (Up / Down) with high-visibility TV buttons
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF141822),
                        border = BorderStroke(1.dp, Color(0xFF2B3245))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(38.dp)
                        ) {
                            TvIconButton(
                                icon = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move Up",
                                onClick = onMoveUp,
                                enabled = !isFirst,
                                size = 38.dp,
                                defaultBgColor = Color.Transparent,
                                focusedBgColor = Color.White,
                                focusedIconColor = Color.Black,
                                defaultTintColor = if (!isFirst) Color.White else Color(0xFF4B5563),
                                shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(18.dp)
                                    .background(Color(0xFF2B3245))
                            )

                            TvIconButton(
                                icon = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Move Down",
                                onClick = onMoveDown,
                                enabled = !isLast,
                                size = 38.dp,
                                defaultBgColor = Color.Transparent,
                                focusedBgColor = Color.White,
                                focusedIconColor = Color.Black,
                                defaultTintColor = if (!isLast) Color.White else Color(0xFF4B5563),
                                shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
                            )
                        }
                    }

                    // Edit action button with high-contrast TV focus
                    TvIconButton(
                        icon = Icons.Default.Edit,
                        contentDescription = "Edit Provider",
                        onClick = onEdit,
                        size = 38.dp,
                        defaultBgColor = Color(0xFF3B82F6).copy(alpha = 0.15f),
                        defaultTintColor = Color(0xFF60A5FA),
                        focusedBgColor = Color(0xFF3B82F6),
                        focusedIconColor = Color.White,
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Delete action button with high-contrast TV focus
                    TvIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = "Delete Provider",
                        onClick = onDelete,
                        size = 38.dp,
                        defaultBgColor = Color(0xFFEF4444).copy(alpha = 0.15f),
                        defaultTintColor = Color(0xFFF87171),
                        focusedBgColor = Color(0xFFEF4444),
                        focusedIconColor = Color.White,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddOrEditProviderModalSheet(
    existingSource: Source? = null,
    onDismiss: () -> Unit,
    onSave: (oldUrl: String?, newSource: Source) -> Unit
) {
    val context = LocalContext.current

    // Initial Tab calculation
    val initialTab = remember(existingSource) {
        when {
            existingSource == null -> 0
            existingSource.type == "XTREAM" || existingSource.url.contains("get.php?username=") -> 2
            existingSource.url.startsWith("/") -> 1
            else -> 0
        }
    }

    var selectedTab by remember { mutableStateOf(initialTab) }

    var name by remember { mutableStateOf(existingSource?.name ?: "") }
    var url by remember { mutableStateOf(if (initialTab == 0) (existingSource?.url ?: "") else "") }
    var localPath by remember { mutableStateOf(if (initialTab == 1) (existingSource?.url ?: "") else "") }

    // Xtream parsing logic for initial state
    val parsedXtream = remember(existingSource) {
        if (existingSource != null && (existingSource.type == "XTREAM" || existingSource.url.contains("get.php?username="))) {
            try {
                val uri = Uri.parse(existingSource.url)
                val u = uri.getQueryParameter("username") ?: ""
                val p = uri.getQueryParameter("password") ?: ""
                val s = if (existingSource.url.contains("/get.php")) {
                    existingSource.url.substringBefore("/get.php")
                } else ""
                Triple(s, u, p)
            } catch (e: Exception) {
                Triple("", "", "")
            }
        } else {
            Triple("", "", "")
        }
    }

    var serverUrl by remember { mutableStateOf(parsedXtream.first) }
    var username by remember { mutableStateOf(parsedXtream.second) }
    var password by remember { mutableStateOf(parsedXtream.third) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val copiedPath = copyFileToInternalStorage(context, it)
            if (copiedPath != null) {
                localPath = copiedPath
                if (name.isBlank()) {
                    name = getFileName(context, it)?.removeSuffix(".m3u")?.removeSuffix(".m3u8") ?: "Local Playlist"
                }
                Toast.makeText(context, "Local file loaded", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161A23),
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.7f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = if (existingSource == null) "Add Provider" else "Edit Provider",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tabs for M3U URL vs Local File vs Xtream
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1F2432),
                contentColor = Color(0xFF10B981),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("M3U URL", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Local File", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Xtream Codes", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Common Provider Name Field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Provider Name", color = Color(0xFF9CA3AF)) },
                placeholder = { Text("e.g. Premium Sports TV", color = Color(0xFF4B5563)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1A1F2C),
                    unfocusedContainerColor = Color(0xFF1A1F2C),
                    focusedBorderColor = Color(0xFF10B981),
                    unfocusedBorderColor = Color(0xFF2B3245),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> {
                    // M3U URL Tab
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("M3U Playlist URL", color = Color(0xFF9CA3AF)) },
                        placeholder = { Text("https://example.com/playlist.m3u", color = Color(0xFF4B5563)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.List, contentDescription = null, tint = Color(0xFF10B981)) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1A1F2C),
                            unfocusedContainerColor = Color(0xFF1A1F2C),
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color(0xFF2B3245),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                1 -> {
                    // Local File Tab
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = localPath,
                            onValueChange = { localPath = it },
                            label = { Text("Local File Path", color = Color(0xFF9CA3AF)) },
                            placeholder = { Text("Select .m3u / .m3u8 file", color = Color(0xFF4B5563)) },
                            singleLine = true,
                            readOnly = true,
                            leadingIcon = { Icon(Icons.Default.List, contentDescription = null, tint = Color(0xFF10B981)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1A1F2C),
                                unfocusedContainerColor = Color(0xFF1A1F2C),
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = Color(0xFF2B3245),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { filePicker.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2432)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981)),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("Browse", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                2 -> {
                    // Xtream Codes Tab
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("Server URL", color = Color(0xFF9CA3AF)) },
                            placeholder = { Text("http://example.com:8080", color = Color(0xFF4B5563)) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF10B981)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1A1F2C),
                                unfocusedContainerColor = Color(0xFF1A1F2C),
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = Color(0xFF2B3245),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Username", color = Color(0xFF9CA3AF)) },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF10B981)) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF1A1F2C),
                                    unfocusedContainerColor = Color(0xFF1A1F2C),
                                    focusedBorderColor = Color(0xFF10B981),
                                    unfocusedBorderColor = Color(0xFF2B3245),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password", color = Color(0xFF9CA3AF)) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF10B981)) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF1A1F2C),
                                    unfocusedContainerColor = Color(0xFF1A1F2C),
                                    focusedBorderColor = Color(0xFF10B981),
                                    unfocusedBorderColor = Color(0xFF2B3245),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color(0xFF9CA3AF))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        val finalName = name.trim()
                        val finalUrl = when (selectedTab) {
                            0 -> url.trim()
                            1 -> localPath.trim()
                            2 -> {
                                var sUrl = serverUrl.trim().removeSuffix("/")
                                if (!sUrl.startsWith("http://") && !sUrl.startsWith("https://")) {
                                    sUrl = "http://$sUrl"
                                }
                                "$sUrl/get.php?username=${username.trim()}&password=${password.trim()}&type=m3u_plus&output=ts"
                            }
                            else -> ""
                        }

                        if (finalName.isNotBlank() && finalUrl.isNotBlank()) {
                            val newSource = Source(
                                name = finalName,
                                url = finalUrl,
                                isActive = existingSource?.isActive ?: true,
                                type = if (selectedTab == 2) "XTREAM" else "M3U",
                                refreshIntervalHours = existingSource?.refreshIntervalHours ?: 0,
                                isUserAdded = existingSource?.isUserAdded ?: true
                            )
                            onSave(existingSource?.url, newSource)
                        } else {
                            Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (existingSource == null) "Save Provider" else "Update Provider",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun copyFileToInternalStorage(context: Context, uri: Uri): String? {
    return try {
        val fileName = getFileName(context, uri) ?: "imported_playlist_${System.currentTimeMillis()}.m3u"
        val destinationFile = File(context.filesDir, fileName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destinationFile).use { output ->
                input.copyTo(output)
            }
        }
        destinationFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index != -1) {
                name = it.getString(index)
            }
        }
    }
    return name
}
