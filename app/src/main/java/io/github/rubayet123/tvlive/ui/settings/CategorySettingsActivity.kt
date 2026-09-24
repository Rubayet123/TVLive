package io.github.rubayet123.tvlive.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rubayet123.tvlive.data.LiveTvManager
import io.github.rubayet123.tvlive.ui.tv.TvIconButton
import io.github.rubayet123.tvlive.ui.tv.tvFocusable
import io.github.rubayet123.tvlive.util.AppTheme
import io.github.rubayet123.tvlive.util.CategoryOrderManager
import io.github.rubayet123.tvlive.util.CategoryOverrideManager
import io.github.rubayet123.tvlive.util.DeviceUtils
import io.github.rubayet123.tvlive.util.ThemeManager
import java.util.Collections

class CategorySettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceUtils.setupOrientationForDevice(this)

        setContent {
            CategorySettingsScreen(onBack = { finish() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val currentTheme = remember { ThemeManager.getSelectedTheme(context) }

    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var providerOverrideCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    // Sync categories directly from active master channels matching the home screen
    fun syncActiveCategories() {
        val masterChannels = LiveTvManager.getMasterPlaylist()
        if (masterChannels.isEmpty()) {
            Toast.makeText(context, "No active channels loaded to scan", Toast.LENGTH_SHORT).show()
            return
        }
        val synced = CategoryOrderManager.syncFromActiveChannels(context, masterChannels)
        categories = synced
        
        val reordered = CategoryOrderManager.getCategoryOrderedChannels(context, masterChannels)
        LiveTvManager.setMasterPlaylist(reordered)
        
        Toast.makeText(context, "Synced ${synced.size} active categories matching Home Screen", Toast.LENGTH_SHORT).show()
    }

    // Load categories & override data
    fun loadCategoryData() {
        val masterChannels = LiveTvManager.getMasterPlaylist()
        if (masterChannels.isNotEmpty()) {
            val synced = CategoryOrderManager.syncFromActiveChannels(context, masterChannels)
            categories = synced
        } else {
            val discovered = CategoryOrderManager.getDiscoveredCategories(context)
            val sorted = CategoryOrderManager.sortCategories(discovered, context)
            categories = sorted
        }
        providerOverrideCounts = CategoryOverrideManager.getOverrideCountPerProvider(context)
        isLoading = false
    }

    LaunchedEffect(Unit) {
        loadCategoryData()
    }

    fun swapItems(fromIndex: Int, toIndex: Int) {
        if (fromIndex < 0 || fromIndex >= categories.size || toIndex < 0 || toIndex >= categories.size) return
        val list = categories.toMutableList()
        Collections.swap(list, fromIndex, toIndex)
        categories = list
        CategoryOrderManager.saveCategoryOrder(context, list)
        
        val currentMaster = LiveTvManager.getMasterPlaylist()
        if (currentMaster.isNotEmpty()) {
            val reordered = CategoryOrderManager.getCategoryOrderedChannels(context, currentMaster)
            LiveTvManager.setMasterPlaylist(reordered)
        }
    }

    fun resetOrder() {
        CategoryOrderManager.resetCategoryOrder(context)
        val discovered = CategoryOrderManager.getDiscoveredCategories(context)
        categories = discovered
        val currentMaster = LiveTvManager.getMasterPlaylist()
        if (currentMaster.isNotEmpty()) {
            val reordered = CategoryOrderManager.getCategoryOrderedChannels(context, currentMaster)
            LiveTvManager.setMasterPlaylist(reordered)
        }
        Toast.makeText(context, "Category order reset to default", Toast.LENGTH_SHORT).show()
    }

    fun resetProviderOverrides(providerName: String) {
        CategoryOverrideManager.resetOverridesForProvider(context, providerName)
        providerOverrideCounts = CategoryOverrideManager.getOverrideCountPerProvider(context)
        val currentMaster = LiveTvManager.getMasterPlaylist()
        if (currentMaster.isNotEmpty()) {
            val allWithOverrides = CategoryOverrideManager.applyOverrides(context, currentMaster)
            val reordered = CategoryOrderManager.getCategoryOrderedChannels(context, allWithOverrides)
            LiveTvManager.setMasterPlaylist(reordered)
            categories = CategoryOrderManager.syncFromActiveChannels(context, reordered)
        }
        Toast.makeText(context, "Reset overrides for $providerName to defaults", Toast.LENGTH_SHORT).show()
    }

    fun resetAllOverrides() {
        CategoryOverrideManager.resetAllOverrides(context)
        providerOverrideCounts = CategoryOverrideManager.getOverrideCountPerProvider(context)
        val currentMaster = LiveTvManager.getMasterPlaylist()
        if (currentMaster.isNotEmpty()) {
            val allWithOverrides = CategoryOverrideManager.applyOverrides(context, currentMaster)
            val reordered = CategoryOrderManager.getCategoryOrderedChannels(context, allWithOverrides)
            LiveTvManager.setMasterPlaylist(reordered)
            categories = CategoryOrderManager.syncFromActiveChannels(context, reordered)
        }
        Toast.makeText(context, "Reset all custom channel categories to defaults", Toast.LENGTH_SHORT).show()
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
                                text = "Manage Categories & Tabs",
                                color = currentTheme.primaryText,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Reorder category tabs or manage custom channel assignments",
                                color = currentTheme.secondaryText,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = currentTheme.accent
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                    ) {
                        // Prominent Action Buttons on the screen
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { syncActiveCategories() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = currentTheme.accent,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .tvFocusable(
                                            shape = RoundedCornerShape(12.dp),
                                            focusedBorderColor = Color.White,
                                            focusedBorderWidth = 2.5.dp,
                                            scaleOnFocus = 1.05f
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Scan Active Channels",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Scan Active", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { resetOrder() },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = currentTheme.accent
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, currentTheme.accent.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .tvFocusable(
                                            shape = RoundedCornerShape(12.dp),
                                            focusedBorderColor = Color.White,
                                            focusedBorderWidth = 2.5.dp,
                                            scaleOnFocus = 1.05f
                                        )
                                ) {
                                    Text("Reset Order", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // Help Tip Banner
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = currentTheme.cardBg.copy(alpha = 0.7f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "💡 Tip: Top categories appear first in the guide & home tabs. To move a channel to any category, long-press the channel card on Home or in the player.",
                                        fontSize = 12.sp,
                                        color = currentTheme.secondaryText,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }

                        // --- CATEGORY ORDER SECTION ---
                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CATEGORY TAB ORDER",
                                    color = currentTheme.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.1.sp
                                )
                                if (categories.isNotEmpty()) {
                                    Text(
                                        text = "${categories.size} Active Categories",
                                        color = currentTheme.secondaryText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        itemsIndexed(categories) { index, categoryName ->
                            CategoryOrderItem(
                                index = index,
                                totalCount = categories.size,
                                categoryName = categoryName,
                                onMoveUp = { swapItems(index, index - 1) },
                                onMoveDown = { swapItems(index, index + 1) },
                                currentTheme = currentTheme
                            )
                        }

                        // --- PROVIDER OVERRIDES SECTION ---
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CUSTOM CHANNEL ASSIGNMENTS",
                                    color = currentTheme.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 4.dp)
                                )

                                if (providerOverrideCounts.isNotEmpty()) {
                                    TextButton(onClick = { resetAllOverrides() }) {
                                        Text("Reset All Channels", color = Color(0xFFEF4444), fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        if (providerOverrideCounts.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = currentTheme.cardBg),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "No individual channel custom categories active. Long-press any channel card on the home screen to assign it directly.",
                                        color = currentTheme.secondaryText,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        } else {
                            providerOverrideCounts.forEach { (providerName, count) ->
                                item {
                                    ProviderOverrideResetItem(
                                        providerName = providerName,
                                        overrideCount = count,
                                        onReset = { resetProviderOverrides(providerName) },
                                        currentTheme = currentTheme
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
fun CategoryOrderItem(
    index: Int,
    totalCount: Int,
    categoryName: String,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    currentTheme: AppTheme
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable(
                shape = RoundedCornerShape(12.dp),
                focusedBorderColor = currentTheme.accent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                focusedBorderWidth = 3.dp,
                scaleOnFocus = 1.02f
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = currentTheme.cardBg
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(currentTheme.accent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        color = currentTheme.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = categoryName,
                    color = currentTheme.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TvIconButton(
                    icon = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Move Up",
                    onClick = onMoveUp,
                    enabled = index > 0,
                    size = 36.dp,
                    defaultBgColor = if (index > 0) currentTheme.accent.copy(alpha = 0.15f) else Color.Transparent,
                    defaultTintColor = if (index > 0) currentTheme.accent else currentTheme.secondaryText.copy(alpha = 0.3f),
                    focusedBgColor = Color.White,
                    focusedIconColor = Color.Black,
                    shape = CircleShape
                )

                TvIconButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Move Down",
                    onClick = onMoveDown,
                    enabled = index < totalCount - 1,
                    size = 36.dp,
                    defaultBgColor = if (index < totalCount - 1) currentTheme.accent.copy(alpha = 0.15f) else Color.Transparent,
                    defaultTintColor = if (index < totalCount - 1) currentTheme.accent else currentTheme.secondaryText.copy(alpha = 0.3f),
                    focusedBgColor = Color.White,
                    focusedIconColor = Color.Black,
                    shape = CircleShape
                )
            }
        }
    }
}

@Composable
fun ProviderOverrideResetItem(
    providerName: String,
    overrideCount: Int,
    onReset: () -> Unit,
    currentTheme: AppTheme
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable(
                shape = RoundedCornerShape(12.dp),
                focusedBorderColor = currentTheme.accent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                focusedBorderWidth = 3.dp,
                scaleOnFocus = 1.02f
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = currentTheme.cardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = providerName,
                    color = currentTheme.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$overrideCount channel${if (overrideCount > 1) "s" else ""} customized",
                    color = currentTheme.secondaryText,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(
                    containerColor = currentTheme.accent.copy(alpha = 0.2f),
                    contentColor = currentTheme.accent
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.tvFocusable(
                    shape = RoundedCornerShape(8.dp),
                    focusedBorderColor = Color.White,
                    focusedBorderWidth = 2.5.dp,
                    scaleOnFocus = 1.08f
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset Source Defaults",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reset Source Defaults", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
