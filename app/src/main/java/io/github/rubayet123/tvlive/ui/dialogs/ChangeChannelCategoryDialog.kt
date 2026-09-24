package io.github.rubayet123.tvlive.ui.dialogs

import android.app.AlertDialog
import android.content.Context
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rubayet123.tvlive.model.Channel
import io.github.rubayet123.tvlive.util.CategoryOrderManager
import io.github.rubayet123.tvlive.util.CategoryOverrideManager
import io.github.rubayet123.tvlive.util.ThemeManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeChannelCategorySheet(
    channel: Channel,
    onDismiss: () -> Unit,
    onCategoryChanged: () -> Unit
) {
    val context = LocalContext.current
    val currentTheme = remember { ThemeManager.getSelectedTheme(context) }
    val currentCat = remember { channel.group ?: "Uncategorized" }
    val isOverridden = remember { CategoryOverrideManager.isChannelOverridden(context, channel) }

    val discoveredCategories = remember {
        val list = CategoryOrderManager.getDiscoveredCategories(context).toMutableList()
        // Gather from active channels
        val masterChannels = io.github.rubayet123.tvlive.data.LiveTvManager.getMasterPlaylist()
        masterChannels.forEach { ch ->
            val norm = CategoryOrderManager.normalizeCategoryName(ch.group)
            if (norm.isNotBlank() && !list.contains(norm)) {
                list.add(norm)
            }
        }
        // Gather from overrides
        val customOverrides = CategoryOverrideManager.getAllOverrideCategories(context)
        customOverrides.forEach { raw ->
            val norm = CategoryOrderManager.normalizeCategoryName(raw)
            if (norm.isNotBlank() && !list.contains(norm)) {
                list.add(norm)
            }
        }
        val sorted = CategoryOrderManager.sortCategories(list, context).toMutableList()
        sorted.remove("All")
        sorted.remove("Favorites")
        sorted
    }

    var searchQuery by remember { mutableStateOf("") }
    var isCreatingNew by remember { mutableStateOf(false) }
    var newCatInput by remember { mutableStateOf("") }

    val filteredCategories = remember(searchQuery, discoveredCategories) {
        if (searchQuery.isBlank()) discoveredCategories
        else discoveredCategories.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = currentTheme.topBar,
        contentColor = currentTheme.primaryText,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Move Channel Category",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentTheme.primaryText
                    )
                    Text(
                        text = channel.name,
                        fontSize = 14.sp,
                        color = currentTheme.secondaryText
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = currentTheme.secondaryText
                    )
                }
            }

            // Current Category Badge
            Surface(
                color = currentTheme.cardBg,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current Category: ",
                        fontSize = 13.sp,
                        color = currentTheme.secondaryText
                    )
                    Text(
                        text = currentCat,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = currentTheme.accent
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search category...", fontSize = 14.sp, color = currentTheme.secondaryText.copy(alpha = 0.6f)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = currentTheme.secondaryText
                    )
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = currentTheme.secondaryText)
                        }
                    }
                } else null,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = currentTheme.accent,
                    unfocusedBorderColor = currentTheme.secondaryText.copy(alpha = 0.3f),
                    focusedTextColor = currentTheme.primaryText,
                    unfocusedTextColor = currentTheme.primaryText
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Create New Category Section
            if (!isCreatingNew) {
                Surface(
                    onClick = { isCreatingNew = true },
                    color = currentTheme.cardBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = currentTheme.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Create New Category...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = currentTheme.accent
                        )
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = currentTheme.cardBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "New Category Name",
                            fontSize = 13.sp,
                            color = currentTheme.primaryText,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newCatInput,
                                onValueChange = { newCatInput = it },
                                placeholder = { Text("e.g. Football HD", fontSize = 13.sp, color = currentTheme.secondaryText) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = currentTheme.accent,
                                    unfocusedBorderColor = currentTheme.secondaryText.copy(alpha = 0.3f),
                                    focusedTextColor = currentTheme.primaryText,
                                    unfocusedTextColor = currentTheme.primaryText
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val name = newCatInput.trim()
                                    if (name.isNotBlank()) {
                                        CategoryOverrideManager.saveOverride(context, channel, name)
                                        val existing = CategoryOrderManager.getDiscoveredCategories(context).toMutableList()
                                        if (!existing.contains(name)) {
                                            existing.add(name)
                                            CategoryOrderManager.saveDiscoveredCategories(context, existing)
                                        }
                                        Toast.makeText(context, "Moved '${channel.name}' to '$name'", Toast.LENGTH_SHORT).show()
                                        onCategoryChanged()
                                        onDismiss()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = currentTheme.accent)
                            ) {
                                Text("Save", color = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Reset to Default option if overridden
            if (isOverridden) {
                Surface(
                    onClick = {
                        CategoryOverrideManager.removeOverride(context, channel)
                        Toast.makeText(context, "Reset to default source category", Toast.LENGTH_SHORT).show()
                        onCategoryChanged()
                        onDismiss()
                    },
                    color = currentTheme.cardBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Reset to Default Source Category",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFFF6B6B)
                        )
                    }
                }
            }

            Text(
                text = "SELECT CATEGORY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = currentTheme.secondaryText,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            // Category List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredCategories) { cat ->
                    val isSelected = cat.equals(currentCat, ignoreCase = true)
                    Surface(
                        onClick = {
                            CategoryOverrideManager.saveOverride(context, channel, cat)
                            Toast.makeText(context, "Moved '${channel.name}' to '$cat'", Toast.LENGTH_SHORT).show()
                            onCategoryChanged()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) currentTheme.accent.copy(alpha = 0.2f) else currentTheme.cardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = currentTheme.accent,
                                        unselectedColor = currentTheme.secondaryText
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = cat,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) currentTheme.accent else currentTheme.primaryText
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = currentTheme.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

object ChangeChannelCategoryDialog {

    fun show(
        context: Context,
        channel: Channel,
        onCategoryChanged: () -> Unit
    ) {
        val discoveredCategories = CategoryOrderManager.getDiscoveredCategories(context).toMutableList()
        // Gather from active channels
        val masterChannels = io.github.rubayet123.tvlive.data.LiveTvManager.getMasterPlaylist()
        masterChannels.forEach { ch ->
            val norm = CategoryOrderManager.normalizeCategoryName(ch.group)
            if (norm.isNotBlank() && !discoveredCategories.contains(norm)) {
                discoveredCategories.add(norm)
            }
        }
        // Gather from overrides
        val customOverrides = CategoryOverrideManager.getAllOverrideCategories(context)
        customOverrides.forEach { raw ->
            val norm = CategoryOrderManager.normalizeCategoryName(raw)
            if (norm.isNotBlank() && !discoveredCategories.contains(norm)) {
                discoveredCategories.add(norm)
            }
        }

        val sortedCategories = CategoryOrderManager.sortCategories(discoveredCategories, context).toMutableList()
        sortedCategories.remove("All")
        sortedCategories.remove("Favorites")

        val optionsList = sortedCategories.toMutableList()
        val customOption = "+ Create New Category..."
        optionsList.add(customOption)

        val isOverridden = CategoryOverrideManager.isChannelOverridden(context, channel)
        if (isOverridden) {
            optionsList.add("Reset to Default Source Category")
        }

        val builder = AlertDialog.Builder(context)
        builder.setTitle("Change Category for '${channel.name}'")

        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, optionsList)

        builder.setAdapter(adapter) { dialog, which ->
            val selectedOption = optionsList[which]
            dialog.dismiss()
            when (selectedOption) {
                customOption -> {
                    showCustomCategoryInput(context, channel, onCategoryChanged)
                }
                "Reset to Default Source Category" -> {
                    CategoryOverrideManager.removeOverride(context, channel)
                    Toast.makeText(context, "Reset category to default source category", Toast.LENGTH_SHORT).show()
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onCategoryChanged()
                    }
                }
                else -> {
                    CategoryOverrideManager.saveOverride(context, channel, selectedOption)
                    Toast.makeText(context, "Moved '${channel.name}' to '$selectedOption'", Toast.LENGTH_SHORT).show()
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onCategoryChanged()
                    }
                }
            }
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        builder.create().show()
    }

    private fun showCustomCategoryInput(
        context: Context,
        channel: Channel,
        onCategoryChanged: () -> Unit
    ) {
        val inputBuilder = AlertDialog.Builder(context)
        inputBuilder.setTitle("Enter New Category Name")

        val input = EditText(context)
        input.hint = "e.g. Bangla, Sports HD, etc."
        input.setSingleLine()

        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val margin = (16 * context.resources.displayMetrics.density).toInt()
        params.setMargins(margin, margin / 2, margin, margin / 2)
        container.addView(input, params)

        inputBuilder.setView(container)

        inputBuilder.setPositiveButton("Save") { dialog, _ ->
            val newCategory = input.text.toString().trim()
            dialog.dismiss()
            if (newCategory.isNotBlank()) {
                CategoryOverrideManager.saveOverride(context, channel, newCategory)

                val existing = CategoryOrderManager.getDiscoveredCategories(context).toMutableList()
                if (!existing.contains(newCategory)) {
                    existing.add(newCategory)
                    CategoryOrderManager.saveDiscoveredCategories(context, existing)
                }

                Toast.makeText(context, "Moved '${channel.name}' to '$newCategory'", Toast.LENGTH_SHORT).show()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onCategoryChanged()
                }
            } else {
                Toast.makeText(context, "Category name cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        inputBuilder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        inputBuilder.show()
    }
}
