package io.github.rubayet123.tvlive.ui.dialogs

import android.app.AlertDialog
import android.content.Context
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import io.github.rubayet123.tvlive.R
import io.github.rubayet123.tvlive.model.Channel
import io.github.rubayet123.tvlive.util.CategoryOverrideManager
import io.github.rubayet123.tvlive.util.HiddenChannelsManager
import io.github.rubayet123.tvlive.util.ThemeManager

/**
 * Modern Hidden Channels Management Sheet for Mobile and TV.
 * Allows viewing all hidden channels, restoring them individually, or restoring all at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenChannelsSheet(
    allChannels: List<Channel>,
    onDismiss: () -> Unit,
    onChannelsUpdated: () -> Unit
) {
    val context = LocalContext.current
    val currentTheme = remember { ThemeManager.getSelectedTheme(context) }
    var searchQuery by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }

    val hiddenChannels = remember(allChannels, refreshKey) {
        HiddenChannelsManager.getHiddenChannels(context, allChannels)
    }

    val filteredHiddenChannels = remember(hiddenChannels, searchQuery) {
        if (searchQuery.isBlank()) {
            hiddenChannels
        } else {
            val q = searchQuery.trim().lowercase()
            hiddenChannels.filter { it.name.lowercase().contains(q) || (it.group?.lowercase()?.contains(q) == true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = currentTheme.topBar,
        contentColor = currentTheme.primaryText,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp),
                shape = RoundedCornerShape(2.dp),
                color = currentTheme.secondaryText.copy(alpha = 0.4f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Hidden Channels",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentTheme.primaryText
                    )
                    Text(
                        text = "${hiddenChannels.size} channels hidden",
                        fontSize = 12.sp,
                        color = currentTheme.secondaryText
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hiddenChannels.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                HiddenChannelsManager.unhideAll(context)
                                Toast.makeText(context, "Restored all channels", Toast.LENGTH_SHORT).show()
                                refreshKey++
                                onChannelsUpdated()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = currentTheme.accent
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Restore All",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = currentTheme.accent
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = currentTheme.secondaryText
                        )
                    }
                }
            }

            // Search Box (if more than 3 hidden channels)
            if (hiddenChannels.size > 3) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text("Search hidden channels...", color = currentTheme.secondaryText.copy(alpha = 0.6f), fontSize = 14.sp)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = currentTheme.secondaryText, modifier = Modifier.size(18.dp))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = currentTheme.primaryText,
                        unfocusedTextColor = currentTheme.primaryText,
                        focusedContainerColor = currentTheme.cardBg.copy(alpha = 0.6f),
                        unfocusedContainerColor = currentTheme.cardBg.copy(alpha = 0.4f),
                        focusedBorderColor = currentTheme.accent,
                        unfocusedBorderColor = currentTheme.cardBg
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }

            HorizontalDivider(
                color = currentTheme.cardBg,
                thickness = 1.dp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // Content List
            if (filteredHiddenChannels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = currentTheme.secondaryText.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (hiddenChannels.isEmpty()) "No hidden channels" else "No channels match search",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = currentTheme.secondaryText
                        )
                        if (hiddenChannels.isEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Long-press any channel card to hide it from your guide.",
                                fontSize = 12.sp,
                                color = currentTheme.secondaryText.copy(alpha = 0.7f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredHiddenChannels, key = { it.streamUrl + it.name }) { channel ->
                        val categoryName = CategoryOverrideManager.getOverrideCategory(context, channel)
                            ?: channel.group?.ifBlank { "Uncategorized" } ?: "Uncategorized"

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = currentTheme.cardBg.copy(alpha = 0.7f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, currentTheme.cardBg.copy(alpha = 0.9f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    color = currentTheme.cardBg
                                ) {
                                    AndroidView(
                                        factory = { ctx ->
                                            ImageView(ctx).apply {
                                                scaleType = ImageView.ScaleType.FIT_CENTER
                                            }
                                        },
                                        update = { iv ->
                                            Glide.with(iv.context)
                                                .load(channel.logoUrl)
                                                .placeholder(R.drawable.fallback_logo)
                                                .error(R.drawable.fallback_logo)
                                                .into(iv)
                                        },
                                        modifier = Modifier.fillMaxSize().padding(4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = channel.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = currentTheme.primaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = categoryName,
                                        fontSize = 11.sp,
                                        color = currentTheme.secondaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Button(
                                    onClick = {
                                        HiddenChannelsManager.unhideChannel(context, channel)
                                        Toast.makeText(context, "Restored '${channel.name}'", Toast.LENGTH_SHORT).show()
                                        refreshKey++
                                        onChannelsUpdated()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = currentTheme.accent.copy(alpha = 0.18f),
                                        contentColor = currentTheme.accent
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Restore",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
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

/**
 * TV Dialog for viewing and restoring hidden channels using remote control.
 */
object HiddenChannelsTvDialog {
    fun show(
        context: Context,
        allChannels: List<Channel>,
        onChannelsUpdated: () -> Unit
    ) {
        val hiddenChannels = HiddenChannelsManager.getHiddenChannels(context, allChannels)
        if (hiddenChannels.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle("Hidden Channels")
                .setMessage("There are no hidden channels. Long-press any channel card to hide it.")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .show()
            return
        }

        val items = mutableListOf("⚡ Restore All (${hiddenChannels.size} channels)")
        items.addAll(hiddenChannels.map { "Restore: ${it.name}" })

        AlertDialog.Builder(context)
            .setTitle("Hidden Channels (${hiddenChannels.size})")
            .setItems(items.toTypedArray()) { dialog, which ->
                if (which == 0) {
                    HiddenChannelsManager.unhideAll(context)
                    Toast.makeText(context, "Restored all channels", Toast.LENGTH_SHORT).show()
                    onChannelsUpdated()
                    dialog.dismiss()
                } else {
                    val channel = hiddenChannels[which - 1]
                    HiddenChannelsManager.unhideChannel(context, channel)
                    Toast.makeText(context, "Restored '${channel.name}'", Toast.LENGTH_SHORT).show()
                    onChannelsUpdated()
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
