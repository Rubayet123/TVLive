package io.github.rubayet123.tvlive.util

import android.content.Context
import io.github.rubayet123.tvlive.model.Channel
import org.json.JSONArray
import java.util.Locale

object CategoryOrderManager {

    private const val PREF_NAME = "category_prefs"
    private const val KEY_SAVED_ORDER = "category_order_json"
    private const val KEY_DISCOVERED_CATEGORIES = "discovered_categories_json"

    fun getSavedCategoryOrder(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SAVED_ORDER, null) ?: return emptyList()
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

    fun saveCategoryOrder(context: Context, order: List<String>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        order.distinct().forEach { jsonArray.put(it) }
        prefs.edit().putString(KEY_SAVED_ORDER, jsonArray.toString()).apply()
    }

    fun getDiscoveredCategories(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_DISCOVERED_CATEGORIES, null) ?: return emptyList()
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

    fun saveDiscoveredCategories(context: Context, categories: List<String>) {
        if (categories.isEmpty()) return
        val existing = getDiscoveredCategories(context).toMutableList()
        categories.forEach { cat ->
            if (!existing.contains(cat)) {
                existing.add(cat)
            }
        }
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        existing.distinct().forEach { jsonArray.put(it) }
        prefs.edit().putString(KEY_DISCOVERED_CATEGORIES, jsonArray.toString()).apply()
    }

    fun syncFromActiveChannels(context: Context, channels: List<Channel>): List<String> {
        val activeCategories = channels
            .mapNotNull { it.group?.trim() }
            .filter { it.isNotBlank() }
            .map { normalizeCategoryName(it) }
            .filter { it != "All" && it != "Favorites" }
            .distinct()

        // Overwrite discovered categories with exactly the active categories found in master playlist
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        activeCategories.forEach { jsonArray.put(it) }
        prefs.edit().putString(KEY_DISCOVERED_CATEGORIES, jsonArray.toString()).apply()

        // Clean saved order to only keep existing active categories
        val currentSavedOrder = getSavedCategoryOrder(context)
        if (currentSavedOrder.isNotEmpty()) {
            val activeSet = activeCategories.toSet()
            val cleanedOrder = currentSavedOrder.filter { activeSet.contains(it) }
            saveCategoryOrder(context, cleanedOrder)
        }

        return sortCategories(activeCategories, context)
    }

    fun sortCategories(availableCategories: List<String>, context: Context): List<String> {
        if (availableCategories.isEmpty()) return emptyList()

        val savedOrder = getSavedCategoryOrder(context)
        if (savedOrder.isEmpty()) {
            return availableCategories
        }

        val availableSet = availableCategories.toSet()
        val sortedList = mutableListOf<String>()

        // 1. Add categories in saved order if present in availableCategories
        savedOrder.forEach { cat ->
            if (availableSet.contains(cat) && !sortedList.contains(cat)) {
                sortedList.add(cat)
            }
        }

        // 2. Add remaining available categories that were not in savedOrder
        val unlisted = availableCategories.filter { !sortedList.contains(it) && !it.equals("Uncategorized", ignoreCase = true) }
        sortedList.addAll(unlisted)

        // 3. Keep "Uncategorized" at the end if present and not already positioned
        val uncategorized = availableCategories.firstOrNull { it.equals("Uncategorized", ignoreCase = true) }
        if (uncategorized != null && !sortedList.contains(uncategorized)) {
            sortedList.add(uncategorized)
        }

        return sortedList
    }

    fun resetCategoryOrder(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_SAVED_ORDER).apply()
    }

    fun normalizeCategoryName(rawGroup: String?): String {
        val trimmed = rawGroup?.trim()
        if (trimmed.isNullOrBlank()) {
            return "Uncategorized"
        }
        return trimmed.lowercase().replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
        }
    }

    fun getCategoryOrderedChannels(context: Context, channels: List<Channel>): List<Channel> {
        if (channels.isEmpty()) return emptyList()

        val grouped = channels.groupBy { normalizeCategoryName(it.group) }
        val discoveredNames = grouped.keys.toList()
        saveDiscoveredCategories(context, discoveredNames)
        val sortedCategoryNames = sortCategories(discoveredNames, context)

        val orderedList = ArrayList<Channel>(channels.size)
        sortedCategoryNames.forEach { catName ->
            grouped[catName]?.let { catChannels ->
                val channelOrdered = ChannelOrderManager.applyCategoryChannelOrder(context, catName, catChannels)
                orderedList.addAll(channelOrdered)
            }
        }

        // Safety fallback if any channels were missed
        if (orderedList.size < channels.size) {
            val addedSet = orderedList.toSet()
            channels.forEach { ch ->
                if (!addedSet.contains(ch)) {
                    orderedList.add(ch)
                }
            }
        }

        return orderedList
    }
}
