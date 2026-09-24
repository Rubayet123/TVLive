package io.github.rubayet123.tvlive

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.rubayet123.tvlive.data.LiveTvManager
import io.github.rubayet123.tvlive.data.M3uParser
import io.github.rubayet123.tvlive.data.SourceRepository
import io.github.rubayet123.tvlive.model.Channel
import io.github.rubayet123.tvlive.model.SettingsCardItem
import io.github.rubayet123.tvlive.model.Source
import io.github.rubayet123.tvlive.data.network.NetworkClient
import okhttp3.OkHttpClient

import io.github.rubayet123.tvlive.scraper.ScrapeRepository
import io.github.rubayet123.tvlive.scraper.ScrapeService
import io.github.rubayet123.tvlive.scraper.M3uBuilder
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast

import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import io.github.rubayet123.tvlive.util.ThemeManager

/**
 * Loads a grid of cards with movies to browse.
 */
class MainFragment : BrowseSupportFragment() {

    private lateinit var mBackgroundManager: BackgroundManager
    private var mDefaultBackground: Drawable? = null
    private lateinit var mMetrics: DisplayMetrics

    private val repository by lazy { SourceRepository(requireContext()) }
    private val client = NetworkClient.client
    private val parser = M3uParser(client)
    private var allLoadedChannels: List<Channel> = emptyList()

    override fun onResume() {
        super.onResume()
        applyTheme()
        loadRows()
    }

    private fun updateFavoritesRowOnly() {
        val currentAdapter = adapter as? ArrayObjectAdapter ?: return
        lifecycleScope.launch {
            val favorites = withContext(Dispatchers.IO) { favoritesRepository.getFavorites() }
            val firstRow = if (currentAdapter.size() > 0) currentAdapter.get(0) as? ListRow else null
            if (firstRow != null && firstRow.headerItem.name == "Favorites") {
                val favAdapter = firstRow.adapter as? ArrayObjectAdapter
                if (favAdapter != null) {
                    favAdapter.clear()
                    favorites.forEach { favAdapter.add(it) }
                }
            } else if (favorites.isNotEmpty()) {
                val cardPresenter = CardPresenter()
                val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                favorites.forEach { listRowAdapter.add(it) }
                val header = HeaderItem(0, "Favorites")
                currentAdapter.add(0, ListRow(header, listRowAdapter))
            }
        }
    }

    private fun applyTheme() {
        val context = activity ?: return
        val currentTheme = ThemeManager.getSelectedTheme(context)
        
        // Apply to background manager
        if (::mBackgroundManager.isInitialized) {
            mBackgroundManager.color = currentTheme.primaryBgInt
        }
        
        // Apply brand color (fastlane header panel)
        brandColor = currentTheme.topBarInt
        
        // Apply search affordance color
        searchAffordanceColor = currentTheme.accentInt
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        Log.i(TAG, "onCreate")
        super.onActivityCreated(savedInstanceState)

        checkAndTriggerAutoScrape()

        prepareBackgroundManager()

        setupUIElements()

        loadRows()

        setupEventListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun prepareBackgroundManager() {

        mBackgroundManager = BackgroundManager.getInstance(activity)
        mBackgroundManager.attach(activity!!.window)
        mDefaultBackground = ContextCompat.getDrawable(activity!!, R.drawable.default_background)
        mMetrics = DisplayMetrics()
        activity!!.windowManager.defaultDisplay.getMetrics(mMetrics)
    }

    private fun setupUIElements() {
        title = getString(R.string.browse_title)
        // over title
        headersState = BrowseSupportFragment.HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true

        applyTheme()
    }

    private val favoritesRepository by lazy { io.github.rubayet123.tvlive.data.FavoritesRepository(requireContext()) }

    private fun buildRowsAdapter(channels: List<Channel>, favorites: List<Channel>, context: Context): Pair<ArrayObjectAdapter, List<Channel>> {
        val visibleChannels = io.github.rubayet123.tvlive.util.HiddenChannelsManager.filterVisibleChannels(context, channels)
        val visibleFavorites = io.github.rubayet123.tvlive.util.HiddenChannelsManager.filterVisibleChannels(context, favorites)

        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        val cardPresenter = CardPresenter()

        // 1. Favorites Row
        if (visibleFavorites.isNotEmpty()) {
            val listRowAdapter = ArrayObjectAdapter(cardPresenter)
            visibleFavorites.forEach { listRowAdapter.add(it) }
            val header = HeaderItem(0, "Favorites")
            rowsAdapter.add(ListRow(header, listRowAdapter))
        }

        // Group by normalized category name
        val groupedCategoriesRaw = visibleChannels.groupBy { 
            io.github.rubayet123.tvlive.util.CategoryOrderManager.normalizeCategoryName(it.group)
        }

        val discoveredNames = groupedCategoriesRaw.keys.toList()
        io.github.rubayet123.tvlive.util.CategoryOrderManager.saveDiscoveredCategories(context, discoveredNames)
        val sortedCategoryNames = io.github.rubayet123.tvlive.util.CategoryOrderManager.sortCategories(discoveredNames, context)

        val sortedMasterPlaylist = mutableListOf<Channel>()
        var indexCounter = 1L
        sortedCategoryNames.forEach { categoryName ->
            val rawCatChannels = groupedCategoriesRaw[categoryName]
            if (!rawCatChannels.isNullOrEmpty()) {
                val catChannels = io.github.rubayet123.tvlive.util.ChannelOrderManager.applyCategoryChannelOrder(context, categoryName, rawCatChannels)
                sortedMasterPlaylist.addAll(catChannels)
                val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                catChannels.forEach { listRowAdapter.add(it) }
                val header = HeaderItem(indexCounter++, categoryName)
                rowsAdapter.add(ListRow(header, listRowAdapter))
            }
        }

        // Add settings row at the end
        val gridHeader = HeaderItem(1000L, "SETTINGS")
        val settingsCardPresenter = SettingsCardPresenter()
        val gridRowAdapter = ArrayObjectAdapter(settingsCardPresenter)
        
        gridRowAdapter.add(
            SettingsCardItem(
                102L,
                "Providers",
                "",
                "",
                R.drawable.ic_settings_providers,
                io.github.rubayet123.tvlive.ui.settings.ProviderSettingsActivity::class.java
            )
        )
        gridRowAdapter.add(
            SettingsCardItem(
                103L,
                "Plugins",
                "",
                "",
                R.drawable.ic_settings_plugins,
                io.github.rubayet123.tvlive.ui.settings.PluginActivity::class.java
            )
        )
        gridRowAdapter.add(
            SettingsCardItem(
                106L,
                "Categories",
                "",
                "",
                R.drawable.ic_settings_categories,
                io.github.rubayet123.tvlive.ui.settings.CategorySettingsActivity::class.java
            )
        )
        gridRowAdapter.add(
            SettingsCardItem(
                105L,
                "Theme",
                "",
                "",
                R.drawable.ic_settings_theme,
                io.github.rubayet123.tvlive.ui.settings.ThemeSettingsActivity::class.java
            )
        )
        gridRowAdapter.add(
            SettingsCardItem(
                108L,
                "Stream Health",
                "",
                "",
                R.drawable.ic_settings_plugins,
                io.github.rubayet123.tvlive.ui.settings.StreamHealthSettingsActivity::class.java
            )
        )
        gridRowAdapter.add(
            SettingsCardItem(
                107L,
                "Backup & Restore",
                "",
                "",
                R.drawable.ic_settings_categories,
                io.github.rubayet123.tvlive.ui.settings.BackupSettingsActivity::class.java
            )
        )
        gridRowAdapter.add(
            SettingsCardItem(
                109L,
                "Hidden Channels",
                "",
                "",
                R.drawable.ic_settings_info,
                actionType = "HIDDEN_CHANNELS"
            )
        )
        gridRowAdapter.add(
            SettingsCardItem(
                104L,
                "About",
                "",
                "",
                R.drawable.ic_settings_info,
                actionType = "CREDIT"
            )
        )
        
        rowsAdapter.add(ListRow(gridHeader, gridRowAdapter))
        return Pair(rowsAdapter, sortedMasterPlaylist)
    }
    
    fun loadRows() {
        lifecycleScope.launch {
            val ctx = context?.applicationContext ?: return@launch
            // Immediate frame: Load from memory/disk cache first for instant display (< 50ms)
            val cachedChannels = withContext(Dispatchers.IO) {
                LiveTvManager.initializeFromDiskIfNeeded(ctx)
            }
            val initialFavs = withContext(Dispatchers.IO) { 
                io.github.rubayet123.tvlive.data.FavoritesRepository(ctx).getFavorites() 
            }

            if (cachedChannels.isNotEmpty()) {
                allLoadedChannels = cachedChannels
                val (instantRows, instantMaster) = buildRowsAdapter(cachedChannels, initialFavs, ctx)
                LiveTvManager.setMasterPlaylist(instantMaster, ctx)
                adapter = instantRows
            }

            // Background network fetch and sync
            val sources = withContext(Dispatchers.IO) { repository.getSources() }
            val fetchedChannels = mutableListOf<Channel>()
            
            withContext(Dispatchers.IO) {
                sources.filter { it.isActive }.forEachIndexed { index, source ->
                    try {
                        val categories = parser.parse(source.url, defaultProviderName = source.name, priority = index)
                        categories.forEach { category -> 
                            fetchedChannels.addAll(category.channels)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading source: ${source.name}", e)
                    }
                }
            }

            val processedChannels = if (fetchedChannels.isNotEmpty()) {
                withContext(Dispatchers.Default) {
                    val overridden = io.github.rubayet123.tvlive.util.CategoryOverrideManager.applyOverrides(ctx, fetchedChannels)
                    io.github.rubayet123.tvlive.util.ChannelDeduplicator.deduplicateChannels(ctx, overridden)
                }
            } else {
                emptyList()
            }

            allLoadedChannels = processedChannels
            val currentFavs = withContext(Dispatchers.IO) { 
                io.github.rubayet123.tvlive.data.FavoritesRepository(ctx).getFavorites() 
            }
            val (freshRows, freshMaster) = buildRowsAdapter(processedChannels, currentFavs, ctx)
            LiveTvManager.setMasterPlaylist(freshMaster, ctx)
            adapter = freshRows
        }
    }

    private fun setupEventListeners() {
        setOnSearchClickedListener {
            Toast.makeText(activity!!, "Implement your own in-app search", Toast.LENGTH_LONG)
                .show()
        }

        onItemViewClickedListener = ItemViewClickedListener()
        onItemViewSelectedListener = ItemViewSelectedListener()
    }

    private fun checkAndTriggerAutoScrape() {
        lifecycleScope.launch {
            try {
                val anyScraped = io.github.rubayet123.tvlive.scraper.PluginScraperManager.autoScrapeActivePluginsIfNeeded(requireContext())
                if (anyScraped) {
                    withContext(Dispatchers.Main) {
                        loadRows()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auto-scrape check failed", e)
            }
        }
    }

    private suspend fun saveM3uLocally(fileName: String, content: String) = withContext(Dispatchers.IO) {
        val file = java.io.File(requireContext().filesDir, fileName)
        java.io.FileOutputStream(file).use {
            it.write(content.toByteArray(Charsets.UTF_8))
        }
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row
        ) {
            if (isReorderModeActive) {
                confirmReorder()
                return
            }

            if (item is Channel) {
                // Find the list this channel belongs to
                val listRow = row as ListRow
                val adapter = listRow.adapter as ArrayObjectAdapter
                val playlist = (0 until adapter.size()).map { adapter.get(it) as Channel }
                val index = playlist.indexOf(item)
                
                io.github.rubayet123.tvlive.data.LiveTvManager.updatePlaylist(playlist, index)
                io.github.rubayet123.tvlive.data.LiveTvManager.setCurrentChannelInMaster(item)
                io.github.rubayet123.tvlive.data.LiveTvManager.addToRecent(item)

                val intent = Intent(activity!!, PlaybackActivity::class.java)
                startActivity(intent)
            } else if (item is SettingsCardItem) {
                if (item.actionType == "CREDIT") {
                     val builder = android.app.AlertDialog.Builder(activity)
                     builder.setTitle("TV Live")
                     builder.setMessage("TV Live v1.0\nCreated by: Rubayet Alam\nGitHub: https://github.com/Rubayet123")
                     builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                     builder.show()
                } else if (item.actionType == "HIDDEN_CHANNELS") {
                    io.github.rubayet123.tvlive.ui.dialogs.HiddenChannelsTvDialog.show(
                        requireContext(),
                        allLoadedChannels
                    ) {
                        loadRows()
                    }
                } else if (item.targetActivityClass != null) {
                    val intent = Intent(activity!!, item.targetActivityClass)
                    startActivity(intent)
                }
            } else if (item is String) {
                if (item == "Provider") {
                    val intent = Intent(activity!!, io.github.rubayet123.tvlive.ui.settings.ProviderSettingsActivity::class.java)
                    startActivity(intent)
                } else if (item == "Plugins") {
                    val intent = Intent(activity!!, io.github.rubayet123.tvlive.ui.settings.PluginActivity::class.java)
                    startActivity(intent)
                } else if (item == "Credit") {
                     val builder = android.app.AlertDialog.Builder(activity)
                     builder.setTitle("Credit")
                     builder.setMessage("Created by: Rubayet Alam\nGitHub: https://github.com/Rubayet123")
                     builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                     builder.show()
                }
            }
        }
    }

    fun refreshRowsInMemory() {
        val act = activity ?: return
        act.runOnUiThread {
            if (!isAdded || isDetached) return@runOnUiThread
            val ctx = context?.applicationContext ?: return@runOnUiThread
            try {
                val favs = io.github.rubayet123.tvlive.data.FavoritesRepository(ctx).getFavorites()
                val updatedChannels = io.github.rubayet123.tvlive.util.CategoryOverrideManager.applyOverrides(ctx, allLoadedChannels)
                val orderedChannels = io.github.rubayet123.tvlive.util.CategoryOrderManager.getCategoryOrderedChannels(ctx, updatedChannels)
                allLoadedChannels = orderedChannels
                val (freshRows, freshMaster) = buildRowsAdapter(orderedChannels, favs, ctx)
                LiveTvManager.setMasterPlaylist(freshMaster, ctx)
                adapter = freshRows
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh rows in memory", e)
            }
        }
    }

    var currentFocusedChannel: Channel? = null

    // --- TV In-Place Channel Reorder Mode ---
    var activeReorderingChannel: Channel? = null
    var activeReorderingRowAdapter: ArrayObjectAdapter? = null
    var activeReorderingCategoryName: String = ""
    private var activeReorderingRowIndex: Int = -1

    val isReorderModeActive: Boolean
        get() = activeReorderingChannel != null

    fun startReorderMode(channel: Channel) {
        activeReorderingChannel = channel
        CardPresenter.activeReorderChannelKey = io.github.rubayet123.tvlive.util.ChannelOrderManager.getChannelKey(channel)

        val rows = adapter as? ArrayObjectAdapter
        if (rows != null) {
            for (i in 0 until rows.size()) {
                val listRow = rows.get(i) as? ListRow ?: continue
                val rowAdapter = listRow.adapter as? ArrayObjectAdapter ?: continue
                val key = io.github.rubayet123.tvlive.util.ChannelOrderManager.getChannelKey(channel)
                for (j in 0 until rowAdapter.size()) {
                    val ch = rowAdapter.get(j) as? Channel ?: continue
                    if (io.github.rubayet123.tvlive.util.ChannelOrderManager.getChannelKey(ch) == key) {
                        activeReorderingRowAdapter = rowAdapter
                        activeReorderingCategoryName = listRow.headerItem.name
                        activeReorderingRowIndex = i
                        rowAdapter.notifyArrayItemRangeChanged(j, 1)
                        setSelectedPosition(i, true, ListRowPresenter.SelectItemViewHolderTask(j))
                        break
                    }
                }
                if (activeReorderingRowAdapter != null) break
            }
        }

        Toast.makeText(
            context,
            "Move Mode: Use ◀ / ▶ to move '${channel.name}'. Press OK to place.",
            Toast.LENGTH_LONG
        ).show()
    }

    fun moveReorderingChannelLeft(): Boolean {
        val channel = activeReorderingChannel ?: return false
        val rowAdapter = activeReorderingRowAdapter ?: return false
        val key = io.github.rubayet123.tvlive.util.ChannelOrderManager.getChannelKey(channel)
        var currentIndex = -1
        for (i in 0 until rowAdapter.size()) {
            val ch = rowAdapter.get(i) as? Channel ?: continue
            if (io.github.rubayet123.tvlive.util.ChannelOrderManager.getChannelKey(ch) == key) {
                currentIndex = i
                break
            }
        }

        if (currentIndex > 0) {
            val targetIndex = currentIndex - 1
            val item = rowAdapter.get(currentIndex) ?: return false
            rowAdapter.removeItems(currentIndex, 1)
            rowAdapter.add(targetIndex, item)
            persistActiveRowOrder()

            // Keep Leanback viewport centered on the moved card so it never goes off-screen
            if (activeReorderingRowIndex >= 0) {
                setSelectedPosition(activeReorderingRowIndex, true, ListRowPresenter.SelectItemViewHolderTask(targetIndex))
            }
            return true
        } else {
            Toast.makeText(context, "Start of row reached", Toast.LENGTH_SHORT).show()
            return true
        }
    }

    fun moveReorderingChannelRight(): Boolean {
        val channel = activeReorderingChannel ?: return false
        val rowAdapter = activeReorderingRowAdapter ?: return false
        val key = io.github.rubayet123.tvlive.util.ChannelOrderManager.getChannelKey(channel)
        var currentIndex = -1
        for (i in 0 until rowAdapter.size()) {
            val ch = rowAdapter.get(i) as? Channel ?: continue
            if (io.github.rubayet123.tvlive.util.ChannelOrderManager.getChannelKey(ch) == key) {
                currentIndex = i
                break
            }
        }

        if (currentIndex >= 0 && currentIndex < rowAdapter.size() - 1) {
            val targetIndex = currentIndex + 1
            val item = rowAdapter.get(currentIndex) ?: return false
            rowAdapter.removeItems(currentIndex, 1)
            rowAdapter.add(targetIndex, item)
            persistActiveRowOrder()

            // Keep Leanback viewport centered on the moved card so it never goes off-screen
            if (activeReorderingRowIndex >= 0) {
                setSelectedPosition(activeReorderingRowIndex, true, ListRowPresenter.SelectItemViewHolderTask(targetIndex))
            }
            return true
        } else {
            Toast.makeText(context, "End of row reached", Toast.LENGTH_SHORT).show()
            return true
        }
    }

    private fun persistActiveRowOrder() {
        val rowAdapter = activeReorderingRowAdapter ?: return
        val categoryName = activeReorderingCategoryName.ifBlank { "General" }
        val ctx = context?.applicationContext ?: return

        val channelList = mutableListOf<Channel>()
        for (i in 0 until rowAdapter.size()) {
            (rowAdapter.get(i) as? Channel)?.let { channelList.add(it) }
        }
        if (channelList.isNotEmpty()) {
            io.github.rubayet123.tvlive.util.ChannelOrderManager.saveChannelOrderForCategory(ctx, categoryName, channelList)
        }
    }

    fun confirmReorder(): Boolean {
        val channel = activeReorderingChannel ?: return false
        persistActiveRowOrder()
        val name = channel.name
        exitReorderMode()
        Toast.makeText(context, "Placed '$name' in new position", Toast.LENGTH_SHORT).show()
        return true
    }

    fun exitReorderMode(): Boolean {
        if (activeReorderingChannel == null) return false
        val channel = activeReorderingChannel
        val rowAdapter = activeReorderingRowAdapter
        activeReorderingChannel = null
        CardPresenter.activeReorderChannelKey = null
        activeReorderingRowAdapter = null
        activeReorderingCategoryName = ""
        activeReorderingRowIndex = -1

        rowAdapter?.let { adapter ->
            if (channel != null) {
                val key = io.github.rubayet123.tvlive.util.ChannelOrderManager.getChannelKey(channel)
                for (i in 0 until adapter.size()) {
                    val ch = adapter.get(i) as? Channel ?: continue
                    if (io.github.rubayet123.tvlive.util.ChannelOrderManager.getChannelKey(ch) == key) {
                        adapter.notifyArrayItemRangeChanged(i, 1)
                        break
                    }
                }
            }
        }
        return true
    }

    fun showQuickActionForSelected(): Boolean {
        val channel = currentFocusedChannel ?: return false
        val ctx = context ?: return false
        io.github.rubayet123.tvlive.ui.dialogs.ChannelQuickActionTvDialog.show(
            context = ctx,
            channel = channel,
            onOpenChangeCategory = {
                io.github.rubayet123.tvlive.ui.dialogs.ChangeChannelCategoryDialog.show(ctx, channel) {
                    refreshRowsInMemory()
                }
            },
            onStartMoveMode = {
                startReorderMode(channel)
            },
            onChannelUpdated = {
                refreshRowsInMemory()
            }
        )
        return true
    }

    private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
        override fun onItemSelected(
            itemViewHolder: Presenter.ViewHolder?, item: Any?,
            rowViewHolder: RowPresenter.ViewHolder, row: Row
        ) {
            currentFocusedChannel = item as? Channel
        }
    }
    private inner class GridItemPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
            val view = TextView(parent.context)
            view.layoutParams = ViewGroup.LayoutParams(GRID_ITEM_WIDTH, GRID_ITEM_HEIGHT)
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.setBackgroundColor(ContextCompat.getColor(activity!!, R.color.default_background))
            view.setTextColor(Color.WHITE)
            view.gravity = Gravity.CENTER
            return Presenter.ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
            (viewHolder.view as TextView).text = item as? String ?: ""
        }

        override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {}
    }

    companion object {
        private val TAG = "MainFragment"

        private val GRID_ITEM_WIDTH = 200
        private val GRID_ITEM_HEIGHT = 200
        private val NUM_ROWS = 6
        private val NUM_COLS = 15
    }
}