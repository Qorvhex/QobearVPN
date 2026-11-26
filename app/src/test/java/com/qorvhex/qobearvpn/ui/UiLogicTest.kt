package com.qorvhex.qobearvpn.ui

import androidx.compose.ui.graphics.Color
import com.qorvhex.qobearvpn.ui.components.sweepIntensity
import com.qorvhex.qobearvpn.ui.screens.pingColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiLogicTest {
    @Test
    fun pingUnder300MsIsGreen() {
        val green = Color.Green
        val amber = Color.Yellow
        val red = Color.Red

        assertEquals(green, pingColor(45L, green, amber, red))
        assertEquals(green, pingColor(299L, green, amber, red))
        assertEquals(amber, pingColor(300L, green, amber, red))
        assertEquals(amber, pingColor(599L, green, amber, red))
        assertEquals(red, pingColor(600L, green, amber, red))
    }

    @Test
    fun dialSweepIntensityContinuousAcrossRightQuadrantWrap() {
        // Right side dots sit in -90..0 deg screen coordinates (top-right to 3 o'clock).
        // Verify intensity decays smoothly behind radarAngle with zero stall across 359 -> 1 deg.
        val beforeWrap = sweepIntensity(dotAngleDeg = -5f, radarAngleDeg = 358f)
        val atZero = sweepIntensity(dotAngleDeg = -5f, radarAngleDeg = 0f)
        val afterWrap = sweepIntensity(dotAngleDeg = -5f, radarAngleDeg = 5f)

        assertTrue(beforeWrap in 0.9f..1.0f)
        assertTrue(atZero in 0.9f..1.0f)
        assertTrue(afterWrap in 0.85f..0.95f)
        assertTrue(beforeWrap >= atZero && atZero >= afterWrap)

        // Points far ahead must stay dimmed (near 0) rather than latching at 1.0
        assertEquals(0f, sweepIntensity(dotAngleDeg = -45f, radarAngleDeg = 200f), 0.001f)
    }
}
