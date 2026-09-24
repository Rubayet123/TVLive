package io.github.rubayet123.tvlive.ui.reorder

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sign

// ─────────────────────────────────────────────────────────────────────────────
// Reorderable LazyGrid State & Modifier (Industry Standard Pattern)
// ─────────────────────────────────────────────────────────────────────────────

class ReorderableLazyGridState(
    val gridState: LazyGridState,
    val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    val onDragEnd: () -> Unit,
    val haptic: HapticFeedback?,
    val scope: CoroutineScope
) {
    var draggedKey by mutableStateOf<Any?>(null)
        private set
    var draggedIndex by mutableStateOf<Int?>(null)
        private set
    var dragOffset by mutableStateOf(Offset.Zero)
        private set

    private var autoScrollJob: Job? = null
    private var lastRawCenter = Offset.Zero

    fun onDragStart(key: Any, index: Int) {
        val itemInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
            ?: gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (itemInfo != null) {
            draggedKey = itemInfo.key
            draggedIndex = itemInfo.index
            dragOffset = Offset.Zero
            lastRawCenter = Offset(
                itemInfo.offset.x + (itemInfo.size.width / 2f),
                itemInfo.offset.y + (itemInfo.size.height / 2f)
            )
            try {
                haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (_: Exception) {}
        }
    }

    fun onDrag(dragAmount: Offset) {
        if (draggedKey == null) return
        dragOffset += dragAmount
        checkItemIntersectionAndScroll()
    }

    private fun checkItemIntersectionAndScroll() {
        val currentKey = draggedKey ?: return
        val currentItemInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == currentKey }
            ?: return

        val draggedCenterX = currentItemInfo.offset.x + (currentItemInfo.size.width / 2f) + dragOffset.x
        val draggedCenterY = currentItemInfo.offset.y + (currentItemInfo.size.height / 2f) + dragOffset.y
        lastRawCenter = Offset(draggedCenterX, draggedCenterY)

        val targetItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            draggedCenterX in (item.offset.x.toFloat()..(item.offset.x + item.size.width).toFloat()) &&
            draggedCenterY in (item.offset.y.toFloat()..(item.offset.y + item.size.height).toFloat())
        }

        if (targetItem != null && targetItem.key != currentKey) {
            val fromIndex = currentItemInfo.index
            val toIndex = targetItem.index
            onMove(fromIndex, toIndex)
            draggedIndex = toIndex
            val deltaX = (targetItem.offset.x - currentItemInfo.offset.x).toFloat()
            val deltaY = (targetItem.offset.y - currentItemInfo.offset.y).toFloat()
            dragOffset = Offset(dragOffset.x - deltaX, dragOffset.y - deltaY)
            try {
                haptic?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } catch (_: Exception) {}
        }

        // Edge Auto-scrolling (supports continuous smooth acceleration past edges)
        val viewportHeight = gridState.layoutInfo.viewportSize.height.toFloat()
        val edgeThreshold = 140f
        val distanceFromTop = draggedCenterY
        val distanceFromBottom = viewportHeight - draggedCenterY

        val scrollSpeed = when {
            distanceFromTop < edgeThreshold -> {
                // Above or near top edge
                val ratio = ((edgeThreshold - distanceFromTop) / edgeThreshold).coerceIn(0f, 2.5f)
                -(ratio.pow(1.5f) * 35f).coerceAtLeast(8f)
            }
            distanceFromBottom < edgeThreshold -> {
                // Below or near bottom edge
                val ratio = ((edgeThreshold - distanceFromBottom) / edgeThreshold).coerceIn(0f, 2.5f)
                (ratio.pow(1.5f) * 35f).coerceAtLeast(8f)
            }
            else -> 0f
        }

        if (scrollSpeed != 0f) {
            if (autoScrollJob?.isActive != true) {
                autoScrollJob = scope.launch {
                    while (draggedKey != null) {
                        gridState.scrollBy(scrollSpeed)
                        // Re-evaluate item intersection while scrolling
                        checkItemIntersectionAndScroll()
                        delay(16)
                    }
                }
            }
        } else {
            autoScrollJob?.cancel()
            autoScrollJob = null
        }
    }

    fun onDragFinished() {
        autoScrollJob?.cancel()
        autoScrollJob = null
        if (draggedKey != null) {
            draggedKey = null
            draggedIndex = null
            dragOffset = Offset.Zero
            onDragEnd()
        }
    }
}

@Composable
fun rememberReorderableLazyGridState(
    gridState: LazyGridState,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragEnd: () -> Unit
): ReorderableLazyGridState {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    return remember(gridState, onMove, onDragEnd) {
        ReorderableLazyGridState(
            gridState = gridState,
            onMove = onMove,
            onDragEnd = onDragEnd,
            haptic = haptic,
            scope = scope
        )
    }
}

fun Modifier.reorderableGridItem(
    state: ReorderableLazyGridState,
    key: Any,
    index: Int
): Modifier = this
    .pointerInput(key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { _ ->
                state.onDragStart(key, index)
            },
            onDrag = { change, dragAmount ->
                change.consume()
                state.onDrag(dragAmount)
            },
            onDragEnd = {
                state.onDragFinished()
            },
            onDragCancel = {
                state.onDragFinished()
            }
        )
    }
    .then(
        if (state.draggedKey == key) {
            Modifier
                .zIndex(99f)
                .graphicsLayer {
                    translationX = state.dragOffset.x
                    translationY = state.dragOffset.y
                    scaleX = 1.08f
                    scaleY = 1.08f
                    shadowElevation = 24.dp.toPx()
                    alpha = 0.96f
                }
        } else {
            Modifier.zIndex(1f)
        }
    )

// ─────────────────────────────────────────────────────────────────────────────
// Reorderable LazyList State & Modifier (Industry Standard Pattern)
// ─────────────────────────────────────────────────────────────────────────────

class ReorderableLazyListState(
    val listState: LazyListState,
    val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    val onDragEnd: () -> Unit,
    val haptic: HapticFeedback?,
    val scope: CoroutineScope
) {
    var draggedKey by mutableStateOf<Any?>(null)
        private set
    var draggedIndex by mutableStateOf<Int?>(null)
        private set
    var dragOffset by mutableStateOf(Offset.Zero)
        private set

    private var autoScrollJob: Job? = null

    fun onDragStart(key: Any, index: Int) {
        val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
            ?: listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (itemInfo != null) {
            draggedKey = itemInfo.key
            draggedIndex = itemInfo.index
            dragOffset = Offset.Zero
            try {
                haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (_: Exception) {}
        }
    }

    fun onDrag(dragAmount: Offset) {
        if (draggedKey == null) return
        dragOffset += dragAmount
        checkItemIntersectionAndScroll()
    }

    private fun checkItemIntersectionAndScroll() {
        val currentKey = draggedKey ?: return
        val currentItemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == currentKey }
            ?: return

        val draggedCenterY = currentItemInfo.offset + (currentItemInfo.size / 2f) + dragOffset.y

        val targetItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            draggedCenterY in (item.offset.toFloat()..(item.offset + item.size).toFloat())
        }

        if (targetItem != null && targetItem.key != currentKey) {
            val fromIndex = currentItemInfo.index
            val toIndex = targetItem.index
            onMove(fromIndex, toIndex)
            draggedIndex = toIndex
            val deltaY = (targetItem.offset - currentItemInfo.offset).toFloat()
            dragOffset = Offset(0f, dragOffset.y - deltaY)
            try {
                haptic?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } catch (_: Exception) {}
        }

        // Edge Auto-scrolling
        val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
        val edgeThreshold = 140f
        val distanceFromTop = draggedCenterY
        val distanceFromBottom = viewportHeight - draggedCenterY

        val scrollSpeed = when {
            distanceFromTop < edgeThreshold -> {
                val ratio = ((edgeThreshold - distanceFromTop) / edgeThreshold).coerceIn(0f, 2.5f)
                -(ratio.pow(1.5f) * 35f).coerceAtLeast(8f)
            }
            distanceFromBottom < edgeThreshold -> {
                val ratio = ((edgeThreshold - distanceFromBottom) / edgeThreshold).coerceIn(0f, 2.5f)
                (ratio.pow(1.5f) * 35f).coerceAtLeast(8f)
            }
            else -> 0f
        }

        if (scrollSpeed != 0f) {
            if (autoScrollJob?.isActive != true) {
                autoScrollJob = scope.launch {
                    while (draggedKey != null) {
                        listState.scrollBy(scrollSpeed)
                        checkItemIntersectionAndScroll()
                        delay(16)
                    }
                }
            }
        } else {
            autoScrollJob?.cancel()
            autoScrollJob = null
        }
    }

    fun onDragFinished() {
        autoScrollJob?.cancel()
        autoScrollJob = null
        if (draggedKey != null) {
            draggedKey = null
            draggedIndex = null
            dragOffset = Offset.Zero
            onDragEnd()
        }
    }
}

@Composable
fun rememberReorderableLazyListState(
    listState: LazyListState,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragEnd: () -> Unit
): ReorderableLazyListState {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    return remember(listState, onMove, onDragEnd) {
        ReorderableLazyListState(
            listState = listState,
            onMove = onMove,
            onDragEnd = onDragEnd,
            haptic = haptic,
            scope = scope
        )
    }
}

fun Modifier.reorderableListItem(
    state: ReorderableLazyListState,
    key: Any,
    index: Int
): Modifier = this
    .pointerInput(key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { _ ->
                state.onDragStart(key, index)
            },
            onDrag = { change, dragAmount ->
                change.consume()
                state.onDrag(dragAmount)
            },
            onDragEnd = {
                state.onDragFinished()
            },
            onDragCancel = {
                state.onDragFinished()
            }
        )
    }
    .then(
        if (state.draggedKey == key) {
            Modifier
                .zIndex(99f)
                .graphicsLayer {
                    translationY = state.dragOffset.y
                    scaleX = 1.04f
                    scaleY = 1.04f
                    shadowElevation = 24.dp.toPx()
                    alpha = 0.96f
                }
        } else {
            Modifier.zIndex(1f)
        }
    )

