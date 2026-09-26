package me.kavishdevar.librepods.services

import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickSettingsBatteryTest {
    private fun value(level: Int, status: Int) = quickSettingsBatteryValue(
        listOf(Battery(BatteryComponent.LEFT, level, status)), BatteryComponent.LEFT)
    @Test fun preservesEmptyAndFullReadingsAndCharging() {
        assertEquals("0%", value(0, BatteryStatus.NOT_CHARGING))
        assertEquals("100%", value(100, BatteryStatus.NOT_CHARGING))
        assertEquals("⚡72%", value(72, BatteryStatus.CHARGING))
        assertEquals("80%", value(80, BatteryStatus.OPTIMIZED_CHARGING))
    }
    @Test fun hidesUnavailableAndInvalidReadings() {
        assertEquals("—", quickSettingsBatteryValue(emptyList(), BatteryComponent.CASE))
        assertEquals("—", value(50, BatteryStatus.DISCONNECTED))
        assertEquals("—", value(255, BatteryStatus.NOT_CHARGING))
        assertEquals("—", value(-1, BatteryStatus.NOT_CHARGING))
        assertEquals("—", value(50, 0))
    }
}
