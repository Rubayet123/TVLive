package io.github.rubayet123.tvlive.ui.dialogs

import android.app.AlertDialog
import android.content.Context
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import io.github.rubayet123.tvlive.R
import io.github.rubayet123.tvlive.data.FavoritesRepository
import io.github.rubayet123.tvlive.model.Channel
import io.github.rubayet123.tvlive.util.AppTheme
import io.github.rubayet123.tvlive.util.CategoryOverrideManager
import io.github.rubayet123.tvlive.util.HiddenChannelsManager
import io.github.rubayet123.tvlive.util.ThemeManager

/**
 * Modern Channel Quick Action Sheet for Mobile and Android TV.
 * Displays:
 *  - Channel logo, display name, current category badge, and active stream count badge.
 *  - "Change Category" action
 *  - "Favorite / Unfavorite" action
 *  - "Hide Channel" action
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelQuickActionSheet(
    channel: Channel,
    onDismiss: () -> Unit,
    onOpenChangeCategory: () -> Unit,
    onChannelUpdated: () -> Unit
) {
    val context = LocalContext.current
    val currentTheme = remember { ThemeManager.getSelectedTheme(context) }
    val favoritesRepo = remember { FavoritesRepository(context) }
    var isFavorite by remember { mutableStateOf(favoritesRepo.isFavorite(channel)) }

    val streamCount = remember(channel) {
        val count = channel.effectiveSources.size
        if (count > 0) count else 1
    }

    val categoryName = remember(channel) {
        val override = CategoryOverrideManager.getOverrideCategory(context, channel)
        override ?: channel.group?.ifBlank { "Uncategorized" } ?: "Uncategorized"
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
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            // Header Section: Logo, Title, Badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Channel Logo
                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = currentTheme.cardBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, currentTheme.accent.copy(alpha = 0.25f))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            ImageView(ctx).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            }
                        },
                        update = { imageView ->
                            Glide.with(imageView.context)
                                .load(channel.logoUrl)
                                .placeholder(R.drawable.fallback_logo)
                                .error(R.drawable.fallback_logo)
                                .into(imageView)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = channel.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentTheme.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Badges Row: Category & Stream Count
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Category Badge
                        Surface(
                            color = currentTheme.accent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, currentTheme.accent.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = categoryName,
                                color = currentTheme.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Stream Count Badge
                        Surface(
                            color = if (streamCount > 1) Color(0xFF10B981).copy(alpha = 0.15f) else currentTheme.secondaryText.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                0.8.dp,
                                if (streamCount > 1) Color(0xFF10B981).copy(alpha = 0.5f) else currentTheme.secondaryText.copy(alpha = 0.25f)
                            )
                        ) {
                            Text(
                                text = if (streamCount > 1) "$streamCount Streams Available" else "1 Stream Available",
                                color = if (streamCount > 1) Color(0xFF10B981) else currentTheme.secondaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Close Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = currentTheme.secondaryText.copy(alpha = 0.8f)
                    )
                }
            }

            HorizontalDivider(
                color = currentTheme.cardBg.copy(alpha = 0.8f),
                thickness = 1.dp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Quick Actions List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Change Category Action
                QuickActionItem(
                    title = "Change Category",
                    subtitle = "Current: $categoryName",
                    icon = Icons.Default.List,
                    iconTint = currentTheme.accent,
                    onClick = {
                        onDismiss()
                        onOpenChangeCategory()
                    },
                    currentTheme = currentTheme
                )

                // 3. Favorite / Unfavorite Action
                QuickActionItem(
                    title = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                    subtitle = if (isFavorite) "Currently in your favorites" else "Save for one-tap access",
                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    iconTint = if (isFavorite) Color(0xFFEF4444) else currentTheme.secondaryText,
                    onClick = {
                        if (isFavorite) {
                            favoritesRepo.removeFavorite(channel)
                            isFavorite = false
                            Toast.makeText(context, "Removed '${channel.name}' from Favorites", Toast.LENGTH_SHORT).show()
                        } else {
                            favoritesRepo.addFavorite(channel)
                            isFavorite = true
                            Toast.makeText(context, "Added '${channel.name}' to Favorites", Toast.LENGTH_SHORT).show()
                        }
                        onChannelUpdated()
                    },
                    currentTheme = currentTheme
                )

                // 3. Hide Channel Action
                QuickActionItem(
                    title = "Hide Channel",
                    subtitle = "Hide this channel from your guide and lists",
                    icon = Icons.Default.Clear,
                    iconTint = Color(0xFFF59E0B),
                    onClick = {
                        HiddenChannelsManager.hideChannel(context, channel)
                        Toast.makeText(context, "Hidden '${channel.name}'", Toast.LENGTH_SHORT).show()
                        onDismiss()
                        onChannelUpdated()
                    },
                    currentTheme = currentTheme
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun QuickActionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    currentTheme: AppTheme
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = currentTheme.cardBg.copy(alpha = 0.7f),
        border = androidx.compose.foundation.BorderStroke(1.dp, currentTheme.cardBg.copy(alpha = 0.9f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = iconTint.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = currentTheme.primaryText
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = currentTheme.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = currentTheme.secondaryText.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * TV Modal Action Dialog for remote control (D-Pad / Menu key) interactions.
 */
object ChannelQuickActionTvDialog {
    fun show(
        context: Context,
        channel: Channel,
        onOpenChangeCategory: () -> Unit,
        onStartMoveMode: (() -> Unit)? = null,
        onChannelUpdated: () -> Unit
    ) {
        val favoritesRepo = FavoritesRepository(context)
        val isFavorite = favoritesRepo.isFavorite(channel)
        val streamCount = if (channel.effectiveSources.size > 0) channel.effectiveSources.size else 1
        val categoryName = CategoryOverrideManager.getOverrideCategory(context, channel)
            ?: channel.group?.ifBlank { "Uncategorized" } ?: "Uncategorized"

        val streamInfo = if (streamCount > 1) " ($streamCount Streams Available)" else " (1 Stream)"
        val favLabel = if (isFavorite) "Remove from Favorites" else "Add to Favorites"

        val options = if (onStartMoveMode != null) {
            arrayOf(
                "Move Channel Position (Reorder)",
                "Change Category (Current: $categoryName)",
                favLabel,
                "Hide Channel"
            )
        } else {
            arrayOf(
                "Change Category (Current: $categoryName)",
                favLabel,
                "Hide Channel"
            )
        }

        val builder = AlertDialog.Builder(context)
        builder.setTitle("${channel.name}$streamInfo")

        builder.setItems(options) { dialog, which ->
            val selectedOption = options[which]
            when {
                selectedOption.startsWith("Move Channel Position") -> {
                    dialog.dismiss()
                    onStartMoveMode?.invoke()
                }
                selectedOption.startsWith("Change Category") -> {
                    dialog.dismiss()
                    onOpenChangeCategory()
                }
                selectedOption == favLabel -> {
                    if (isFavorite) {
                        favoritesRepo.removeFavorite(channel)
                        Toast.makeText(context, "Removed '${channel.name}' from Favorites", Toast.LENGTH_SHORT).show()
                    } else {
                        favoritesRepo.addFavorite(channel)
                        Toast.makeText(context, "Added '${channel.name}' to Favorites", Toast.LENGTH_SHORT).show()
                    }
                    onChannelUpdated()
                    dialog.dismiss()
                }
                selectedOption == "Hide Channel" -> {
                    HiddenChannelsManager.hideChannel(context, channel)
                    Toast.makeText(context, "Hidden '${channel.name}'", Toast.LENGTH_SHORT).show()
                    onChannelUpdated()
                    dialog.dismiss()
                }
            }
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        builder.create().show()
    }
}
