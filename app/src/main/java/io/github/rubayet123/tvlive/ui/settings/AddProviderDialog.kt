package io.github.rubayet123.tvlive.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.activity.result.contract.ActivityResultContracts
import io.github.rubayet123.tvlive.R
import io.github.rubayet123.tvlive.data.SourceRepository
import io.github.rubayet123.tvlive.model.Source

class AddProviderDialog : DialogFragment() {

    private lateinit var editUrl: EditText
    
    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            copyFileToInternalStorage(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_add_provider, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val editName = view.findViewById<EditText>(R.id.edit_source_name)
        editUrl = view.findViewById<EditText>(R.id.edit_source_url)
        val btnBrowse = view.findViewById<Button>(R.id.btn_browse)
        val btnSave = view.findViewById<Button>(R.id.btn_save)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)

        btnBrowse.setOnClickListener {
            filePicker.launch("*/*")
        }

        btnCancel.setOnClickListener {
            dismiss()
        }

        btnSave.setOnClickListener {
            val name = editName.text.toString().trim()
            val url = editUrl.text.toString().trim()
            val type = "M3U"

            if (name.isNotEmpty() && url.isNotEmpty()) {
                val repository = SourceRepository(requireContext())
                repository.addSource(Source(
                    name = name, 
                    url = url, 
                    isActive = true, 
                    type = type,
                    refreshIntervalHours = 0, // Default to OFF
                    isUserAdded = true
                ))
                
                Toast.makeText(requireContext(), "Provider added successfully", Toast.LENGTH_SHORT).show()
                dismiss()
            } else {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyFileToInternalStorage(uri: android.net.Uri) {
        try {
            val contentResolver = requireContext().contentResolver
            val fileName = getFileName(uri) ?: "imported_playlist.m3u"
            val destinationFile = java.io.File(requireContext().filesDir, fileName)
            
            contentResolver.openInputStream(uri)?.use { input ->
                java.io.FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            editUrl.setText(destinationFile.absolutePath)
            Toast.makeText(requireContext(), "File imported locally", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Failed to import file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getFileName(uri: android.net.Uri): String? {
        var name: String? = null
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
