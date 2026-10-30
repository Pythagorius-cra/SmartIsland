/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.ui.hyper

import android.graphics.Rect
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Touch target of the HyperOS-style island, in overlay-window pixels. The service reads this when
 * the style is on so the full-screen window only catches the pill, the side bubble, and (while the
 * card is open) taps outside it.
 */
internal object HyperIslandTouch {
    val bounds = MutableStateFlow<Rect?>(null)
    val cardOpen = MutableStateFlow(false)

    fun clear() {
        bounds.value = null
        cardOpen.value = false
    }
}
