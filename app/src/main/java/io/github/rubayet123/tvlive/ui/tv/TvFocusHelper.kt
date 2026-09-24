package io.github.rubayet123.tvlive.ui.tv

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Universal Android TV Focus Modifier that gives cards, rows, buttons, and switches
 * high-contrast visibility on remote DPAD navigation with smooth scaling and prominent borders.
 */
fun Modifier.tvFocusable(
    interactionSource: MutableInteractionSource? = null,
    focusedBorderColor: Color = Color.White,
    unfocusedBorderColor: Color = Color.Transparent,
    focusedBorderWidth: Dp = 3.dp,
    unfocusedBorderWidth: Dp = 1.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    scaleOnFocus: Float = 1.025f,
    elevationOnFocus: Dp = 8.dp,
    onFocusChange: ((Boolean) -> Unit)? = null
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val isFocused by source.collectIsFocusedAsState()

    val currentScale by animateFloatAsState(
        targetValue = if (isFocused) scaleOnFocus else 1f,
        animationSpec = tween(120),
        label = "tv_scale"
    )

    val currentBorderColor by animateColorAsState(
        targetValue = if (isFocused) focusedBorderColor else unfocusedBorderColor,
        animationSpec = tween(120),
        label = "tv_border_color"
    )

    val currentBorderWidth = if (isFocused) focusedBorderWidth else unfocusedBorderWidth

    this
        .scale(currentScale)
        .shadow(
            elevation = if (isFocused) elevationOnFocus else 0.dp,
            shape = shape,
            clip = false
        )
        .border(
            BorderStroke(currentBorderWidth, currentBorderColor),
            shape = shape
        )
        .onFocusChanged { focusState ->
            onFocusChange?.invoke(focusState.isFocused)
        }
}

/**
 * High-visibility D-pad remote focusable button for TV interfaces.
 */
@Composable
fun TvIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    defaultTintColor: Color = Color.White,
    defaultBgColor: Color = Color(0xFF1E2330),
    focusedBgColor: Color = Color.White,
    focusedIconColor: Color = Color(0xFF0F172A),
    size: Dp = 38.dp,
    shape: Shape = RoundedCornerShape(10.dp)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused && enabled) 1.15f else 1f,
        animationSpec = tween(120),
        label = "tv_btn_scale"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            !enabled -> defaultBgColor.copy(alpha = 0.4f)
            isFocused -> focusedBgColor
            else -> defaultBgColor
        },
        animationSpec = tween(120),
        label = "tv_btn_bg"
    )

    val iconTint by animateColorAsState(
        targetValue = when {
            !enabled -> Color(0xFF4B5563)
            isFocused -> focusedIconColor
            else -> defaultTintColor
        },
        animationSpec = tween(120),
        label = "tv_btn_tint"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isFocused && enabled) Color.White else Color(0x22FFFFFF),
        animationSpec = tween(120),
        label = "tv_btn_border"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(shape)
            .background(bgColor)
            .border(
                BorderStroke(if (isFocused && enabled) 2.5.dp else 1.dp, borderColor),
                shape = shape
            )
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}
