package app.luma.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapStreakTest {
    @Test fun fiveQuickTapsSpinTheMarkAndTheCountStartsOver() {
        val streak = TapStreak()
        assertFalse((0 until 4).any { streak.tap(it * 400L) })
        assertTrue(streak.tap(1600))
        // Firing uses the streak up: the next quick tap is the first of a new one.
        assertFalse(streak.tap(1800))
    }

    @Test fun aPauseBreaksTheStreak() {
        val streak = TapStreak()
        repeat(4) { streak.tap(it * 300L) }
        assertFalse(streak.tap(900 + 701))
        // …and the tap after the pause counts as the first again.
        assertFalse((1 until 4).any { streak.tap(1601 + it * 300L) })
        assertTrue(streak.tap(1601 + 4 * 300L))
    }
}
