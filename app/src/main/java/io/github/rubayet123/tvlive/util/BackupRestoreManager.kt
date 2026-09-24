package io.github.rubayet123.tvlive.util

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.github.rubayet123.tvlive.data.FavoritesRepository
import io.github.rubayet123.tvlive.data.SourceRepository
import io.github.rubayet123.tvlive.model.Channel
import io.github.rubayet123.tvlive.model.Source
import java.io.InputStream
import java.io.OutputStream

data class BackupData(
    val version: Int = 1,
    val appName: String = "TV Live",
    val timestamp: Long = System.currentTimeMillis(),
    val themeId: String? = null,
    val homeLayout: String? = null,
    val playerOrientation: String? = null,
    val sources: List<Source> = emptyList(),
    val favorites: List<Channel> = emptyList(),
    val categoryOrder: List<String> = emptyList(),
    val discoveredCategories: List<String> = emptyList(),
    val categoryOverrides: Map<String, String> = emptyMap(),
    val providerAssociations: Map<String, String> = emptyMap()
)

object BackupRestoreManager {
    private const val TAG = "BackupRestoreManager"
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun createBackupJson(context: Context): String {
        val sourceRepository = SourceRepository(context)
        val favoritesRepository = FavoritesRepository(context)
        val homePrefs = context.getSharedPreferences("tv_live_prefs", Context.MODE_PRIVATE)

        val sources = sourceRepository.getSources()
        val favorites = favoritesRepository.getFavorites()
        val homeLayout = homePrefs.getString("pref_home_layout", "2_COLUMNS")
        val playerOrientation = homePrefs.getString("pref_player_orientation", "portrait")
        val themeId = ThemeManager.getSelectedTheme(context).id

        val categoryOrder = CategoryOrderManager.getSavedCategoryOrder(context)
        val discoveredCategories = CategoryOrderManager.getDiscoveredCategories(context)

        val overridesPrefs = context.getSharedPreferences("channel_category_overrides_prefs", Context.MODE_PRIVATE)
        val overridesJsonStr = overridesPrefs.getString("category_overrides_json", null)
        val providersJsonStr = overridesPrefs.getString("provider_associations_json", null)

        val mapType = object : TypeToken<Map<String, String>>() {}.type
        val categoryOverrides: Map<String, String> = if (!overridesJsonStr.isNullOrBlank()) {
            try { gson.fromJson(overridesJsonStr, mapType) ?: emptyMap() } catch (e: Exception) { emptyMap() }
        } else emptyMap()

        val providerAssociations: Map<String, String> = if (!providersJsonStr.isNullOrBlank()) {
            try { gson.fromJson(providersJsonStr, mapType) ?: emptyMap() } catch (e: Exception) { emptyMap() }
        } else emptyMap()

        val backup = BackupData(
            version = 1,
            appName = "TV Live",
            timestamp = System.currentTimeMillis(),
            themeId = themeId,
            homeLayout = homeLayout,
            playerOrientation = playerOrientation,
            sources = sources,
            favorites = favorites,
            categoryOrder = categoryOrder,
            discoveredCategories = discoveredCategories,
            categoryOverrides = categoryOverrides,
            providerAssociations = providerAssociations
        )

        return gson.toJson(backup)
    }

    fun restoreBackupJson(context: Context, jsonString: String): Boolean {
        return try {
            val backup = gson.fromJson(jsonString, BackupData::class.java) ?: return false

            // 1. Theme
            backup.themeId?.let { id ->
                AppTheme.values().find { it.id == id }?.let { theme ->
                    ThemeManager.setSelectedTheme(context, theme)
                }
            }

            // 2. Home Prefs
            val homePrefs = context.getSharedPreferences("tv_live_prefs", Context.MODE_PRIVATE)
            val editor = homePrefs.edit()
            backup.homeLayout?.let { editor.putString("pref_home_layout", it) }
            backup.playerOrientation?.let { editor.putString("pref_player_orientation", it) }

            // Sources
            if (backup.sources.isNotEmpty()) {
                editor.putString("saved_sources", gson.toJson(backup.sources))
            }

            // Favorites
            if (backup.favorites.isNotEmpty()) {
                editor.putString("favorite_channels", gson.toJson(backup.favorites))
            }
            editor.apply()

            // 3. Category Order & Discovered
            if (backup.categoryOrder.isNotEmpty()) {
                CategoryOrderManager.saveCategoryOrder(context, backup.categoryOrder)
            }
            if (backup.discoveredCategories.isNotEmpty()) {
                CategoryOrderManager.saveDiscoveredCategories(context, backup.discoveredCategories)
            }

            // 4. Overrides & Providers
            if (backup.categoryOverrides.isNotEmpty() || backup.providerAssociations.isNotEmpty()) {
                val overridesPrefs = context.getSharedPreferences("channel_category_overrides_prefs", Context.MODE_PRIVATE)
                val overridesEditor = overridesPrefs.edit()
                if (backup.categoryOverrides.isNotEmpty()) {
                    val expandedOverrides = mutableMapOf<String, String>()
                    val expandedProviders = mutableMapOf<String, String>()

                    for ((rawKey, category) in backup.categoryOverrides) {
                        expandedOverrides[rawKey] = category
                        val provider = backup.providerAssociations[rawKey] ?: "Backup Import"
                        expandedProviders[rawKey] = provider

                        val aliases = CanonicalKeyHelper.getLookupKeysForRawKey(rawKey)
                        for (alias in aliases) {
                            expandedOverrides[alias] = category
                            expandedProviders[alias] = provider
                        }
                    }
                    overridesEditor.putString("category_overrides_json", gson.toJson(expandedOverrides))
                    overridesEditor.putString("provider_associations_json", gson.toJson(expandedProviders))
                } else if (backup.providerAssociations.isNotEmpty()) {
                    overridesEditor.putString("provider_associations_json", gson.toJson(backup.providerAssociations))
                }
                overridesEditor.apply()
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring backup JSON", e)
            false
        }
    }

    fun exportToStream(context: Context, outputStream: OutputStream): Boolean {
        return try {
            val json = createBackupJson(context)
            outputStream.write(json.toByteArray(Charsets.UTF_8))
            outputStream.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write backup to stream", e)
            false
        }
    }

    fun importFromStream(context: Context, inputStream: InputStream): Boolean {
        return try {
            val json = inputStream.bufferedReader().use { it.readText() }
            restoreBackupJson(context, json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read backup from stream", e)
            false
        }
    }
}
