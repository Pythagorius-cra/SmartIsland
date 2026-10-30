/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.ui.hyper

import android.graphics.Rect
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.agupta07505.smartisland.R
import com.agupta07505.smartisland.data.SmartIslandSettings
import com.agupta07505.smartisland.model.HyperActivities
import com.agupta07505.smartisland.model.HyperGesture
import com.agupta07505.smartisland.model.HyperGestureEffect
import com.agupta07505.smartisland.model.HyperIslandGeometry
import com.agupta07505.smartisland.model.HyperIslandGestures
import com.agupta07505.smartisland.model.HyperIslandMotion
import com.agupta07505.smartisland.model.HyperIslandPhase
import com.agupta07505.smartisland.model.HyperTemplates
import com.agupta07505.smartisland.model.IslandNotification
import com.agupta07505.smartisland.util.CameraCutoutDetector
import com.agupta07505.smartisland.util.HapticKind
import com.agupta07505.smartisland.util.HapticStrength
import com.agupta07505.smartisland.util.IslandHaptics
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * HyperOS-style overlay. The shell springs between the collapsed pill, the compact capsule, and the
 * card. Shown only while [SmartIslandSettings.hyperOsStyle] is on; the original island is untouched.
 */
@Composable
internal fun HyperIslandHost(
    settings: SmartIslandSettings,
    expanded: Boolean,
    notifications: List<IslandNotification>,
    selectedIndex: Int,
    onPageSelected: (Int) -> Unit,
    onOpenNotification: (IslandNotification) -> Unit,
    onToggleExpanded: () -> Unit,
    onDismissNotification: () -> Unit,
    onUserEngaged: () -> Unit,
    onReplyStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val description = stringResource(R.string.hyper_island_cd)
    val detected = remember(
        context,
        configuration.orientation,
        configuration.screenWidthDp,
        configuration.screenHeightDp
    ) { CameraCutoutDetector.rawHole(context) }
    val hole = if (settings.cameraCutoutManual) {
        HyperIslandGeometry.manualHole(
            settings.cameraCutoutX, settings.cameraCutoutY, settings.cameraCutoutWidth, settings.cameraCutoutHeight
        )
    } else {
        detected
    }
    var phase by remember { mutableStateOf(if (expanded) HyperIslandPhase.Card else HyperIslandPhase.Collapsed) }
    var expanding by remember { mutableStateOf(true) }
    var latchedPhase by remember { mutableStateOf(phase) }
    if (latchedPhase != phase) {
        expanding = HyperIslandMotion.expanding(latchedPhase, phase)
        latchedPhase = phase
    }
    androidx.compose.runtime.LaunchedEffect(expanded) {
        if (expanded) {
            phase = HyperIslandPhase.Card
        } else if (phase == HyperIslandPhase.Card) {
            phase = HyperIslandPhase.Collapsed
        }
    }

    val safeIndex = selectedIndex.coerceIn(0, (notifications.size - 1).coerceAtLeast(0))
    val current = notifications.getOrNull(safeIndex)
    val slots = HyperActivities.slots(notifications.size, safeIndex)
    val secondaryIndex = slots.secondary
    val secondary = secondaryIndex?.let { notifications.getOrNull(it) }
    val hasReply = current?.actionIntents?.any { it.isQuickReply || it.title.contains("reply", ignoreCase = true) } == true
    val template = HyperTemplates.of(current?.mode ?: com.agupta07505.smartisland.model.IslandMode.Empty, hasReply)
    val cardHeight = HyperTemplates.cardHeight(template)
    val screen = configuration.screenWidthDp.toFloat()
    val target = HyperIslandGeometry.frame(hole, screen, phase, cardHeight)
    val collapsed = HyperIslandGeometry.frame(hole, screen, HyperIslandPhase.Collapsed, cardHeight)
    val compact = HyperIslandGeometry.frame(hole, screen, HyperIslandPhase.Compact, cardHeight)

    val damping = HyperIslandMotion.damping(expanding)
    val width by animateDpAsState(
        targetValue = target.widthDp.dp,
        animationSpec = spring(damping, HyperIslandMotion.stiffness(expanding, HyperIslandMotion.WIDTH_STIFFNESS_SCALE)),
        label = "hyperWidth"
    )
    val height by animateDpAsState(
        targetValue = target.heightDp.dp,
        animationSpec = spring(damping, HyperIslandMotion.stiffness(expanding, HyperIslandMotion.HEIGHT_STIFFNESS_SCALE)),
        label = "hyperHeight"
    )
    val xOff by animateDpAsState(
        targetValue = target.xOffsetDp.dp,
        animationSpec = spring(damping, HyperIslandMotion.stiffness(expanding, 1f)),
        label = "hyperX"
    )
    val yOff by animateDpAsState(
        targetValue = target.yOffsetDp.dp,
        animationSpec = spring(damping, HyperIslandMotion.stiffness(expanding, 1f)),
        label = "hyperY"
    )
    val radius = HyperIslandMotion.cornerRadius(height.value, collapsed.heightDp).dp
    val holeWidth by animateDpAsState(target.holeWidthDp.dp, spring(damping, HyperIslandMotion.stiffness(expanding, 1f)), label = "hyperHole")
    val holeLeft by animateDpAsState(
        target.holeLeftInPillDp.dp,
        spring(damping, HyperIslandMotion.stiffness(expanding, 1f)),
        label = "hyperHoleLeft"
    )

    val onRight = settings.circlePosition != SmartIslandSettings.CIRCLE_POSITION_LEFT
    val bubbleSize = (collapsed.heightDp * 0.78f).dp
    val bubbleX = HyperIslandGeometry.bubbleCenterX(
        xOff.value, width.value, bubbleSize.value, 8f, onRight
    ).dp
    val showBubble = secondary != null && phase != HyperIslandPhase.Card
    val bubbleAlpha by animateFloatAsState(if (showBubble) 1f else 0f, tween(160), label = "hyperBubble")

    val latestPhase = rememberUpdatedState(phase)
    val latestCount = rememberUpdatedState(notifications.size)
    val latestIndex = rememberUpdatedState(safeIndex)
    val apply = rememberUpdatedState<(HyperGestureEffect) -> Unit> { effect ->
        onUserEngaged()
        val kind = when (effect.phase) {
            HyperIslandPhase.Collapsed -> if (effect.dismiss) HapticKind.Swipe else HapticKind.Collapse
            HyperIslandPhase.Card -> if (latestPhase.value == HyperIslandPhase.Card) HapticKind.Swipe else HapticKind.Expand
            HyperIslandPhase.Compact -> if (effect.selectIndex != null) HapticKind.Swipe else HapticKind.Expand
        }
        IslandHaptics.play(context, HapticStrength.fromId(settings.hapticStrength), kind)
        effect.selectIndex?.let(onPageSelected)
        if (effect.dismiss) onDismissNotification()
        if (effect.openApp) current?.let(onOpenNotification)
        if (effect.phase == HyperIslandPhase.Card && !expanded) onToggleExpanded()
        if (effect.phase != HyperIslandPhase.Card && expanded) onToggleExpanded()
        phase = effect.phase
    }

    DisposableEffect(Unit) {
        onDispose { HyperIslandTouch.clear() }
    }

    var pillRect by remember { mutableStateOf<Rect?>(null) }
    var bubbleRect by remember { mutableStateOf<Rect?>(null) }
    SideEffect {
        val pill = pillRect
        val bubble = if (showBubble) bubbleRect else null
        HyperIslandTouch.bounds.value = when {
            pill == null -> bubble
            bubble == null -> pill
            else -> Rect(
                minOf(pill.left, bubble.left),
                minOf(pill.top, bubble.top),
                maxOf(pill.right, bubble.right),
                maxOf(pill.bottom, bubble.bottom)
            )
        }
        HyperIslandTouch.cardOpen.value = phase == HyperIslandPhase.Card
    }

    Box(modifier.fillMaxSize()) {
        if (phase == HyperIslandPhase.Card) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            apply.value(HyperGestureEffect(HyperIslandPhase.Collapsed))
                        }
                    }
            )
        }
        HyperShell(
            width = width,
            height = height,
            radius = radius,
            xOff = xOff,
            yOff = yOff,
            description = description,
            onRect = { pillRect = it },
            modifier = Modifier.hyperGestures {
                val effect = HyperIslandGestures.resolve(
                    latestPhase.value, it, latestCount.value, latestIndex.value
                )
                apply.value(effect)
            }
        ) {
            HyperIslandContent(
                phase = phase,
                notification = current,
                template = template,
                headerHeight = minOf(compact.heightDp.dp, height),
                holeWidth = holeWidth,
                holeLeft = holeLeft,
                shellWidth = width,
                onReplyActive = onReplyStateChanged
            )
        }
        val bubbleIndex = secondaryIndex
        if (bubbleAlpha > 0.02f && bubbleIndex != null && secondary != null) {
            val bubbleTop = yOff + (compact.heightDp.dp - bubbleSize) / 2
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(bubbleX.roundToPx(), bubbleTop.roundToPx()) }
                    .graphicsLayer { alpha = bubbleAlpha }
                    .size(bubbleSize)
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInWindow()
                        bubbleRect = Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                    }
                    .clip(CircleShape)
                    .background(glassBrush())
                    .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                    .pointerInput(bubbleIndex) {
                        detectTapGestures {
                            apply.value(HyperGestureEffect(latestPhase.value, selectIndex = bubbleIndex))
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                HyperLeadingIcon(secondary, (bubbleSize.value * 0.62f).dp)
            }
        }
    }
}

@Composable
private fun BoxScope.HyperShell(
    width: Dp,
    height: Dp,
    radius: Dp,
    xOff: Dp,
    yOff: Dp,
    description: String,
    onRect: (Rect) -> Unit,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .align(Alignment.TopCenter)
            .offset(x = xOff, y = yOff)
            .size(width, height)
            .onGloballyPositioned { coords ->
                val b = coords.boundsInWindow()
                onRect(Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt()))
            }
            .semantics { contentDescription = description }
            .clip(shape)
            .background(glassBrush())
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.20f))
        )
        content()
    }
}

@Composable
private fun HyperIslandContent(
    phase: HyperIslandPhase,
    notification: IslandNotification?,
    template: com.agupta07505.smartisland.model.HyperTemplate,
    headerHeight: Dp,
    holeWidth: Dp,
    holeLeft: Dp,
    shellWidth: Dp,
    onReplyActive: (Boolean) -> Unit
) {
    val right = (shellWidth - holeLeft - holeWidth).coerceAtLeast(0.dp)
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(headerHeight)
        ) {
            if (holeLeft >= 16.dp) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(holeLeft)
                        .fillMaxHeight()
                        .padding(start = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    HyperLeadingIcon(notification, if (phase == HyperIslandPhase.Collapsed) 14.dp else 22.dp)
                }
            }
            if (right >= 28.dp && phase != HyperIslandPhase.Collapsed) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(right)
                        .fillMaxHeight()
                        .padding(end = 10.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    HyperCompactStatus(notification, template)
                }
            }
        }
        if (phase == HyperIslandPhase.Card) {
            HyperCardBody(
                notification = notification,
                template = template,
                onReplyActive = onReplyActive,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
            )
        }
    }
}

private fun glassBrush(): Brush = Brush.verticalGradient(
    0f to Color(0xF22C2C31),
    0.45f to Color(0xF0141418),
    1f to Color(0xF20A0A0E)
)

private fun Modifier.hyperGestures(onGesture: (HyperGesture) -> Unit): Modifier = pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop
    val threshold = HyperIslandGestures.SWIPE_DISTANCE_DP.dp.toPx()
    val longMs = viewConfiguration.longPressTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        val start = down.position
        val pointer = down.id
        val outcome = withTimeoutOrNull(longMs) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointer } ?: return@withTimeoutOrNull "up"
                if (!change.pressed) return@withTimeoutOrNull "up"
                val dx = change.position.x - start.x
                val dy = change.position.y - start.y
                if (dx * dx + dy * dy > slop * slop) return@withTimeoutOrNull "drag"
            }
        }
        when (outcome) {
            "up" -> onGesture(HyperGesture.Tap)
            null -> {
                onGesture(HyperGesture.LongPress)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointer } ?: break
                    if (!change.pressed) break
                }
            }
            else -> {
                var dx = 0f
                var dy = 0f
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointer } ?: break
                    dx = change.position.x - start.x
                    dy = change.position.y - start.y
                    if (!change.pressed) break
                }
                val gesture = when {
                    abs(dx) >= abs(dy) && abs(dx) >= threshold && dx < 0f -> HyperGesture.SwipeLeft
                    abs(dx) >= abs(dy) && abs(dx) >= threshold && dx > 0f -> HyperGesture.SwipeRight
                    dy < 0f && abs(dy) >= threshold && abs(dy) > abs(dx) -> HyperGesture.SwipeUp
                    else -> null
                }
                gesture?.let(onGesture)
            }
        }
    }
}
