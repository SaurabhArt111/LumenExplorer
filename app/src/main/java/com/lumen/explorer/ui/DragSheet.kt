package com.lumen.explorer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private val EnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)     // Material 3 "emphasized decelerate"
private val ExitEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)      // Material 3 "emphasized accelerate"

/**
 * Material-3 style modal sheet (bottom, or top for quick settings).
 *  - 28 dp corners, 32×4 drag handle, 32 % scrim that fades with the sheet, flush with the screen edge, max 640 dp wide
 *  - opens at the content height (at most 60 % of the screen); drag toward the middle to expand — all the way to the top/bottom
 *    of the screen, even when there is little content; drag toward its own edge to shrink, then dismiss
 *  - scrollable content scrolls first, the sheet takes over at the ends (nested scrolling, like the real thing)
 */
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
    var natural by remember { mutableFloatStateOf(0f) }     // resting height in px
    var stretch by remember { mutableFloatStateOf(0f) }     // extra px beyond resting height
    var drag by remember { mutableFloatStateOf(0f) }        // px pulled toward the dismiss edge
    var appear by remember { mutableFloatStateOf(1f) }      // 1 = off-screen, 0 = shown
    var limit by remember { mutableFloatStateOf(0f) }       // tallest the sheet may get, px
    var closing by remember { mutableStateOf(false) }

    fun maxStretch(): Float = max(0f, limit - natural)
    fun norm(v: Float): Float = if (fromTop) -v else v       // + = toward the dismiss edge

    fun requestDismiss() {
        if (closing) return
        closing = true
        scope.launch {
            animate(appear, 1f, animationSpec = tween(200, easing = ExitEasing)) { v, _ -> appear = v }
            onDismiss()
        }
    }

    LaunchedEffect(Unit) { animate(1f, 0f, animationSpec = tween(300, easing = EnterEasing)) { v, _ -> appear = v } }
    BackHandler { requestDismiss() }

    /** moves the sheet by d (normalised, + toward dismiss); returns how much of d was used */
    fun applyDelta(d: Float): Float {
        if (closing) return d
        if (d > 0f) {
            val use = min(stretch, d)
            stretch -= use
            drag += d - use
            return d
        }
        val back = min(drag, -d)
        drag -= back
        val add = min(-d - back, max(0f, maxStretch() - stretch))
        stretch += add
        return -(back + add)
    }

    fun settle(v: Float) {
        scope.launch {
            val s = spring<Float>(dampingRatio = 0.86f, stiffness = 420f)
            if (drag > 0f) {
                if (drag > (natural + stretch) * 0.3f || v > 1100f) { requestDismiss(); return@launch }
                animate(drag, 0f, animationSpec = s) { x, _ -> drag = x }
            } else if (stretch > 0f || (v < -900f && maxStretch() > 0f)) {
                val target = when {
                    v < -600f -> maxStretch()
                    v > 600f -> 0f
                    stretch > maxStretch() * 0.4f -> maxStretch()
                    else -> 0f
                }
                animate(stretch, target, animationSpec = s) { x, _ -> stretch = x }
            } else if (v > 1500f) requestDismiss()
        }
    }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.Drag) return Offset.Zero
                val d = norm(available.y)
                if (d == 0f) return Offset.Zero
                // toward the middle of the screen: grow the sheet first; or already pulled away from the edge: keep pulling
                if (d < 0f || drag > 0f) return Offset(0f, norm(applyDelta(d)))
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.Drag) return Offset.Zero
                val d = norm(available.y)
                if (d <= 0f) return Offset.Zero
                return Offset(0f, norm(applyDelta(d)))        // content hit its end → the sheet takes over
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag > 0f || (stretch > 0f && stretch < maxStretch())) { settle(norm(available.y)); return available }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                settle(norm(available.y))
                return available
            }
        }
    }

    val headerDrag = rememberDraggableState { dy -> applyDelta(norm(dy)) }

    Box(Modifier.fillMaxSize()) {
        val shown = (1f - appear) * (1f - (drag / (natural + stretch + 1f)).coerceIn(0f, 1f))
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f * shown))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { requestDismiss() }
        )
        BoxWithConstraints(Modifier.fillMaxSize().let { if (fromTop) it.navigationBarsPadding() else it.statusBarsPadding().imePadding() }) {
            limit = with(density) { maxHeight.toPx() }
            val peek = maxHeight * 0.6f
            val shape = if (fromTop) RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            val header: @Composable () -> Unit = {
                Column(
                    Modifier.fillMaxWidth().draggable(headerDrag, Orientation.Vertical, onDragStopped = { vel -> settle(norm(vel)) })
                ) {
                    if (!fromTop) Grabber()
                    if (title != null && !fromTop) SheetTitle(title)
                }
            }
            Column(
                Modifier
                    .align(if (fromTop) Alignment.TopCenter else Alignment.BottomCenter)
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = (if (fromTop) -1f else 1f) * (drag + appear * max(natural + stretch, 1f))
                        alpha = if (natural <= 0f) 0f else 1f
                    }
                    .then(if (stretch > 0.5f) Modifier.height(with(density) { (natural + stretch).toDp() }) else Modifier.heightIn(max = peek))
                    .onSizeChanged { if (stretch <= 0.5f) natural = it.height.toFloat() }
                    .clip(shape)
                    .background(c.card)
                    .nestedScroll(connection)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { /* swallow taps */ }
            ) {
                if (fromTop) {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        Spacer(Modifier.statusBarsPadding().height(8.dp))
                        if (title != null) SheetTitle(title)
                        content()
                    }
                    Column(Modifier.fillMaxWidth().draggable(headerDrag, Orientation.Vertical, onDragStopped = { vel -> settle(norm(vel)) })) { Grabber() }
                } else {
                    header()
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        content()
                        Spacer(Modifier.navigationBarsPadding().height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Grabber() {
    Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Win.c.text2.copy(alpha = 0.4f)))
    }
}

@Composable
private fun SheetTitle(title: String) {
    Txt(title, 17.sp, FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 8.dp), maxLines = 2)
}
