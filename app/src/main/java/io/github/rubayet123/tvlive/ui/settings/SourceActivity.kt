package io.github.rubayet123.tvlive.ui.settings

import androidx.fragment.app.FragmentActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ItemBridgeAdapter
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.VerticalGridView
import io.github.rubayet123.tvlive.R
import io.github.rubayet123.tvlive.data.SourceRepository
import io.github.rubayet123.tvlive.model.Source
import android.view.ViewGroup
import android.widget.TextView
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SourceActivity : FragmentActivity() {

    private lateinit var sourcesList: VerticalGridView
    
    private val repository by lazy { SourceRepository(this) }
    private val sourcesAdapter by lazy { ArrayObjectAdapter(SourcePresenter { refreshList() }) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        io.github.rubayet123.tvlive.util.DeviceUtils.setupOrientationForDevice(this)
        setContentView(R.layout.activity_source)
        
        sourcesList = findViewById(R.id.sources_list)
        sourcesList.adapter = ItemBridgeAdapter(sourcesAdapter)
        
        refreshList()
    }
    
    private fun refreshList() {
        sourcesAdapter.clear()
        val sources = repository.getSources()
        sources.forEach { sourcesAdapter.add(it) }
    }
    
    class SourcePresenter(private val onRefresh: () -> Unit) : Presenter() {
         override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
             val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_source_card, parent, false)
             return ViewHolder(view)
         }
         
         override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
             val source = item as? Source ?: return
             val view = viewHolder.view
             
             val nameText = view.findViewById<TextView>(R.id.text_source_name)
             val urlText = view.findViewById<TextView>(R.id.text_source_url)
             val badge = view.findViewById<TextView>(R.id.text_status_badge)
             
             nameText.text = source.name
             urlText.text = source.url
             
             if (source.isActive) {
                 badge.text = "ACTIVE"
                 badge.setBackgroundResource(R.drawable.bg_status_active)
             } else {
                 badge.text = "INACTIVE"
                 badge.setBackgroundResource(R.drawable.bg_status_inactive)
             }
             
             view.setOnClickListener { 
                 val context = view.context
                 val repo = SourceRepository(context)
                 
                 val builder = android.app.AlertDialog.Builder(context)
                 builder.setTitle("Manage Source")
                 builder.setMessage("Options for ${source.name}")
                 builder.setPositiveButton(if (source.isActive) "Deactivate" else "Activate") { _, _ ->
                     val updated = source.copy(isActive = !source.isActive)
                     repo.updateSource(updated)
                     onRefresh()
                 }
                 builder.setNegativeButton("Delete") { _, _ ->
                     repo.removeSource(source)
                     onRefresh()
                 }
                builder.setNeutralButton("Options") { _, _ ->
                    val optionsBuilder = android.app.AlertDialog.Builder(context)
                    optionsBuilder.setTitle("Source Options")
                    val options = if (source.url.startsWith("http")) {
                        arrayOf("Sync Now", "Refresh Interval", "Move Up", "Move Down", "Cancel")
                    } else {
                        arrayOf("Move Up", "Move Down", "Cancel")
                    }
                    
                    optionsBuilder.setItems(options) { _, which ->
                        val selectedOption = options[which]
                        when (selectedOption) {
                            "Sync Now" -> {
                                Toast.makeText(context, "Syncing ${source.name}...", Toast.LENGTH_SHORT).show()
                                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                                    val success = repo.syncSource(source)
                                    if (success) {
                                        Toast.makeText(context, "${source.name} synced successfully", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to sync ${source.name}", Toast.LENGTH_SHORT).show()
                                    }
                                    onRefresh()
                                }
                            }
                            "Refresh Interval" -> {
                                val intervalOptions = arrayOf("Off (Manual)", "App Start", "1 Hour", "2 Hours", "6 Hours", "12 Hours", "24 Hours", "48 Hours", "7 Days")
                                val currentIdx = when(source.refreshIntervalHours) {
                                    0 -> 0
                                    -1 -> 1
                                    1 -> 2
                                    2 -> 3
                                    6 -> 4
                                    12 -> 5
                                    24 -> 6
                                    48 -> 7
                                    168 -> 8
                                    else -> 0
                                }
                                
                                val intervalBuilder = android.app.AlertDialog.Builder(context)
                                intervalBuilder.setTitle("Select Refresh Interval")
                                intervalBuilder.setSingleChoiceItems(intervalOptions, currentIdx) { dialog, i ->
                                    val hours = when(i) {
                                        0 -> 0
                                        1 -> -1
                                        2 -> 1
                                        3 -> 2
                                        4 -> 6
                                        5 -> 12
                                        6 -> 24
                                        7 -> 48
                                        8 -> 168
                                        else -> 0
                                    }
                                    val updated = source.copy(refreshIntervalHours = hours)
                                    repo.updateSource(updated)
                                    
                                    if (hours != 0) {
                                        repo.triggerAppStartRefreshes()
                                    }
                                    
                                    Toast.makeText(context, "Interval set to: ${intervalOptions[i]}", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                    onRefresh()
                                }
                                intervalBuilder.show()
                            }
                            "Move Up" -> {
                                repo.moveSourceUp(source)
                                onRefresh()
                                Toast.makeText(context, "Moved Up (Affects Home Screen order)", Toast.LENGTH_SHORT).show()
                            }
                            "Move Down" -> {
                                repo.moveSourceDown(source)
                                onRefresh()
                                Toast.makeText(context, "Moved Down (Affects Home Screen order)", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    optionsBuilder.show()
                }
                builder.show()
            }
        }
         
         override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
    }
}
