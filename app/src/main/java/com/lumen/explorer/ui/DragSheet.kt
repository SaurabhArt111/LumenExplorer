package com.lumen.explorer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * A bottom (or top) sheet that can ALWAYS be dragged to the very top / bottom of the screen — even when its content is short.
 *  - drag toward the screen centre  → the sheet grows (content area gets the extra room) until it fills the screen
 *  - drag toward its own edge       → it shrinks back, and past its natural size it dismisses
 *  - fling gestures snap to full / natural size / dismissed
 * The whole sheet is draggable; scrollable content inside still scrolls first.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DragSheet(
    onDismiss: () -> Unit,
    fromTop: Boolean = false,
    title: String? = null,
    maxWidth: androidx.compose.ui.unit.Dp = 640.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val c = Win.c
    var natural by remember { mutableFloatStateOf(0f) }     // natural (wrap-content) height in px
    var stretch by remember { mutableFloatStateOf(0f) }     // extra px beyond natural
    var drag by remember { mutableFloatStateOf(0f) }        // px pulled toward the dismiss edge
    var appear by remember { mutableFloatStateOf(1f) }      // 1 = off-screen, 0 = shown
    var limit by remember { mutableFloatStateOf(0f) }       // max height available in px
    var closing by remember { mutableFloatStateOf(0f) }

    fun requestDismiss() {
        if (closing > 0f) return
        closing = 1f
        scope.launch {
            animate(appear, 1f, animationSpec = tween(190, easing = FastOutSlowInEasing)) { v, _ -> appear = v }
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        animate(1f, 0f, animationSpec = tween(260, easing = FastOutSlowInEasing)) { v, _ -> appear = v }
    }
    BackHandler { requestDismiss() }

    val maxStretch = max(0f, limit - natural)

    fun settle(velocity: Float) {
        // velocity > 0 means moving toward the dismiss edge
        val v = if (fromTop) -velocity else velocity
        scope.launch {
            if (drag > 0f) {
                if (drag > natural * 0.28f || v > 1300f) { requestDismiss(); return@launch }
                animate(drag, 0f, animationSpec = tween(180)) { x, _ -> drag = x }
            } else if (stretch > 0f || v < -700f) {
                val target = when {
                    v < -700f -> maxStretch
                    v > 700f -> 0f
                    stretch > maxStretch * 0.35f -> maxStretch
                    else -> 0f
                }
                animate(stretch, target, animationSpec = tween(220, easing = FastOutSlowInEasing)) { x, _ -> stretch = x }
            }
        }
    }

    val dragState = rememberDraggableState { rawDy ->
        val d = if (fromTop) -rawDy else rawDy         // normalise: + = toward dismiss edge
        if (d > 0f) {
            val use = min(stretch, d)
            stretch -= use
            drag += d - use
        } else {
            val back = min(drag, -d)
            drag -= back
            stretch = (stretch + (-d - back)).coerceIn(0f, maxStretch)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // scrim
        val shownFraction = (1f - appear) * (1f - (drag / (natural + stretch + 1f)).coerceIn(0f, 1f))
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.50f * shownFraction))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { requestDismiss() }
        )
        BoxWithConstraints(
            Modifier.fillMaxSize().let { if (fromTop) it.navigationBarsPadding() else it.statusBarsPadding().imePadding() }
        ) {
            limit = with(density) { (maxHeight - 8.dp).toPx() }
            val sheetShape = if (fromTop) RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp) else RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
            Column(
                Modifier
                    .align(if (fromTop) Alignment.TopCenter else Alignment.BottomCenter)
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val h = natural + stretch
                        translationY = (if (fromTop) -1f else 1f) * (drag + appear * max(h, 1f))
                        alpha = if (natural <= 0f) 0f else 1f
                    }
                    .then(if (stretch > 0.5f) Modifier.height(with(density) { (natural + stretch).toDp() }) else Modifier.wrapContentHeight())
                    .onSizeChanged { if (stretch <= 0.5f) natural = it.height.toFloat() }
                    .shadow(16.dp, sheetShape)
                    .clip(sheetShape)
                    .background(c.card)
                    .draggable(state = dragState, orientation = Orientation.Vertical, onDragStopped = { vel -> settle(vel) })
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { /* swallow taps */ }
            ) {
                if (fromTop) {
                    Column(Modifier.weight(1f, fill = false).statusBarsPadding().verticalScroll(rememberScrollState())) {
                        if (title != null) SheetTitle(title)
                        content()
                    }
                    Grabber()
                } else {
                    Grabber()
                    if (title != null) SheetTitle(title)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { content() }
                    Box(Modifier.navigationBarsPadding().height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun Grabber() {
    Box(Modifier.fillMaxWidth().height(26.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Win.c.text2.copy(alpha = 0.45f)))
    }
}

@Composable
private fun SheetTitle(title: String) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Txt(title, 17.sp, FontWeight.SemiBold)
    }
}
