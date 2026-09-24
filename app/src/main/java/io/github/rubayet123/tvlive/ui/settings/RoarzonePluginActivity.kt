package io.github.rubayet123.tvlive.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import io.github.rubayet123.tvlive.R
import io.github.rubayet123.tvlive.data.SourceRepository
import io.github.rubayet123.tvlive.model.Source
import io.github.rubayet123.tvlive.data.network.NetworkClient
import io.github.rubayet123.tvlive.scraper.M3uBuilder
import io.github.rubayet123.tvlive.scraper.RoarzoneRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream

class RoarzonePluginActivity : FragmentActivity() {

    private lateinit var btnRefresh: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    
    private val roarzoneRepository by lazy {
        RoarzoneRepository(NetworkClient.client)
    }
    
    private val sourceRepository by lazy {
        SourceRepository(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        io.github.rubayet123.tvlive.util.DeviceUtils.setupOrientationForDevice(this)
        setContentView(R.layout.activity_roarzone_plugin)
        
        btnRefresh = findViewById(R.id.btn_refresh_roarzone)
        progressBar = findViewById(R.id.pb_roarzone)
        tvStatus = findViewById(R.id.tv_roarzone_status)
        
        btnRefresh.setOnClickListener {
            refreshChannels()
        }
        
        btnRefresh.requestFocus()
    }
    
    private fun refreshChannels() {
        btnRefresh.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Fetching Roarzone channels..."
        
        lifecycleScope.launch {
            try {
                val channels = roarzoneRepository.fetchChannels()
                
                if (channels.isEmpty()) {
                    throw Exception("No channels found from Roarzone.")
                }
                
                tvStatus.text = "Found ${channels.size} channels. Saving..."
                val m3uContent = M3uBuilder.build(channels)
                val filePath = saveM3uLocally(m3uContent)
                
                registerSource(filePath)
                
                tvStatus.text = "Success! Roarzone updated."
                Toast.makeText(this@RoarzonePluginActivity, "Roarzone updated!", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                tvStatus.text = "Error: ${e.message}"
                Toast.makeText(this@RoarzonePluginActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            } finally {
                btnRefresh.isEnabled = true
                progressBar.visibility = View.GONE
            }
        }
    }
    
    private suspend fun saveM3uLocally(content: String): String = withContext(Dispatchers.IO) {
        val file = File(filesDir, "roarzone.m3u")
        FileOutputStream(file).use {
            it.write(content.toByteArray(Charsets.UTF_8))
        }
        file.absolutePath
    }
    
    private fun registerSource(path: String) {
        val sourceName = "Roarzone ISP TV"
        val sources = sourceRepository.getSources()
        val existing = sources.find { it.name == sourceName }
        
        if (existing == null) {
            sourceRepository.addSource(Source(sourceName, path, true))
        } else {
            sourceRepository.updateSource(Source(sourceName, path, true))
        }
    }
}
