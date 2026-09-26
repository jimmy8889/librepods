package me.kavishdevar.librepods.services

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickSettingsModesTest {
    @Test fun proModesUseCorrectWireValues() {
        assertEquals(listOf(3, 2, 4, 1), availableQuickSettingsModes(true, true))
    }
    @Test fun hiddenOffAndUnsupportedAdaptiveAreExcluded() {
        assertEquals(listOf(3, 2), availableQuickSettingsModes(false, false))
        assertEquals(listOf(3, 2, 1), availableQuickSettingsModes(false, true))
        assertEquals(listOf(3, 2, 4), availableQuickSettingsModes(true, false))
    }
}
