package io.github.rubayet123.tvlive.util

import android.content.Context
import io.github.rubayet123.tvlive.data.LiveTvManager
import io.github.rubayet123.tvlive.model.Channel
import org.json.JSONArray
import java.util.Collections

object ChannelOrderManager {

    private const val PREF_NAME = "channel_order_prefs"
    private const val KEY_PREFIX_CATEGORY = "cat_order_"
    private const val KEY_ALL_CATEGORIES_WITH_CUSTOM_ORDER = "categories_with_custom_order"

    /**
     * Unique stable key for a channel to identify its position regardless of playlist reloads.
     */
    fun getChannelKey(channel: Channel): String {
        return if (channel.id.isNotBlank()) {
            channel.id
        } else {
            val slug = CanonicalKeyHelper.toCanonicalSlug(channel.name)
            if (slug.isNotBlank()) slug else channel.name.trim().lowercase()
        }
    }

    /**
     * Retrieves the saved list of channel keys for a given category.
     */
    fun getSavedChannelKeys(context: Context, categoryName: String): List<String> {
        val normalizedCat = CategoryOrderManager.normalizeCategoryName(categoryName)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_PREFIX_CATEGORY + normalizedCat, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Saves the custom channel order for a category based on the provided list of channels.
     */
    fun saveChannelOrderForCategory(context: Context, categoryName: String, orderedChannels: List<Channel>) {
        val normalizedCat = CategoryOrderManager.normalizeCategoryName(categoryName)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        val savedKeys = mutableListOf<String>()
        
        orderedChannels.forEach { channel ->
            val key = getChannelKey(channel)
            if (!savedKeys.contains(key)) {
                savedKeys.add(key)
                jsonArray.put(key)
            }
        }

        prefs.edit().putString(KEY_PREFIX_CATEGORY + normalizedCat, jsonArray.toString()).apply()

        // Track category in set of custom orders
        val catSet = prefs.getStringSet(KEY_ALL_CATEGORIES_WITH_CUSTOM_ORDER, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        catSet.add(normalizedCat)
        prefs.edit().putStringSet(KEY_ALL_CATEGORIES_WITH_CUSTOM_ORDER, catSet).apply()

        // Synchronize in-memory Master Playlist so Channel Zap and Player immediately reflect the new order
        try {
            val currentMaster = LiveTvManager.getMasterPlaylist()
            if (currentMaster.isNotEmpty()) {
                val reorderedMaster = CategoryOrderManager.getCategoryOrderedChannels(context, currentMaster)
                LiveTvManager.setMasterPlaylist(reorderedMaster, context)
            }
        } catch (e: Exception) {
            // Non-critical fallback
        }
    }

    /**
     * Orders a list of channels for a category according to saved preferences.
     * Any new/unranked channels are cleanly appended at the end in their original relative order.
     */
    fun applyCategoryChannelOrder(context: Context, categoryName: String, channels: List<Channel>): List<Channel> {
        if (channels.size <= 1) return channels
        val savedKeys = getSavedChannelKeys(context, categoryName)
        if (savedKeys.isEmpty()) return channels

        val channelMap = mutableMapOf<String, MutableList<Channel>>()
        channels.forEach { ch ->
            val key = getChannelKey(ch)
            val list = channelMap.getOrPut(key) { mutableListOf() }
            list.add(ch)
        }

        val orderedResult = mutableListOf<Channel>()
        val consumedChannels = mutableSetOf<Channel>()

        // 1. Place channels in the saved custom order
        savedKeys.forEach { key ->
            channelMap[key]?.forEach { ch ->
                if (!consumedChannels.contains(ch)) {
                    orderedResult.add(ch)
                    consumedChannels.add(ch)
                }
            }
        }

        // 2. Append any remaining channels that were not in the saved order list
        channels.forEach { ch ->
            if (!consumedChannels.contains(ch)) {
                orderedResult.add(ch)
                consumedChannels.add(ch)
            }
        }

        return orderedResult
    }

    /**
     * Swaps or moves a channel in the list and immediately persists the new order.
     */
    fun moveChannel(
        context: Context,
        categoryName: String,
        fromIndex: Int,
        toIndex: Int,
        currentList: List<Channel>
    ): List<Channel> {
        if (fromIndex < 0 || fromIndex >= currentList.size || toIndex < 0 || toIndex >= currentList.size || fromIndex == toIndex) {
            return currentList
        }
        val mutable = currentList.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        saveChannelOrderForCategory(context, categoryName, mutable)
        return mutable
    }

    /**
     * Resets the channel order for a specific category to default playlist order.
     */
    fun resetCategoryChannelOrder(context: Context, categoryName: String) {
        val normalizedCat = CategoryOrderManager.normalizeCategoryName(categoryName)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_PREFIX_CATEGORY + normalizedCat).apply()

        val catSet = prefs.getStringSet(KEY_ALL_CATEGORIES_WITH_CUSTOM_ORDER, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        catSet.remove(normalizedCat)
        prefs.edit().putStringSet(KEY_ALL_CATEGORIES_WITH_CUSTOM_ORDER, catSet).apply()
    }

    /**
     * Resets all custom channel orderings across all categories.
     */
    fun resetAllChannelOrders(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
