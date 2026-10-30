/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.model

/**
 * HyperOS-style island behaviour, tuned from public observation of HyperOS 2/3 Hyper Island
 * (developer docs, reviews, and slow-motion clips). These numbers are not taken from Xiaomi
 * source or assets. The original Smart Island look stays in place when the setting is off.
 *
 * Three resting shapes:
 * - [HyperIslandPhase.Collapsed] hugs the punch-hole.
 * - [HyperIslandPhase.Compact] is a wider capsule: icon on the left of the camera, status on the right.
 * - [HyperIslandPhase.Card] is the full panel, with corners that stay at the pill radius.
 */
enum class HyperIslandPhase { Collapsed, Compact, Card }

enum class HyperGesture { Tap, LongPress, SwipeLeft, SwipeRight, SwipeUp }

data class HyperGestureEffect(
    val phase: HyperIslandPhase,
    /** Absolute index to show. Null keeps the current activity. */
    val selectIndex: Int? = null,
    val dismiss: Boolean = false,
    /** Tap on the open card opens the app behind the activity. */
    val openApp: Boolean = false
)

/**
 * Springs chosen to feel like the public HyperOS morph: expand settles in about a third of a
 * second with a small overshoot ("liquid"); collapse is a little stiffer and barely bounces.
 * Width eases slightly slower than height so the capsule stretches before it grows down.
 */
object HyperIslandMotion {
    const val EXPAND_DAMPING = 0.74f
    const val EXPAND_STIFFNESS = 320f
    const val COLLAPSE_DAMPING = 0.88f
    const val COLLAPSE_STIFFNESS = 500f
    const val WIDTH_STIFFNESS_SCALE = 0.84f
    const val HEIGHT_STIFFNESS_SCALE = 1.06f

    fun expanding(from: HyperIslandPhase, to: HyperIslandPhase): Boolean = to.ordinal >= from.ordinal

    fun damping(expanding: Boolean): Float = if (expanding) EXPAND_DAMPING else COLLAPSE_DAMPING

    fun stiffness(expanding: Boolean, axisScale: Float): Float =
        (if (expanding) EXPAND_STIFFNESS else COLLAPSE_STIFFNESS) * axisScale

    /**
     * Capsule corners while the island is still pill-shaped; once it grows into a card the radius
     * eases to half the collapsed pill so the card corners match the pill.
     */
    fun cornerRadius(heightDp: Float, pillHeightDp: Float): Float {
        val pill = pillHeightDp.coerceAtLeast(1f)
        val capsule = (heightDp / 2f).coerceAtLeast(0f)
        if (heightDp <= pill * 1.12f) return capsule
        val t = ((heightDp - pill) / (pill * 2.2f)).coerceIn(0f, 1f)
        val cardRadius = pill / 2f
        return capsule * (1f - t) + cardRadius * t
    }
}

/** The front-camera hole in dp. [centerXOffsetDp] is from the screen centre, positive to the right. */
data class HyperHole(
    val centerXOffsetDp: Float,
    val topDp: Float,
    val widthDp: Float,
    val heightDp: Float,
    val detected: Boolean
)

/** One resting frame of the island, in the same coordinates as [HyperHole]. */
data class HyperFrame(
    val xOffsetDp: Float,
    val yOffsetDp: Float,
    val widthDp: Float,
    val heightDp: Float,
    val radiusDp: Float,
    val holeWidthDp: Float,
    /** Left edge of the hole measured from the left edge of this frame. */
    val holeLeftInPillDp: Float
)

object HyperIslandGeometry {
    const val COLLAPSED_PAD_X = 20f
    const val COLLAPSED_PAD_Y = 10f
    const val MIN_COLLAPSED_W = 40f
    const val MAX_COLLAPSED_W = 92f
    const val MIN_COLLAPSED_H = 28f
    const val MAX_COLLAPSED_H = 42f
    /** Each side of the compact capsule, outside the hole, for the icon and the status. */
    const val COMPACT_LOBE = 86f
    const val MIN_COMPACT_W = 168f
    const val MAX_COMPACT_W = 248f
    const val COMPACT_MIN_H = 34f
    const val CARD_WIDTH = 336f
    const val SCREEN_MARGIN = 12f

    /**
     * Used only when the display reports no cutout. A center punch-hole of this size matches a
     * Pixel 9 class front camera closely enough for the pill to sit in the right place until
     * [android.view.DisplayCutout] is available.
     */
    const val FALLBACK_HOLE_DP = 14f
    const val FALLBACK_TOP_DP = 11f

    fun fallbackHole(): HyperHole = HyperHole(
        centerXOffsetDp = 0f,
        topDp = FALLBACK_TOP_DP,
        widthDp = FALLBACK_HOLE_DP,
        heightDp = FALLBACK_HOLE_DP,
        detected = false
    )

    fun manualHole(xOffsetDp: Float, topDp: Float, widthDp: Float, heightDp: Float): HyperHole = HyperHole(
        centerXOffsetDp = xOffsetDp,
        topDp = topDp,
        widthDp = widthDp.coerceIn(6f, 96f),
        heightDp = heightDp.coerceIn(6f, 64f),
        detected = true
    )

    fun holeFromPixels(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        screenWidthPx: Int,
        density: Float
    ): HyperHole {
        if (density <= 0f || screenWidthPx <= 0 || right <= left || bottom <= top) return fallbackHole()
        val widthDp = ((right - left) / density).coerceIn(6f, 96f)
        val heightDp = ((bottom - top) / density).coerceIn(6f, 64f)
        val centerPx = (left + right) / 2f
        val offsetDp = ((centerPx - screenWidthPx / 2f) / density).coerceIn(-180f, 180f)
        val topDp = (top / density).coerceIn(0f, 120f)
        return HyperHole(offsetDp, topDp, widthDp, heightDp, detected = true)
    }

    fun frame(
        hole: HyperHole,
        screenWidthDp: Float,
        phase: HyperIslandPhase,
        cardHeightDp: Float
    ): HyperFrame {
        val screen = screenWidthDp.coerceAtLeast(MIN_COMPACT_W + SCREEN_MARGIN * 2)
        val collapsedW = (hole.widthDp + COLLAPSED_PAD_X).coerceIn(MIN_COLLAPSED_W, MAX_COLLAPSED_W)
        val collapsedH = (hole.heightDp + COLLAPSED_PAD_Y).coerceIn(MIN_COLLAPSED_H, MAX_COLLAPSED_H)
        val compactW = (hole.widthDp + COMPACT_LOBE * 2f).coerceIn(
            MIN_COMPACT_W,
            minOf(MAX_COMPACT_W, screen - SCREEN_MARGIN * 2)
        )
        val compactH = maxOf(collapsedH, COMPACT_MIN_H)
        val cardW = minOf(CARD_WIDTH, screen - SCREEN_MARGIN * 2).coerceAtLeast(compactW)
        val cardH = cardHeightDp.coerceIn(96f, 420f)

        val width = when (phase) {
            HyperIslandPhase.Collapsed -> collapsedW
            HyperIslandPhase.Compact -> compactW
            HyperIslandPhase.Card -> cardW
        }
        val height = when (phase) {
            HyperIslandPhase.Collapsed -> collapsedH
            HyperIslandPhase.Compact -> compactH
            HyperIslandPhase.Card -> cardH
        }
        val collapsedY = (hole.topDp - (collapsedH - hole.heightDp) / 2f).coerceAtLeast(0f)
        val y = when (phase) {
            HyperIslandPhase.Collapsed -> collapsedY
            HyperIslandPhase.Compact -> (hole.topDp - (compactH - hole.heightDp) / 2f).coerceAtLeast(0f)
            HyperIslandPhase.Card -> collapsedY
        }
        val x = clampCenter(hole.centerXOffsetDp, width, screen)
        return HyperFrame(
            xOffsetDp = x,
            yOffsetDp = y,
            widthDp = width,
            heightDp = height,
            radiusDp = HyperIslandMotion.cornerRadius(height, collapsedH),
            holeWidthDp = hole.widthDp.coerceAtMost(width),
            holeLeftInPillDp = holeLeftInPill(x, width, hole.centerXOffsetDp, hole.widthDp)
        )
    }

    fun clampCenter(centerOffsetDp: Float, widthDp: Float, screenWidthDp: Float): Float {
        val half = widthDp / 2f
        val minCenter = -screenWidthDp / 2f + SCREEN_MARGIN + half
        val maxCenter = screenWidthDp / 2f - SCREEN_MARGIN - half
        if (minCenter > maxCenter) return 0f
        return centerOffsetDp.coerceIn(minCenter, maxCenter)
    }

    fun holeLeftInPill(pillXOffsetDp: Float, pillWidthDp: Float, holeCenterOffsetDp: Float, holeWidthDp: Float): Float {
        val pillLeft = pillXOffsetDp - pillWidthDp / 2f
        val holeLeft = holeCenterOffsetDp - holeWidthDp / 2f
        val maxLeft = (pillWidthDp - holeWidthDp).coerceAtLeast(0f)
        return (holeLeft - pillLeft).coerceIn(0f, maxLeft)
    }

    /** Centre of the secondary bubble, in the same x coordinates as the pill. */
    fun bubbleCenterX(
        pillXOffsetDp: Float,
        pillWidthDp: Float,
        bubbleSizeDp: Float,
        gapDp: Float,
        onRight: Boolean
    ): Float {
        val reach = pillWidthDp / 2f + gapDp + bubbleSizeDp / 2f
        return if (onRight) pillXOffsetDp + reach else pillXOffsetDp - reach
    }
}

object HyperActivities {
    fun slots(count: Int, selected: Int): HyperActivitySlots {
        if (count <= 0) return HyperActivitySlots(primary = 0, secondary = null)
        val primary = selected.coerceIn(0, count - 1)
        if (count == 1) return HyperActivitySlots(primary, secondary = null)
        return HyperActivitySlots(primary, secondary = (primary + 1) % count)
    }

    fun indexAfterSwipe(count: Int, selected: Int, forward: Boolean): Int {
        if (count <= 1) return selected.coerceAtLeast(0)
        val current = selected.coerceIn(0, count - 1)
        return if (forward) (current + 1) % count else (current - 1 + count) % count
    }
}

data class HyperActivitySlots(val primary: Int, val secondary: Int?)

object HyperIslandGestures {
    /** Travel, in dp, before a drag counts as a swipe. Short of this, the finger was a tap. */
    const val SWIPE_DISTANCE_DP = 42f

    fun resolve(
        phase: HyperIslandPhase,
        gesture: HyperGesture,
        count: Int,
        selected: Int
    ): HyperGestureEffect {
        val safeSelected = if (count <= 0) 0 else selected.coerceIn(0, count - 1)
        return when (gesture) {
            HyperGesture.Tap -> when (phase) {
                HyperIslandPhase.Collapsed -> HyperGestureEffect(HyperIslandPhase.Compact)
                HyperIslandPhase.Compact -> HyperGestureEffect(HyperIslandPhase.Compact)
                HyperIslandPhase.Card -> HyperGestureEffect(HyperIslandPhase.Card, openApp = count > 0)
            }
            HyperGesture.LongPress -> HyperGestureEffect(HyperIslandPhase.Card)
            HyperGesture.SwipeLeft -> swipe(phase, count, safeSelected, forward = true)
            HyperGesture.SwipeRight -> swipe(phase, count, safeSelected, forward = false)
            HyperGesture.SwipeUp -> when (phase) {
                HyperIslandPhase.Card, HyperIslandPhase.Compact -> HyperGestureEffect(HyperIslandPhase.Collapsed)
                HyperIslandPhase.Collapsed -> HyperGestureEffect(
                    HyperIslandPhase.Collapsed,
                    dismiss = count > 0
                )
            }
        }
    }

    private fun swipe(phase: HyperIslandPhase, count: Int, selected: Int, forward: Boolean): HyperGestureEffect {
        if (count < 2) return HyperGestureEffect(phase)
        return HyperGestureEffect(
            phase = phase,
            selectIndex = HyperActivities.indexAfterSwipe(count, selected, forward)
        )
    }
}

enum class HyperTemplate {
    Empty, Music, Call, Timer, Stopwatch, Charging, Navigation, Ride, Progress, HeadsUp
}

object HyperTemplates {
    fun cardHeight(template: HyperTemplate): Float = when (template) {
        HyperTemplate.Empty -> 112f
        HyperTemplate.Music -> 228f
        HyperTemplate.Call -> 196f
        HyperTemplate.Timer, HyperTemplate.Stopwatch -> 176f
        HyperTemplate.Charging -> 168f
        HyperTemplate.Navigation, HyperTemplate.Ride -> 196f
        HyperTemplate.Progress -> 180f
        HyperTemplate.HeadsUp -> 214f
    }

    fun of(mode: IslandMode, hasReply: Boolean): HyperTemplate = when (mode) {
        IslandMode.Empty -> HyperTemplate.Empty
        IslandMode.Music -> HyperTemplate.Music
        IslandMode.IncomingCall -> HyperTemplate.Call
        IslandMode.Timer -> HyperTemplate.Timer
        IslandMode.Stopwatch -> HyperTemplate.Stopwatch
        IslandMode.Battery -> HyperTemplate.Charging
        IslandMode.Navigation -> HyperTemplate.Navigation
        IslandMode.LiveActivity -> HyperTemplate.Ride
        IslandMode.DownloadUpload -> HyperTemplate.Progress
        IslandMode.Notification -> if (hasReply) HyperTemplate.HeadsUp else HyperTemplate.HeadsUp
        else -> HyperTemplate.HeadsUp
    }
}

/**
 * Android 16 Live Updates post a [android.app.Notification.ProgressStyle] template. When HyperOS
 * style is on, those map onto the navigation, ride, or progress island instead of a plain heads-up.
 * Wording is matched as plain text; nothing here is copied from an OEM parser.
 */
object HyperLiveUpdate {
    private val NAV = listOf(
        "navigate", "navigation", "rerout", "eta", "turn left", "turn right", "km", " mi",
        "навиг", "маршрут", "поворот"
    )
    private val RIDE = listOf(
        "uber", "lyft", "ola", "grab", "delivery", "courier", "driver", "pickup", "drop-off",
        "dropoff", "arriving", "order", "doordash", "deliveroo", "swiggy", "zomato", "blinkit",
        "курьер", "доставк", "водитель"
    )

    fun refine(
        mode: IslandMode,
        progressStyle: Boolean,
        title: String,
        text: String,
        category: String?
    ): IslandMode {
        val live = progressStyle || category.equals("progress", ignoreCase = true)
        if (!live) return mode
        if (mode == IslandMode.IncomingCall || mode == IslandMode.Music ||
            mode == IslandMode.Timer || mode == IslandMode.Stopwatch || mode == IslandMode.Battery
        ) {
            return mode
        }
        if (mode == IslandMode.Navigation || mode == IslandMode.LiveActivity) return mode
        val blob = "$title $text".lowercase()
        if (NAV.any { blob.contains(it) }) return IslandMode.Navigation
        if (RIDE.any { blob.contains(it) }) return IslandMode.LiveActivity
        if (mode == IslandMode.Notification) return IslandMode.DownloadUpload
        return mode
    }
}

object HyperFormat {
    fun clock(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val seconds = safe % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    fun fraction(progress: Int, max: Int): Float? {
        if (max <= 0) return null
        return (progress.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    }
}
