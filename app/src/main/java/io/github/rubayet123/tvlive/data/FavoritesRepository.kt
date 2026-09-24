package io.github.rubayet123.tvlive.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.rubayet123.tvlive.model.Channel

class FavoritesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tv_live_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val FAVORITES_KEY = "favorite_channels"

    fun getFavorites(): List<Channel> {
        val json = prefs.getString(FAVORITES_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<Channel>>() {}.type
        return gson.fromJson(json, type)
    }

    fun addFavorite(channel: Channel) {
        val current = getFavorites().toMutableList()
        val allUrls = (channel.effectiveSources.map { it.streamUrl } + channel.streamUrl).toSet()
        if (current.none { fav -> allUrls.contains(fav.streamUrl) || (fav.name.equals(channel.name, ignoreCase = true) && fav.group.equals(channel.group, ignoreCase = true)) }) {
            current.add(channel)
            saveFavorites(current)
        }
    }

    fun removeFavorite(channel: Channel) {
        val current = getFavorites().toMutableList()
        val allUrls = (channel.effectiveSources.map { it.streamUrl } + channel.streamUrl).toSet()
        current.removeAll { fav ->
            allUrls.contains(fav.streamUrl) || (fav.name.equals(channel.name, ignoreCase = true) && fav.group.equals(channel.group, ignoreCase = true))
        }
        saveFavorites(current)
    }

    fun isFavorite(channel: Channel): Boolean {
        val favorites = getFavorites()
        val allUrls = (channel.effectiveSources.map { it.streamUrl } + channel.streamUrl).toSet()
        return favorites.any { fav ->
            allUrls.contains(fav.streamUrl) || (fav.name.equals(channel.name, ignoreCase = true) && fav.group.equals(channel.group, ignoreCase = true))
        }
    }

    private fun saveFavorites(favorites: List<Channel>) {
        val json = gson.toJson(favorites)
        prefs.edit().putString(FAVORITES_KEY, json).apply()
    }
}
