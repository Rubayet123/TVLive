package io.github.rubayet123.tvlive.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rubayet123.tvlive.util.AppTheme
import io.github.rubayet123.tvlive.util.DeviceUtils
import io.github.rubayet123.tvlive.util.ThemeManager

class ThemeSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceUtils.setupOrientationForDevice(this)

        setContent {
            ThemeSettingsScreen(onBack = { finish() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var currentTheme by remember { mutableStateOf(ThemeManager.getSelectedTheme(context)) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = currentTheme.primaryBg
    ) {
        Scaffold(
            containerColor = currentTheme.primaryBg,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    currentTheme.topBar,
                                    currentTheme.primaryBg
                                )
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(currentTheme.cardBg)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = currentTheme.primaryText
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Theme Settings",
                                color = currentTheme.primaryText,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Personalize your layout with high-contrast, eye-pleasing styles",
                                color = currentTheme.secondaryText,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(AppTheme.values()) { theme ->
                        ThemeCard(
                            theme = theme,
                            isSelected = theme == currentTheme,
                            onSelect = {
                                ThemeManager.setSelectedTheme(context, theme)
                                currentTheme = theme
                                Toast.makeText(context, "Theme set to ${theme.displayName}", Toast.LENGTH_SHORT).show()
                            },
                            currentTheme = currentTheme
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeCard(
    theme: AppTheme,
    isSelected: Boolean,
    onSelect: () -> Unit,
    currentTheme: AppTheme
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused || isSelected) 2.dp else 1.dp,
                color = if (isFocused) theme.accent else if (isSelected) currentTheme.accent else currentTheme.cardBg.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = currentTheme.cardBg
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = theme.displayName,
                    color = currentTheme.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(theme.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, currentTheme.secondaryText.copy(alpha = 0.5f), CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = theme.description,
                color = currentTheme.secondaryText,
                fontSize = 11.sp,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Beautiful Theme Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.primaryBg)
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Simulated TopBar or Card inside the theme
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(theme.cardBg)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(theme.primaryText.copy(alpha = 0.7f))
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(theme.accent)
                        )
                    }

                    // Simulated Content
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(theme.cardBg)
                                .padding(6.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(40.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(theme.primaryText)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(70.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(theme.secondaryText)
                                )
                            }
                        }

                        // Accent Colored FAB representation
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(theme.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }
        }
    }
}
