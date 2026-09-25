package io.github.rubayet123.tvlive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed as lazyListItemsIndexed
import io.github.rubayet123.tvlive.ui.reorder.*
import io.github.rubayet123.tvlive.util.ChannelOrderManager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import io.github.rubayet123.tvlive.data.FavoritesRepository
import io.github.rubayet123.tvlive.data.LiveTvManager
import io.github.rubayet123.tvlive.data.M3uParser
import io.github.rubayet123.tvlive.data.SourceRepository
import io.github.rubayet123.tvlive.model.Channel
import io.github.rubayet123.tvlive.ui.settings.CategorySettingsActivity
import io.github.rubayet123.tvlive.ui.settings.PluginActivity
import io.github.rubayet123.tvlive.ui.settings.ProviderSettingsActivity
import io.github.rubayet123.tvlive.ui.settings.SourceActivity
import io.github.rubayet123.tvlive.ui.settings.ThemeSettingsActivity
import io.github.rubayet123.tvlive.util.ThemeManager
import io.github.rubayet123.tvlive.util.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.rubayet123.tvlive.data.network.NetworkClient
import okhttp3.OkHttpClient
import java.util.Locale

class MobileHomeFragment : Fragment() {

    private val themeUpdateTrigger = mutableStateOf(0)

    override fun onResume() {
        super.onResume()
        themeUpdateTrigger.value++
    }

    fun reloadChannels() {
        themeUpdateTrigger.value++
    }

    private val repository by lazy { SourceRepository(requireContext()) }
    private val favoritesRepository by lazy { FavoritesRepository(requireContext()) }
    private val client = NetworkClient.client
    private val parser = M3uParser(client)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MobileHomeScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MobileHomeScreen() {
        val context = LocalContext.current
        val themeTrigger = themeUpdateTrigger.value
        val currentTheme = remember(themeTrigger) { ThemeManager.getSelectedTheme(context) }
        
        val initialMasterList = remember { LiveTvManager.initializeFromDiskIfNeeded(context) }
        val initialCategories = remember(initialMasterList) {
            if (initialMasterList.isNotEmpty()) {
                val extractedCategories = initialMasterList
                    .mapNotNull { it.group?.trim() }
                    .filter { it.isNotBlank() }
                    .map { raw ->
                        raw.lowercase().replaceFirstChar { char ->
                            if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
                        }
                    }
                    .distinct()
                val sortedExtracted = io.github.rubayet123.tvlive.util.CategoryOrderManager.sortCategories(extractedCategories, context)
                listOf("All", "Favorites") + sortedExtracted
            } else {
                emptyList()
            }
        }

        var isLoading by remember { mutableStateOf(initialMasterList.isEmpty()) }
        var allChannels by remember { mutableStateOf<List<Channel>>(initialMasterList) }
        var favoriteChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
        var recentChannels by remember { mutableStateOf<List<Channel>>(LiveTvManager.getRecentChannels()) }
        var categories by remember { mutableStateOf<List<String>>(initialCategories) }
        
        var selectedCategory by remember { mutableStateOf("All") }
        var searchQuery by remember { mutableStateOf("") }
        var isSearchActive by remember { mutableStateOf(false) }
        var showSettingsSheet by remember { mutableStateOf(false) }
        var showHiddenChannelsSheet by remember { mutableStateOf(false) }
        var selectedChannelForQuickAction by remember { mutableStateOf<Channel?>(null) }
        var selectedChannelForCategoryChange by remember { mutableStateOf<Channel?>(null) }
        var refreshTrigger by remember { mutableStateOf(0) }

        val homePrefs = remember { context.getSharedPreferences("tv_live_prefs", Context.MODE_PRIVATE) }
        var selectedLayout by remember {
            mutableStateOf(homePrefs.getString("pref_home_layout", "2_COLUMNS") ?: "2_COLUMNS")
        }
        var showLayoutMenu by remember { mutableStateOf(false) }

        // Function to load all sources and channels
        fun loadData(forceSpinner: Boolean = true) {
            lifecycleScope.launch {
                if (forceSpinner && allChannels.isEmpty()) {
                    isLoading = true
                }
                checkAndTriggerAutoScrape()
                
                val favs = withContext(Dispatchers.IO) { favoritesRepository.getFavorites() }
                favoriteChannels = favs
                recentChannels = LiveTvManager.getRecentChannels()

                val sources = withContext(Dispatchers.IO) { repository.getSources() }
                val fetchedChannels = mutableListOf<Channel>()

                withContext(Dispatchers.IO) {
                    sources.filter { it.isActive }.forEachIndexed { index, source ->
                        try {
                            val parsedCategories = parser.parse(source.url, defaultProviderName = source.name, priority = index)
                            parsedCategories.forEach { category ->
                                fetchedChannels.addAll(category.channels)
                            }
                        } catch (e: Exception) {
                            Log.e("MobileHomeFragment", "Error loading source: ${source.name}", e)
                        }
                    }
                }

                val overriddenChannels = if (fetchedChannels.isNotEmpty()) {
                    withContext(Dispatchers.Default) {
                        val overridden = io.github.rubayet123.tvlive.util.CategoryOverrideManager.applyOverrides(context, fetchedChannels)
                        val deduped = io.github.rubayet123.tvlive.util.ChannelDeduplicator.deduplicateChannels(context, overridden)
                        io.github.rubayet123.tvlive.util.CategoryOrderManager.getCategoryOrderedChannels(context, deduped)
                    }
                } else {
                    emptyList()
                }
                
                allChannels = overriddenChannels
                LiveTvManager.setMasterPlaylist(overriddenChannels, context)

                // Extract unique categories and apply saved custom order
                val extractedCategories = overriddenChannels
                    .mapNotNull { it.group?.trim() }
                    .filter { it.isNotBlank() }
                    .map { raw ->
                        raw.lowercase().replaceFirstChar { char ->
                            if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
                        }
                    }
                    .distinct()

                io.github.rubayet123.tvlive.util.CategoryOrderManager.saveDiscoveredCategories(context, extractedCategories)
                val sortedExtracted = io.github.rubayet123.tvlive.util.CategoryOrderManager.sortCategories(extractedCategories, context)

                categories = listOf("All", "Favorites") + sortedExtracted
                isLoading = false
            }
        }

        LaunchedEffect(themeUpdateTrigger.value) {
            val favs = withContext(Dispatchers.IO) { favoritesRepository.getFavorites() }
            favoriteChannels = favs
            recentChannels = LiveTvManager.getRecentChannels()

            loadData(forceSpinner = false)
        }

        // Filter channels based on selected category & search query (excluding hidden channels)
        val filteredChannels = remember(allChannels, favoriteChannels, selectedCategory, searchQuery, refreshTrigger) {
            val visibleMaster = io.github.rubayet123.tvlive.util.HiddenChannelsManager.filterVisibleChannels(context, allChannels)
            val visibleFavorites = io.github.rubayet123.tvlive.util.HiddenChannelsManager.filterVisibleChannels(context, favoriteChannels)

            var list = when (selectedCategory) {
                "All" -> visibleMaster
                "Favorites" -> visibleFavorites
                else -> visibleMaster.filter { channel ->
                    val normGroup = channel.group?.trim()?.lowercase() ?: ""
                    normGroup == selectedCategory.lowercase()
                }
            }

            // Apply custom channel order per category when not searching
            if (searchQuery.isBlank()) {
                list = ChannelOrderManager.applyCategoryChannelOrder(context, selectedCategory, list)
            }

            if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim().lowercase()
                list = list.filter { channel ->
                    channel.name.lowercase().contains(q) ||
                            (channel.group?.lowercase()?.contains(q) == true)
                }
            }
            list
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
                        // Main Header Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.logo),
                                    contentDescription = "App Logo",
                                    modifier = Modifier.height(32.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Surface(
                                    color = currentTheme.accent.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, currentTheme.accent.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(currentTheme.accent)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${allChannels.size} Channels",
                                            color = currentTheme.accent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { isSearchActive = !isSearchActive },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(currentTheme.cardBg)
                                ) {
                                    Icon(
                                        imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = currentTheme.primaryText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Box {
                                    IconButton(
                                        onClick = { showLayoutMenu = true },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(currentTheme.cardBg)
                                    ) {
                                        Icon(
                                            imageVector = when (selectedLayout) {
                                                "3_COLUMNS" -> Icons.Default.Menu
                                                "LIST" -> Icons.Default.List
                                                else -> Icons.Default.List
                                            },
                                            contentDescription = "Layout Style",
                                            tint = currentTheme.primaryText,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showLayoutMenu,
                                        onDismissRequest = { showLayoutMenu = false },
                                        modifier = Modifier.background(currentTheme.cardBg)
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = if (selectedLayout == "3_COLUMNS") Icons.Default.Check else Icons.Default.Menu,
                                                        contentDescription = null,
                                                        tint = if (selectedLayout == "3_COLUMNS") currentTheme.accent else currentTheme.primaryText,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(
                                                        "3 Columns",
                                                        color = if (selectedLayout == "3_COLUMNS") currentTheme.accent else currentTheme.primaryText,
                                                        fontWeight = if (selectedLayout == "3_COLUMNS") FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            },
                                            onClick = {
                                                selectedLayout = "3_COLUMNS"
                                                homePrefs.edit().putString("pref_home_layout", "3_COLUMNS").apply()
                                                showLayoutMenu = false
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = if (selectedLayout == "2_COLUMNS") Icons.Default.Check else Icons.Default.List,
                                                        contentDescription = null,
                                                        tint = if (selectedLayout == "2_COLUMNS") currentTheme.accent else currentTheme.primaryText,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(
                                                        "2 Columns",
                                                        color = if (selectedLayout == "2_COLUMNS") currentTheme.accent else currentTheme.primaryText,
                                                        fontWeight = if (selectedLayout == "2_COLUMNS") FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            },
                                            onClick = {
                                                selectedLayout = "2_COLUMNS"
                                                homePrefs.edit().putString("pref_home_layout", "2_COLUMNS").apply()
                                                showLayoutMenu = false
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = if (selectedLayout == "LIST") Icons.Default.Check else Icons.Default.List,
                                                        contentDescription = null,
                                                        tint = if (selectedLayout == "LIST") currentTheme.accent else currentTheme.primaryText,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(
                                                        "List View",
                                                        color = if (selectedLayout == "LIST") currentTheme.accent else currentTheme.primaryText,
                                                        fontWeight = if (selectedLayout == "LIST") FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            },
                                            onClick = {
                                                selectedLayout = "LIST"
                                                homePrefs.edit().putString("pref_home_layout", "LIST").apply()
                                                showLayoutMenu = false
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = { showSettingsSheet = true },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(currentTheme.cardBg)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = currentTheme.primaryText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Search Input Bar
                        AnimatedVisibility(
                            visible = isSearchActive,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text("Search channels or categories...", color = currentTheme.secondaryText.copy(alpha = 0.7f), fontSize = 14.sp)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = currentTheme.accent)
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = currentTheme.secondaryText)
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = currentTheme.cardBg,
                                    unfocusedContainerColor = currentTheme.cardBg,
                                    focusedBorderColor = currentTheme.accent,
                                    unfocusedBorderColor = currentTheme.cardBg.copy(alpha = 0.5f),
                                    focusedTextColor = currentTheme.primaryText,
                                    unfocusedTextColor = currentTheme.primaryText
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Horizontal Category Chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            items(categories) { category ->
                                val isSelected = category == selectedCategory
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategory = category },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (category == "Favorites") {
                                                Icon(
                                                    imageVector = Icons.Default.Favorite,
                                                    contentDescription = null,
                                                    tint = if (isSelected) Color.White else Color(0xFFEF4444),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = category,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.sp
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = currentTheme.cardBg,
                                        labelColor = currentTheme.secondaryText,
                                        selectedContainerColor = currentTheme.accent,
                                        selectedLabelColor = Color.White
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = currentTheme.cardBg.copy(alpha = 0.5f),
                                        selectedBorderColor = currentTheme.accent,
                                        enabled = true,
                                        selected = isSelected
                                    )
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
                ) {
                    if (isLoading) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = currentTheme.accent)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading M3U Channels & Plugins...",
                                color = currentTheme.secondaryText,
                                fontSize = 14.sp
                            )
                        }
                    } else if (filteredChannels.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "No channels",
                                tint = currentTheme.secondaryText.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Channels Found",
                                color = currentTheme.primaryText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (selectedCategory == "Favorites")
                                    "You haven't added any channels to your favorites yet. Tap the heart icon on any channel card."
                                else
                                    "Try selecting a different category or update your M3U playlist sources in Settings.",
                                color = currentTheme.secondaryText,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { loadData() },
                                colors = ButtonDefaults.buttonColors(containerColor = currentTheme.accent),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reload Channels")
                            }
                        }
                    } else {
                        val displayChannels = remember(filteredChannels) {
                            mutableStateListOf<Channel>().apply { addAll(filteredChannels) }
                        }

                        val isPlayzCategory = selectedCategory.equals("Playz Live", ignoreCase = true)

                        when (selectedLayout) {
                            "3_COLUMNS" -> {
                                val gridState = rememberLazyGridState()
                                val reorderState = rememberReorderableLazyGridState(
                                    gridState = gridState,
                                    onMove = { fromIndex, toIndex ->
                                        if (fromIndex in displayChannels.indices && toIndex in displayChannels.indices) {
                                            val item = displayChannels.removeAt(fromIndex)
                                            displayChannels.add(toIndex, item)
                                        }
                                    },
                                    onDragEnd = {
                                        if (searchQuery.isBlank()) {
                                            ChannelOrderManager.saveChannelOrderForCategory(
                                                context,
                                                selectedCategory,
                                                displayChannels.toList()
                                            )
                                            LiveTvManager.updatePlaylist(displayChannels.toList(), 0)
                                        }
                                    }
                                )

                                LazyVerticalGrid(
                                    state = gridState,
                                    columns = if (isPlayzCategory) GridCells.Adaptive(minSize = 320.dp) else GridCells.Fixed(3),
                                    contentPadding = PaddingValues(if (isPlayzCategory) 12.dp else 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(if (isPlayzCategory) 12.dp else 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(if (isPlayzCategory) 12.dp else 8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(
                                        items = displayChannels,
                                        key = { _, channel -> "${channel.id}_${channel.streamUrl}_${channel.name}" }
                                    ) { index, channel ->
                                        val itemKey = "${channel.id}_${channel.streamUrl}_${channel.name}"
                                        val isFav = favoriteChannels.any { it.streamUrl == channel.streamUrl }
                                        if (isPlayzCategory) {
                                            PlayzMatchCard(
                                                channel = channel,
                                                isFavorite = isFav,
                                                onFavoriteToggle = {
                                                    if (isFav) {
                                                        favoritesRepository.removeFavorite(channel)
                                                    } else {
                                                        favoritesRepository.addFavorite(channel)
                                                    }
                                                    favoriteChannels = favoritesRepository.getFavorites()
                                                },
                                                onClick = {
                                                    LiveTvManager.updatePlaylist(displayChannels.toList(), index)
                                                    LiveTvManager.setCurrentChannelInMaster(channel)
                                                    LiveTvManager.addToRecent(channel)
                                                    val intent = Intent(context, PlaybackActivity::class.java)
                                                    startActivity(intent)
                                                },
                                                onMoreOptionsClick = {
                                                    selectedChannelForQuickAction = channel
                                                },
                                                currentTheme = currentTheme,
                                                modifier = Modifier.reorderableGridItem(reorderState, itemKey, index)
                                            )
                                        } else {
                                            ChannelCardCompact(
                                                channel = channel,
                                                isFavorite = isFav,
                                                onFavoriteToggle = {
                                                    if (isFav) {
                                                        favoritesRepository.removeFavorite(channel)
                                                    } else {
                                                        favoritesRepository.addFavorite(channel)
                                                    }
                                                    favoriteChannels = favoritesRepository.getFavorites()
                                                },
                                                onClick = {
                                                    LiveTvManager.updatePlaylist(displayChannels.toList(), index)
                                                    LiveTvManager.setCurrentChannelInMaster(channel)
                                                    LiveTvManager.addToRecent(channel)
                                                    val intent = Intent(context, PlaybackActivity::class.java)
                                                    startActivity(intent)
                                                },
                                                onMoreOptionsClick = {
                                                    selectedChannelForQuickAction = channel
                                                },
                                                currentTheme = currentTheme,
                                                modifier = Modifier.reorderableGridItem(reorderState, itemKey, index)
                                            )
                                        }
                                    }
                                }
                            }
                            "LIST" -> {
                                val listState = rememberLazyListState()
                                val reorderState = rememberReorderableLazyListState(
                                    listState = listState,
                                    onMove = { fromIndex, toIndex ->
                                        if (fromIndex in displayChannels.indices && toIndex in displayChannels.indices) {
                                            val item = displayChannels.removeAt(fromIndex)
                                            displayChannels.add(toIndex, item)
                                        }
                                    },
                                    onDragEnd = {
                                        if (searchQuery.isBlank()) {
                                            ChannelOrderManager.saveChannelOrderForCategory(
                                                context,
                                                selectedCategory,
                                                displayChannels.toList()
                                            )
                                            LiveTvManager.updatePlaylist(displayChannels.toList(), 0)
                                        }
                                    }
                                )

                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(if (isPlayzCategory) 12.dp else 8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    lazyListItemsIndexed(
                                        items = displayChannels,
                                        key = { _, channel -> "${channel.id}_${channel.streamUrl}_${channel.name}" }
                                    ) { index, channel ->
                                        val itemKey = "${channel.id}_${channel.streamUrl}_${channel.name}"
                                        val isFav = favoriteChannels.any { it.streamUrl == channel.streamUrl }
                                        if (isPlayzCategory) {
                                            PlayzMatchCard(
                                                channel = channel,
                                                isFavorite = isFav,
                                                onFavoriteToggle = {
                                                    if (isFav) {
                                                        favoritesRepository.removeFavorite(channel)
                                                    } else {
                                                        favoritesRepository.addFavorite(channel)
                                                    }
                                                    favoriteChannels = favoritesRepository.getFavorites()
                                                },
                                                onClick = {
                                                    LiveTvManager.updatePlaylist(displayChannels.toList(), index)
                                                    LiveTvManager.setCurrentChannelInMaster(channel)
                                                    LiveTvManager.addToRecent(channel)
                                                    val intent = Intent(context, PlaybackActivity::class.java)
                                                    startActivity(intent)
                                                },
                                                onMoreOptionsClick = {
                                                    selectedChannelForQuickAction = channel
                                                },
                                                currentTheme = currentTheme,
                                                modifier = Modifier.reorderableListItem(reorderState, itemKey, index)
                                            )
                                        } else {
                                            ChannelListItem(
                                                channel = channel,
                                                isFavorite = isFav,
                                                onFavoriteToggle = {
                                                    if (isFav) {
                                                        favoritesRepository.removeFavorite(channel)
                                                    } else {
                                                        favoritesRepository.addFavorite(channel)
                                                    }
                                                    favoriteChannels = favoritesRepository.getFavorites()
                                                },
                                                onClick = {
                                                    LiveTvManager.updatePlaylist(displayChannels.toList(), index)
                                                    LiveTvManager.setCurrentChannelInMaster(channel)
                                                    LiveTvManager.addToRecent(channel)
                                                    val intent = Intent(context, PlaybackActivity::class.java)
                                                    startActivity(intent)
                                                },
                                                onMoreOptionsClick = {
                                                    selectedChannelForQuickAction = channel
                                                },
                                                currentTheme = currentTheme,
                                                modifier = Modifier.reorderableListItem(reorderState, itemKey, index)
                                            )
                                        }
                                    }
                                }
                            }
                            else -> { // "2_COLUMNS"
                                val gridState = rememberLazyGridState()
                                val reorderState = rememberReorderableLazyGridState(
                                    gridState = gridState,
                                    onMove = { fromIndex, toIndex ->
                                        if (fromIndex in displayChannels.indices && toIndex in displayChannels.indices) {
                                            val item = displayChannels.removeAt(fromIndex)
                                            displayChannels.add(toIndex, item)
                                        }
                                    },
                                    onDragEnd = {
                                        if (searchQuery.isBlank()) {
                                            ChannelOrderManager.saveChannelOrderForCategory(
                                                context,
                                                selectedCategory,
                                                displayChannels.toList()
                                            )
                                            LiveTvManager.updatePlaylist(displayChannels.toList(), 0)
                                        }
                                    }
                                )

                                LazyVerticalGrid(
                                    state = gridState,
                                    columns = if (isPlayzCategory) GridCells.Adaptive(minSize = 320.dp) else GridCells.Fixed(2),
                                    contentPadding = PaddingValues(if (isPlayzCategory) 12.dp else 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(
                                        items = displayChannels,
                                        key = { _, channel -> "${channel.id}_${channel.streamUrl}_${channel.name}" }
                                    ) { index, channel ->
                                        val itemKey = "${channel.id}_${channel.streamUrl}_${channel.name}"
                                        val isFav = favoriteChannels.any { it.streamUrl == channel.streamUrl }
                                        if (isPlayzCategory) {
                                            PlayzMatchCard(
                                                channel = channel,
                                                isFavorite = isFav,
                                                onFavoriteToggle = {
                                                    if (isFav) {
                                                        favoritesRepository.removeFavorite(channel)
                                                    } else {
                                                        favoritesRepository.addFavorite(channel)
                                                    }
                                                    favoriteChannels = favoritesRepository.getFavorites()
                                                },
                                                onClick = {
                                                    LiveTvManager.updatePlaylist(displayChannels.toList(), index)
                                                    LiveTvManager.setCurrentChannelInMaster(channel)
                                                    LiveTvManager.addToRecent(channel)
                                                    val intent = Intent(context, PlaybackActivity::class.java)
                                                    startActivity(intent)
                                                },
                                                onMoreOptionsClick = {
                                                    selectedChannelForQuickAction = channel
                                                },
                                                currentTheme = currentTheme,
                                                modifier = Modifier.reorderableGridItem(reorderState, itemKey, index)
                                            )
                                        } else {
                                            ChannelCard(
                                                channel = channel,
                                                isFavorite = isFav,
                                                onFavoriteToggle = {
                                                    if (isFav) {
                                                        favoritesRepository.removeFavorite(channel)
                                                    } else {
                                                        favoritesRepository.addFavorite(channel)
                                                    }
                                                    favoriteChannels = favoritesRepository.getFavorites()
                                                },
                                                onClick = {
                                                    LiveTvManager.updatePlaylist(displayChannels.toList(), index)
                                                    LiveTvManager.setCurrentChannelInMaster(channel)
                                                    LiveTvManager.addToRecent(channel)
                                                    val intent = Intent(context, PlaybackActivity::class.java)
                                                    startActivity(intent)
                                                },
                                                onMoreOptionsClick = {
                                                    selectedChannelForQuickAction = channel
                                                },
                                                currentTheme = currentTheme,
                                                modifier = Modifier.reorderableGridItem(reorderState, itemKey, index)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Settings Modal Bottom Sheet
            if (showSettingsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSettingsSheet = false },
                    containerColor = currentTheme.topBar,
                    contentColor = currentTheme.primaryText,
                    scrimColor = Color.Black.copy(alpha = 0.6f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                            .navigationBarsPadding()
                    ) {
                        Text(
                            text = "Settings",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = currentTheme.primaryText,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        SettingsOptionItem(
                            title = "Providers",
                            icon = Icons.Default.Settings,
                            onClick = {
                                showSettingsSheet = false
                                startActivity(Intent(context, ProviderSettingsActivity::class.java))
                            },
                            currentTheme = currentTheme
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SettingsOptionItem(
                            title = "Plugins",
                            icon = Icons.Default.Star,
                            onClick = {
                                showSettingsSheet = false
                                startActivity(Intent(context, PluginActivity::class.java))
                            },
                            currentTheme = currentTheme
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SettingsOptionItem(
                            title = "Theme Settings",
                            icon = Icons.Default.Settings,
                            subtitle = "Personalize colors and layout style",
                            onClick = {
                                showSettingsSheet = false
                                startActivity(Intent(context, ThemeSettingsActivity::class.java))
                            },
                            currentTheme = currentTheme
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SettingsOptionItem(
                            title = "Stream Health & Failover",
                            icon = Icons.Default.PlayArrow,
                            subtitle = "Auto-failover, stall watchdog & buffer settings",
                            onClick = {
                                showSettingsSheet = false
                                startActivity(Intent(context, io.github.rubayet123.tvlive.ui.settings.StreamHealthSettingsActivity::class.java))
                            },
                            currentTheme = currentTheme
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SettingsOptionItem(
                            title = "Reorder Categories",
                            icon = Icons.Default.List,
                            subtitle = "Customize category tab and row order",
                            onClick = {
                                showSettingsSheet = false
                                startActivity(Intent(context, CategorySettingsActivity::class.java))
                            },
                            currentTheme = currentTheme
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SettingsOptionItem(
                            title = "Hidden Channels",
                            icon = Icons.Default.Clear,
                            subtitle = "View and restore hidden channels",
                            onClick = {
                                showSettingsSheet = false
                                showHiddenChannelsSheet = true
                            },
                            currentTheme = currentTheme
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SettingsOptionItem(
                            title = "Player Default Orientation",
                            icon = Icons.Default.Refresh,
                            onClick = {
                                showSettingsSheet = false
                                val prefs = requireContext().getSharedPreferences("tv_live_prefs", Context.MODE_PRIVATE)
                                val currentPref = prefs.getString("pref_player_orientation", "portrait") ?: "portrait"
                                val options = arrayOf("Portrait First (Default)", "Auto-Rotate / Sensor", "Landscape First")
                                val selectedIndex = when (currentPref) {
                                    "sensor" -> 1
                                    "landscape" -> 2
                                    else -> 0
                                }
                                android.app.AlertDialog.Builder(context)
                                    .setTitle("Default Player Orientation")
                                    .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                                        val newPref = when (which) {
                                            1 -> "sensor"
                                            2 -> "landscape"
                                            else -> "portrait"
                                        }
                                        prefs.edit().putString("pref_player_orientation", newPref).apply()
                                        android.widget.Toast.makeText(context, "Default set to: ${options[which]}", android.widget.Toast.LENGTH_SHORT).show()
                                        dialog.dismiss()
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            },
                            currentTheme = currentTheme
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SettingsOptionItem(
                            title = "Backup & Restore",
                            icon = Icons.Default.Share,
                            subtitle = "Export or import your playlists, favorites & categories",
                            onClick = {
                                showSettingsSheet = false
                                startActivity(Intent(context, io.github.rubayet123.tvlive.ui.settings.BackupSettingsActivity::class.java))
                            },
                            currentTheme = currentTheme
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SettingsOptionItem(
                            title = "Credits",
                            icon = Icons.Default.Info,
                            onClick = {
                                showSettingsSheet = false
                                android.app.AlertDialog.Builder(context)
                                    .setTitle("TV Live")
                                    .setMessage("TV Live v1.0\nCreated by: Rubayet Alam\nGitHub: https://github.com/Rubayet123")
                                    .setPositiveButton("OK") { d, _ -> d.dismiss() }
                                    .show()
                            },
                            currentTheme = currentTheme
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // Quick Action Modal Bottom Sheet
            selectedChannelForQuickAction?.let { ch ->
                io.github.rubayet123.tvlive.ui.dialogs.ChannelQuickActionSheet(
                    channel = ch,
                    onDismiss = { selectedChannelForQuickAction = null },
                    onOpenChangeCategory = {
                        selectedChannelForCategoryChange = ch
                    },
                    onChannelUpdated = {
                        lifecycleScope.launch {
                            val favs = withContext(Dispatchers.IO) { favoritesRepository.getFavorites() }
                            favoriteChannels = favs
                            refreshTrigger++
                        }
                    }
                )
            }

            // Hidden Channels Modal Bottom Sheet
            if (showHiddenChannelsSheet) {
                io.github.rubayet123.tvlive.ui.dialogs.HiddenChannelsSheet(
                    allChannels = allChannels,
                    onDismiss = { showHiddenChannelsSheet = false },
                    onChannelsUpdated = {
                        lifecycleScope.launch {
                            val favs = withContext(Dispatchers.IO) { favoritesRepository.getFavorites() }
                            favoriteChannels = favs
                            refreshTrigger++
                        }
                    }
                )
            }

            // Category Change Modal Bottom Sheet
            selectedChannelForCategoryChange?.let { ch ->
                io.github.rubayet123.tvlive.ui.dialogs.ChangeChannelCategorySheet(
                    channel = ch,
                    onDismiss = { selectedChannelForCategoryChange = null },
                    onCategoryChanged = {
                        val targetChannel = ch
                        selectedChannelForCategoryChange = null
                        
                        // Instant in-memory update with zero network delay
                        val overrideCat = io.github.rubayet123.tvlive.util.CategoryOverrideManager.getOverrideCategory(context, targetChannel)
                        val targetKeys = io.github.rubayet123.tvlive.util.CanonicalKeyHelper.getLookupKeys(targetChannel).toSet()

                        allChannels = allChannels.map { channelItem ->
                            val itemKeys = io.github.rubayet123.tvlive.util.CanonicalKeyHelper.getLookupKeys(channelItem)
                            val isMatch = channelItem.id == targetChannel.id ||
                                    channelItem.streamUrl == targetChannel.streamUrl ||
                                    itemKeys.any { targetKeys.contains(it) }

                            if (isMatch) {
                                val resolvedCat = overrideCat ?: (channelItem.group ?: "Uncategorized")
                                channelItem.copy(group = resolvedCat)
                            } else {
                                channelItem
                            }
                        }

                        // Ensure master playlist retains exact category-ordered structure
                        val orderedChannels = io.github.rubayet123.tvlive.util.CategoryOrderManager.getCategoryOrderedChannels(context, allChannels)
                        allChannels = orderedChannels
                        LiveTvManager.setMasterPlaylist(orderedChannels, context)

                        // Instantly re-calculate categories list
                        val extractedCategories = allChannels
                            .mapNotNull { it.group?.trim() }
                            .filter { it.isNotBlank() }
                            .map { raw ->
                                raw.lowercase().replaceFirstChar { char ->
                                    if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
                                }
                            }
                            .distinct()

                        io.github.rubayet123.tvlive.util.CategoryOrderManager.saveDiscoveredCategories(context, extractedCategories)
                        val sortedExtracted = io.github.rubayet123.tvlive.util.CategoryOrderManager.sortCategories(extractedCategories, context)
                        categories = listOf("All", "Favorites") + sortedExtracted

                        refreshTrigger++
                    }
                )
            }
        }
    }

    @Composable
    private fun PlayzMatchCard(
        channel: Channel,
        isFavorite: Boolean,
        onFavoriteToggle: () -> Unit,
        onClick: () -> Unit,
        onMoreOptionsClick: (() -> Unit)? = null,
        currentTheme: AppTheme,
        modifier: Modifier = Modifier
    ) {
        val isLive = channel.name.startsWith("🔴")
        val cleanName = channel.name.removePrefix("🔴").removePrefix("🔜").removePrefix("✅").trim()

        val parts = channel.subtitle?.split(" • ")
        val tournament = parts?.getOrNull(0)?.ifBlank { null }
        val timeStr = parts?.getOrNull(1)?.ifBlank { null }

        Card(
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = currentTheme.cardBg),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isLive) Color(0xFFEF4444).copy(alpha = 0.6f) else currentTheme.cardBg.copy(alpha = 0.6f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isLive) 4.dp else 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Header Row inside Card: Tournament Pill & Live / Kickoff Badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        color = currentTheme.accent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, currentTheme.accent.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = tournament ?: "Playz Live",
                            color = currentTheme.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (isLive) {
                        Surface(
                            color = Color(0xFFDC2626),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "LIVE",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    } else if (!timeStr.isNullOrBlank()) {
                        Surface(
                            color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "⏰ $timeStr",
                                color = Color(0xFFFBBF24),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = currentTheme.primaryBg.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "UPCOMING",
                                color = currentTheme.secondaryText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Match Poster / Banner (16:9)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
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
                        modifier = Modifier.fillMaxSize()
                    )

                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color(0xFFEF4444) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Bottom Content: Match Title & Kickoff info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cleanName,
                            color = currentTheme.primaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (!tournament.isNullOrBlank() && !timeStr.isNullOrBlank()) "$tournament • $timeStr" else (channel.subtitle ?: "Playz Live"),
                            color = currentTheme.secondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (channel.sources.size > 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = currentTheme.accent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, currentTheme.accent.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "${channel.sources.size} Servers",
                                color = currentTheme.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (onMoreOptionsClick != null) {
                        IconButton(
                            onClick = onMoreOptionsClick,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = currentTheme.secondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ChannelCard(
        channel: Channel,
        isFavorite: Boolean,
        onFavoriteToggle: () -> Unit,
        onClick: () -> Unit,
        onMoreOptionsClick: (() -> Unit)? = null,
        currentTheme: AppTheme,
        modifier: Modifier = Modifier
    ) {
        val isPlayz = channel.group.equals("Playz Live", ignoreCase = true) || !channel.subtitle.isNullOrBlank()
        val isLive = channel.name.startsWith("🔴")
        val cleanName = if (isPlayz) channel.name.removePrefix("🔴").removePrefix("🔜").removePrefix("✅").trim() else channel.name

        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = currentTheme.cardBg),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isPlayz && isLive) Color(0xFFEF4444).copy(alpha = 0.5f) else currentTheme.cardBg.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Logo Image Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(currentTheme.primaryBg),
                        contentAlignment = Alignment.Center
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
                                .padding(8.dp)
                        )

                        if (isPlayz && isLive) {
                            Surface(
                                color = Color(0xFFDC2626),
                                shape = RoundedCornerShape(topStart = 10.dp, bottomEnd = 8.dp),
                                modifier = Modifier.align(Alignment.TopStart)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color.White))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "LIVE",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Title & Group Tag
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = if (onMoreOptionsClick != null) 24.dp else 0.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = cleanName,
                            color = currentTheme.primaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = channel.subtitle?.ifBlank { null } ?: channel.group?.ifBlank { "General" } ?: "General",
                                color = currentTheme.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (channel.sources.size > 1) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = currentTheme.accent.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, currentTheme.accent.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "${channel.sources.size} srcs",
                                        color = currentTheme.accent,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Top Right Favorite Button
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFEF4444) else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Bottom Right 3-Dots Menu Button
                if (onMoreOptionsClick != null) {
                    IconButton(
                        onClick = onMoreOptionsClick,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ChannelCardCompact(
        channel: Channel,
        isFavorite: Boolean,
        onFavoriteToggle: () -> Unit,
        onClick: () -> Unit,
        onMoreOptionsClick: (() -> Unit)? = null,
        currentTheme: AppTheme,
        modifier: Modifier = Modifier
    ) {
        val isPlayz = channel.group.equals("Playz Live", ignoreCase = true) || !channel.subtitle.isNullOrBlank()
        val isLive = channel.name.startsWith("🔴")
        val cleanName = if (isPlayz) channel.name.removePrefix("🔴").removePrefix("🔜").removePrefix("✅").trim() else channel.name

        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(145.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = currentTheme.cardBg),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isPlayz && isLive) Color(0xFFEF4444).copy(alpha = 0.5f) else currentTheme.cardBg.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(currentTheme.primaryBg),
                        contentAlignment = Alignment.Center
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
                                .padding(4.dp)
                        )

                        if (isPlayz && isLive) {
                            Surface(
                                color = Color(0xFFDC2626),
                                shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 6.dp),
                                modifier = Modifier.align(Alignment.TopStart)
                            ) {
                                Text(
                                    text = "LIVE",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = if (onMoreOptionsClick != null) 20.dp else 0.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = cleanName,
                            color = currentTheme.primaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = channel.subtitle?.ifBlank { null } ?: channel.group?.ifBlank { "General" } ?: "General",
                                color = currentTheme.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (channel.sources.size > 1) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    color = currentTheme.accent.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, currentTheme.accent.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "${channel.sources.size}",
                                        color = currentTheme.accent,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFEF4444) else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                if (onMoreOptionsClick != null) {
                    IconButton(
                        onClick = onMoreOptionsClick,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ChannelListItem(
        channel: Channel,
        isFavorite: Boolean,
        onFavoriteToggle: () -> Unit,
        onClick: () -> Unit,
        onMoreOptionsClick: (() -> Unit)? = null,
        currentTheme: AppTheme,
        modifier: Modifier = Modifier
    ) {
        val isPlayz = channel.group.equals("Playz Live", ignoreCase = true) || !channel.subtitle.isNullOrBlank()
        val isLive = channel.name.startsWith("🔴")
        val cleanName = if (isPlayz) channel.name.removePrefix("🔴").removePrefix("🔜").removePrefix("✅").trim() else channel.name

        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(72.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = currentTheme.cardBg),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isPlayz && isLive) Color(0xFFEF4444).copy(alpha = 0.5f) else currentTheme.cardBg.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = if (isPlayz) 80.dp else 64.dp, height = 52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(currentTheme.primaryBg),
                    contentAlignment = Alignment.Center
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
                            .padding(4.dp)
                    )

                    if (isPlayz && isLive) {
                        Surface(
                            color = Color(0xFFDC2626),
                            shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 6.dp),
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Text(
                                text = "LIVE",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = cleanName,
                        color = currentTheme.primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = channel.subtitle?.ifBlank { null } ?: channel.group?.ifBlank { "General" } ?: "General",
                            color = currentTheme.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (channel.sources.size > 1) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = currentTheme.accent.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, currentTheme.accent.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "${channel.sources.size} Sources",
                                    color = currentTheme.accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(currentTheme.primaryBg.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFEF4444) else currentTheme.secondaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (onMoreOptionsClick != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onMoreOptionsClick,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(currentTheme.primaryBg.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = currentTheme.secondaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingsOptionItem(
        title: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        onClick: () -> Unit,
        subtitle: String? = null,
        currentTheme: AppTheme
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = currentTheme.cardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, currentTheme.cardBg.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                        contentDescription = null,
                        tint = currentTheme.accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = currentTheme.primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = currentTheme.secondaryText,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = currentTheme.secondaryText.copy(alpha = 0.5f)
                )
            }
        }
    }

    private suspend fun checkAndTriggerAutoScrape() = withContext(Dispatchers.IO) {
        try {
            val anyScraped = io.github.rubayet123.tvlive.scraper.PluginScraperManager.autoScrapeActivePluginsIfNeeded(requireContext())
            if (anyScraped) {
                withContext(Dispatchers.Main) {
                    reloadChannels()
                }
            }
        } catch (e: Exception) {
            Log.e("MobileHomeFragment", "Auto-scrape check failed", e)
        }
    }
}
