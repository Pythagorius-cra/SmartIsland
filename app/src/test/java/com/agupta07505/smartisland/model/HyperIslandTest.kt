/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.model

import com.agupta07505.smartisland.data.SmartIslandSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HyperIslandTest {

    /** Pixel 9 logical width is about 411 dp; the front camera is a center punch-hole. */
    private val pixel9Width = 411f

    private fun pixelHole(width: Float = 16f, height: Float = 16f, top: Float = 8f) = HyperHole(
        centerXOffsetDp = 0f,
        topDp = top,
        widthDp = width,
        heightDp = height,
        detected = true
    )

    @Test
    fun collapsedPillIsCenteredOnAPixel9PunchHole() {
        val frame = HyperIslandGeometry.frame(pixelHole(), pixel9Width, HyperIslandPhase.Collapsed, 180f)
        assertEquals(0f, frame.xOffsetDp, 0.01f)
        assertTrue(frame.widthDp in 40f..92f)
        assertTrue(frame.widthDp > 16f)
        assertTrue(frame.heightDp in 28f..42f)
        val holeCenter = frame.holeLeftInPillDp + 16f / 2f
        assertEquals(frame.widthDp / 2f, holeCenter, 0.6f)
        assertEquals(frame.heightDp / 2f, frame.radiusDp, 0.01f)
    }

    @Test
    fun compactCapsulePutsRoomOnBothSidesOfTheHole() {
        val frame = HyperIslandGeometry.frame(pixelHole(), pixel9Width, HyperIslandPhase.Compact, 180f)
        assertEquals(0f, frame.xOffsetDp, 0.01f)
        assertTrue(frame.widthDp >= HyperIslandGeometry.MIN_COMPACT_W)
        val leftLobe = frame.holeLeftInPillDp
        val rightLobe = frame.widthDp - frame.holeLeftInPillDp - frame.holeWidthDp
        assertTrue(leftLobe > 40f)
        assertTrue(rightLobe > 40f)
        assertEquals(leftLobe, rightLobe, 1f)
    }

    @Test
    fun cardCornersMatchTheCollapsedPillRadius() {
        val collapsed = HyperIslandGeometry.frame(pixelHole(), pixel9Width, HyperIslandPhase.Collapsed, 180f)
        val card = HyperIslandGeometry.frame(pixelHole(), pixel9Width, HyperIslandPhase.Card, 220f)
        assertEquals(collapsed.heightDp / 2f, card.radiusDp, 0.05f)
        assertTrue(card.heightDp > collapsed.heightDp)
        assertTrue(card.widthDp > collapsed.widthDp)
        assertEquals(collapsed.yOffsetDp, card.yOffsetDp, 0.01f)
    }

    @Test
    fun manualHoleOverridesTheDetectedCenter() {
        val manual = HyperIslandGeometry.manualHole(xOffsetDp = 24f, topDp = 18f, widthDp = 30f, heightDp = 20f)
        val frame = HyperIslandGeometry.frame(manual, pixel9Width, HyperIslandPhase.Collapsed, 180f)
        assertEquals(24f, frame.xOffsetDp, 0.01f)
        assertTrue(frame.yOffsetDp >= 0f)
        assertTrue(frame.holeWidthDp <= 30f + 0.1f)
    }

    @Test
    fun holeFromPixelsCentersASymmetricCutout() {
        val density = 2.625f
        val screenPx = (pixel9Width * density).toInt()
        val holePx = (14f * density).toInt()
        val left = (screenPx - holePx) / 2
        val hole = HyperIslandGeometry.holeFromPixels(left, 20, left + holePx, 20 + holePx, screenPx, density)
        assertTrue(hole.detected)
        assertEquals(0f, hole.centerXOffsetDp, 0.6f)
        assertEquals(14f, hole.widthDp, 1.2f)
    }

    @Test
    fun missingCutoutFallsBackToACenterHole() {
        val hole = HyperIslandGeometry.fallbackHole()
        assertFalse(hole.detected)
        assertEquals(0f, hole.centerXOffsetDp, 0f)
        val frame = HyperIslandGeometry.frame(hole, pixel9Width, HyperIslandPhase.Collapsed, 160f)
        assertEquals(0f, frame.xOffsetDp, 0.01f)
    }

    @Test
    fun wideCardStaysOnANarrowScreen() {
        val frame = HyperIslandGeometry.frame(pixelHole(), 200f, HyperIslandPhase.Card, 200f)
        val left = 100f + frame.xOffsetDp - frame.widthDp / 2f
        val right = left + frame.widthDp
        assertTrue(left >= HyperIslandGeometry.SCREEN_MARGIN - 0.1f)
        assertTrue(right <= 200f - HyperIslandGeometry.SCREEN_MARGIN + 0.1f)
    }

    @Test
    fun cornerRadiusTracksThePillThenLocks() {
        val pill = 32f
        assertEquals(16f, HyperIslandMotion.cornerRadius(32f, pill), 0.01f)
        val tall = HyperIslandMotion.cornerRadius(220f, pill)
        assertEquals(pill / 2f, tall, 0.05f)
        assertTrue(HyperIslandMotion.expanding(HyperIslandPhase.Collapsed, HyperIslandPhase.Compact))
        assertFalse(HyperIslandMotion.expanding(HyperIslandPhase.Card, HyperIslandPhase.Collapsed))
    }

    @Test
    fun tapExpandsAndLongPressOpensTheCard() {
        val tap = HyperIslandGestures.resolve(HyperIslandPhase.Collapsed, HyperGesture.Tap, 1, 0)
        assertEquals(HyperIslandPhase.Compact, tap.phase)
        assertFalse(tap.dismiss)
        val hold = HyperIslandGestures.resolve(HyperIslandPhase.Compact, HyperGesture.LongPress, 1, 0)
        assertEquals(HyperIslandPhase.Card, hold.phase)
        val again = HyperIslandGestures.resolve(HyperIslandPhase.Compact, HyperGesture.Tap, 1, 0)
        assertEquals(HyperIslandPhase.Compact, again.phase)
    }

    @Test
    fun swipeUpCollapsesThenDismisses() {
        val fromCard = HyperIslandGestures.resolve(HyperIslandPhase.Card, HyperGesture.SwipeUp, 2, 0)
        assertEquals(HyperIslandPhase.Collapsed, fromCard.phase)
        assertFalse(fromCard.dismiss)
        val fromPill = HyperIslandGestures.resolve(HyperIslandPhase.Collapsed, HyperGesture.SwipeUp, 2, 0)
        assertTrue(fromPill.dismiss)
        val idle = HyperIslandGestures.resolve(HyperIslandPhase.Collapsed, HyperGesture.SwipeUp, 0, 0)
        assertFalse(idle.dismiss)
    }

    @Test
    fun horizontalSwipeSwapsConcurrentActivities() {
        val slots = HyperActivities.slots(count = 2, selected = 0)
        assertEquals(0, slots.primary)
        assertEquals(1, slots.secondary)
        val next = HyperIslandGestures.resolve(HyperIslandPhase.Compact, HyperGesture.SwipeLeft, 2, 0)
        assertEquals(1, next.selectIndex)
        assertEquals(HyperIslandPhase.Compact, next.phase)
        val back = HyperIslandGestures.resolve(HyperIslandPhase.Compact, HyperGesture.SwipeRight, 3, 0)
        assertEquals(2, back.selectIndex)
        val alone = HyperIslandGestures.resolve(HyperIslandPhase.Compact, HyperGesture.SwipeLeft, 1, 0)
        assertNull(alone.selectIndex)
        assertNull(HyperActivities.slots(1, 0).secondary)
    }

    @Test
    fun progressStyleMapsOntoIslandTemplates() {
        assertEquals(
            IslandMode.Navigation,
            HyperLiveUpdate.refine(IslandMode.Notification, true, "Turn left", "200 m", null)
        )
        assertEquals(
            IslandMode.LiveActivity,
            HyperLiveUpdate.refine(IslandMode.DownloadUpload, true, "Uber", "Driver arriving", "progress")
        )
        assertEquals(
            IslandMode.DownloadUpload,
            HyperLiveUpdate.refine(IslandMode.Notification, true, "Backup", "45%", "progress")
        )
        assertEquals(
            IslandMode.Music,
            HyperLiveUpdate.refine(IslandMode.Music, true, "Song", "Artist", "progress")
        )
        assertEquals(
            IslandMode.Notification,
            HyperLiveUpdate.refine(IslandMode.Notification, false, "Hi", "There", null)
        )
        assertEquals(HyperTemplate.Ride, HyperTemplates.of(IslandMode.LiveActivity, hasReply = false))
        assertEquals(HyperTemplate.Music, HyperTemplates.of(IslandMode.Music, hasReply = false))
        assertEquals(HyperTemplate.HeadsUp, HyperTemplates.of(IslandMode.Notification, hasReply = true))
    }

    @Test
    fun clockAndSettingsFlagRoundTrip() {
        assertEquals("1:05", HyperFormat.clock(65))
        assertEquals("1:00:00", HyperFormat.clock(3600))
        assertEquals(0.5f, HyperFormat.fraction(1, 2)!!, 0.001f)
        assertNull(HyperFormat.fraction(1, 0))
        val saved = SmartIslandSettings(hyperOsStyle = true)
        assertTrue(SmartIslandSettings.fromJson(saved.toJson()).hyperOsStyle)
        assertFalse(SmartIslandSettings.fromJson(SmartIslandSettings().toJson()).hyperOsStyle)
    }
}
