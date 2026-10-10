package me.kavishdevar.librepods.data

import org.junit.Assert.*
import org.junit.Test

class AirPodsModelRegistryTest {
    @Test fun newModelsAreDiscoverableByEveryUpstreamModelNumber() {
        for (number in listOf("A3531", "A3532", "A3533")) {
            assertTrue("Missing $number", AirPodsModels.getModelByModelNumber(number) is AirPods5)
        }
        for (number in listOf("A3439", "A3440", "A3441")) {
            assertTrue("Missing $number", AirPodsModels.getModelByModelNumber(number) is AirPods5WCC)
        }
    }
    @Test fun preservesExistingPro3AndRejectsUnknownModels() {
        for (number in listOf("A3063", "A3064", "A3065")) {
            assertTrue(AirPodsModels.getModelByModelNumber(number) is AirPodsPro3)
        }
        assertNull(AirPodsModels.getModelByModelNumber("unknown"))
        val numbers = AirPodsModels.models.flatMap { it.modelNumber }
        assertEquals("Ambiguous model registry", numbers.size, numbers.toSet().size)
    }
}
