package me.kavishdevar.librepods.services

/** AACP listening-mode values, in picker order. */
internal fun availableQuickSettingsModes(adaptiveSupported: Boolean, offEnabled: Boolean): List<Int> =
    buildList {
        add(3) // Transparency
        add(2) // Noise cancellation
        if (adaptiveSupported) add(4)
        if (offEnabled) add(1)
    }
