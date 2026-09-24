package io.github.rubayet123.tvlive.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.rubayet123.tvlive.data.FavoritesRepository
import io.github.rubayet123.tvlive.data.SourceRepository
import io.github.rubayet123.tvlive.util.BackupRestoreManager
import io.github.rubayet123.tvlive.util.CategoryOrderManager
import io.github.rubayet123.tvlive.util.CategoryOverrideManager
import io.github.rubayet123.tvlive.util.DeviceUtils
import io.github.rubayet123.tvlive.util.ThemeManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceUtils.setupOrientationForDevice(this)

        setContent {
            BackupSettingsScreen(onBack = { finish() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val currentTheme = remember { ThemeManager.getSelectedTheme(context) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Backup details state
    val sourceCount = remember(refreshTrigger) { SourceRepository(context).getSources().size }
    val favoriteCount = remember(refreshTrigger) { FavoritesRepository(context).getFavorites().size }
    val overrideCount = remember(refreshTrigger) { CategoryOverrideManager.getOverrideCountPerProvider(context).values.sum() }

    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }

    // Launcher for Export File
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    val success = BackupRestoreManager.exportToStream(context, os)
                    if (success) {
                        Toast.makeText(context, "Backup saved successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to save backup file", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error writing file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Launcher for Import File
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val success = BackupRestoreManager.importFromStream(context, inputStream)
                    if (success) {
                        refreshTrigger++
                        Toast.makeText(context, "Settings restored successfully!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Invalid backup file format", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading backup file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                                text = "Backup & Restore",
                                color = currentTheme.primaryText,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Export your playlists, favorites & categories to easily transfer to another device",
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
                Spacer(modifier = Modifier.height(12.dp))

                // Summary Header Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = currentTheme.cardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = currentTheme.accent,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Current Configuration",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = currentTheme.primaryText
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatBadge(label = "Playlist Sources", value = "$sourceCount", theme = currentTheme)
                            StatBadge(label = "Favorites", value = "$favoriteCount", theme = currentTheme)
                            StatBadge(label = "Custom Categories", value = "$overrideCount", theme = currentTheme)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "EXPORT BACKUP",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = currentTheme.accent,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Export File Card
                ActionCardItem(
                    title = "Export to File",
                    subtitle = "Save a .json backup file to your downloads or storage",
                    icon = Icons.Default.Send,
                    currentTheme = currentTheme,
                    onClick = {
                        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
                        val fileName = "tv_live_backup_${dateFormat.format(Date())}.json"
                        exportFileLauncher.launch(fileName)
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Share Backup Card
                ActionCardItem(
                    title = "Share Backup File",
                    subtitle = "Send your backup directly via Telegram, Email, Google Drive, etc.",
                    icon = Icons.Default.Share,
                    currentTheme = currentTheme,
                    onClick = {
                        try {
                            val jsonStr = BackupRestoreManager.createBackupJson(context)
                            val file = File(context.cacheDir, "tv_live_backup.json")
                            file.writeText(jsonStr)

                            val contentUri: Uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )

                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, contentUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share TV Live Backup"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Copy Code Card
                ActionCardItem(
                    title = "Copy Backup JSON Code",
                    subtitle = "Copy the raw JSON backup configuration to your clipboard",
                    icon = Icons.Default.List,
                    currentTheme = currentTheme,
                    onClick = {
                        val jsonStr = BackupRestoreManager.createBackupJson(context)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("TV Live Backup", jsonStr)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Backup copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "IMPORT BACKUP",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = currentTheme.accent,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Import File Card
                ActionCardItem(
                    title = "Import from File",
                    subtitle = "Select a .json backup file from device storage to restore",
                    icon = Icons.Default.Refresh,
                    currentTheme = currentTheme,
                    onClick = {
                        importFileLauncher.launch("*/*")
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Paste Code Card
                ActionCardItem(
                    title = "Paste Backup JSON Code",
                    subtitle = "Paste a copied JSON text configuration to restore settings",
                    icon = Icons.Default.Edit,
                    currentTheme = currentTheme,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipData = clipboard.primaryClip
                        if (clipData != null && clipData.itemCount > 0) {
                            pasteText = clipData.getItemAt(0).text?.toString() ?: ""
                        }
                        showPasteDialog = true
                    }
                )

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Paste Backup Code Dialog
    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            title = {
                Text(
                    text = "Paste Backup JSON",
                    fontWeight = FontWeight.Bold,
                    color = currentTheme.primaryText
                )
            },
            text = {
                Column {
                    Text(
                        text = "Paste the JSON string copied from another device:",
                        fontSize = 13.sp,
                        color = currentTheme.secondaryText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pasteText,
                        onValueChange = { pasteText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        placeholder = { Text("{\n  \"version\": 1,\n  ...\n}", color = currentTheme.secondaryText.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = currentTheme.accent,
                            unfocusedBorderColor = currentTheme.secondaryText.copy(alpha = 0.3f),
                            focusedTextColor = currentTheme.primaryText,
                            unfocusedTextColor = currentTheme.primaryText
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pasteText.isNotBlank()) {
                            val success = BackupRestoreManager.restoreBackupJson(context, pasteText)
                            if (success) {
                                refreshTrigger++
                                showPasteDialog = false
                                Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Invalid JSON backup data", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = currentTheme.accent)
                ) {
                    Text("Restore", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) {
                    Text("Cancel", color = currentTheme.secondaryText)
                }
            },
            containerColor = currentTheme.cardBg
        )
    }
}

@Composable
private fun StatBadge(label: String, value: String, theme: io.github.rubayet123.tvlive.util.AppTheme) {
    Surface(
        color = theme.primaryBg,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = theme.accent
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = theme.secondaryText
            )
        }
    }
}

@Composable
private fun ActionCardItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    currentTheme: io.github.rubayet123.tvlive.util.AppTheme,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isFocused) currentTheme.accent.copy(alpha = 0.25f) else currentTheme.cardBg,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) currentTheme.accent else currentTheme.cardBg,
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(currentTheme.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = currentTheme.accent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = currentTheme.primaryText
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = currentTheme.secondaryText
                )
            }
        }
    }
}
