package io.github.rubayet123.tvlive.data

import android.content.Context
import io.github.rubayet123.tvlive.model.Channel
import java.util.LinkedList

object LiveTvManager {
    private var currentChannelIndex: Int = -1
    private var currentPlaylist: List<Channel> = emptyList()
    private var masterPlaylist: List<Channel> = emptyList()
    private var masterChannelIndex: Int = -1
    private val recentChannels = LinkedList<Channel>()
    private const val MAX_RECENTS = 10

    fun updatePlaylist(playlist: List<Channel>, currentIndex: Int) {
        currentPlaylist = playlist
        currentChannelIndex = currentIndex
    }
    
    fun setMasterPlaylist(allChannels: List<Channel>, context: Context? = null) {
        masterPlaylist = allChannels
        if (context != null) {
            Thread {
                ChannelCacheManager.saveChannelsToDisk(context, allChannels)
            }.start()
        }
    }

    fun initializeFromDiskIfNeeded(context: Context): List<Channel> {
        if (masterPlaylist.isEmpty()) {
            val cached = ChannelCacheManager.loadChannelsFromDisk(context)
            if (cached.isNotEmpty()) {
                masterPlaylist = cached
            }
        }
        return masterPlaylist
    }
    
    fun setCurrentChannelInMaster(channel: Channel) {
        val idx = masterPlaylist.indexOfFirst {
            it.streamUrl == channel.streamUrl || it.name.equals(channel.name, ignoreCase = true)
        }
        if (idx != -1) {
            masterChannelIndex = idx
        }
    }

    fun getCurrentChannel(): Channel? {
        if (currentChannelIndex in currentPlaylist.indices) {
            return currentPlaylist[currentChannelIndex]
        }
        if (masterChannelIndex in masterPlaylist.indices) {
            return masterPlaylist[masterChannelIndex]
        }
        return null
    }

    fun getNextChannel(): Channel? {
        if (masterPlaylist.isEmpty()) return null
        if (masterChannelIndex !in masterPlaylist.indices) {
            val current = getCurrentChannel()
            masterChannelIndex = if (current != null) {
                masterPlaylist.indexOfFirst {
                    it.streamUrl == current.streamUrl || it.name.equals(current.name, ignoreCase = true)
                }.coerceAtLeast(0)
            } else 0
        }
        masterChannelIndex = (masterChannelIndex + 1) % masterPlaylist.size
        val next = masterPlaylist[masterChannelIndex]
        updatePlaylist(masterPlaylist, masterChannelIndex)
        return next
    }

    fun getPreviousChannel(): Channel? {
        if (masterPlaylist.isEmpty()) return null
        if (masterChannelIndex !in masterPlaylist.indices) {
            val current = getCurrentChannel()
            masterChannelIndex = if (current != null) {
                masterPlaylist.indexOfFirst {
                    it.streamUrl == current.streamUrl || it.name.equals(current.name, ignoreCase = true)
                }.coerceAtLeast(0)
            } else 0
        }
        masterChannelIndex = if (masterChannelIndex - 1 < 0) masterPlaylist.size - 1 else masterChannelIndex - 1
        val prev = masterPlaylist[masterChannelIndex]
        updatePlaylist(masterPlaylist, masterChannelIndex)
        return prev
    }

    fun addToRecent(channel: Channel) {
        recentChannels.removeAll { it.streamUrl == channel.streamUrl }
        recentChannels.addFirst(channel)
        if (recentChannels.size > MAX_RECENTS) {
            recentChannels.removeLast()
        }
    }

    fun getRecentChannels(): List<Channel> = recentChannels
    
    fun getCurrentPlaylist(): List<Channel> = currentPlaylist
    
    fun getMasterPlaylist(): List<Channel> = masterPlaylist
}
