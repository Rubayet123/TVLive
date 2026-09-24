package io.github.rubayet123.tvlive.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.rubayet123.tvlive.model.Channel

object HiddenChannelsManager {
    private const val PREFS_NAME = "tv_live_prefs"
    private const val KEY_HIDDEN_CHANNELS = "hidden_channels_keys"
    private val gson = Gson()

    fun getHiddenKeys(context: Context): Set<String> {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_HIDDEN_CHANNELS, null) ?: return emptySet()
        val type = object : TypeToken<Set<String>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun isChannelHidden(context: Context, channel: Channel): Boolean {
        val hiddenKeys = getHiddenKeys(context)
        if (hiddenKeys.isEmpty()) return false

        val lookupKeys = CanonicalKeyHelper.getLookupKeys(channel)
        if (lookupKeys.any { hiddenKeys.contains(it) }) return true

        if (hiddenKeys.contains(channel.streamUrl)) return true

        return false
    }

    fun hideChannel(context: Context, channel: Channel) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getHiddenKeys(context).toMutableSet()
        val lookupKeys = CanonicalKeyHelper.getLookupKeys(channel)
        current.addAll(lookupKeys)
        current.add(channel.streamUrl)
        prefs.edit().putString(KEY_HIDDEN_CHANNELS, gson.toJson(current)).apply()
    }

    fun unhideChannel(context: Context, channel: Channel) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getHiddenKeys(context).toMutableSet()
        val lookupKeys = CanonicalKeyHelper.getLookupKeys(channel)
        lookupKeys.forEach { current.remove(it) }
        current.remove(channel.streamUrl)
        prefs.edit().putString(KEY_HIDDEN_CHANNELS, gson.toJson(current)).apply()
    }

    fun unhideAll(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HIDDEN_CHANNELS).apply()
    }

    fun getHiddenChannels(context: Context, channels: List<Channel>): List<Channel> {
        val hiddenKeys = getHiddenKeys(context)
        if (hiddenKeys.isEmpty()) return emptyList()
        return channels.filter { isChannelHidden(context, it) }
    }

    fun filterVisibleChannels(context: Context, channels: List<Channel>): List<Channel> {
        val hiddenKeys = getHiddenKeys(context)
        if (hiddenKeys.isEmpty()) return channels
        return channels.filter { !isChannelHidden(context, it) }
    }
}
