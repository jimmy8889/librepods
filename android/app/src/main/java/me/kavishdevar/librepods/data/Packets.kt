/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package me.kavishdevar.librepods.data

import android.os.Parcelable
import android.util.Log
import kotlinx.parcelize.Parcelize

// TODO: Remove everything but Battery-related stuff

enum class Enums(val value: ByteArray) {
    NOISE_CANCELLATION(byteArrayOf(0x0d)),
    PREFIX(byteArrayOf(0x04, 0x00, 0x04, 0x00)),
    SETTINGS(byteArrayOf(0x09, 0x00)),
    NOISE_CANCELLATION_PREFIX(PREFIX.value + SETTINGS.value + NOISE_CANCELLATION.value),
    CONVERSATION_AWARENESS_RECEIVE_PREFIX(PREFIX.value + byteArrayOf(0x4b, 0x00, 0x02, 0x00)),
}

object BatteryComponent {
    const val LEFT = 4
    const val RIGHT = 2
    const val CASE = 8
}

object BatteryStatus {
    const val CHARGING = 1
    const val NOT_CHARGING = 2
    const val DISCONNECTED = 4
    const val OPTIMIZED_CHARGING = 5
}

@Parcelize
data class Battery(val component: Int, val level: Int, val status: Int, val estimated: Boolean = false) : Parcelable {
    fun getComponentName(): String? {
        return when (component) {
            BatteryComponent.LEFT -> "LEFT"
            BatteryComponent.RIGHT -> "RIGHT"
            BatteryComponent.CASE -> "CASE"
            else -> null
        }
    }

    fun getStatusName(): String? {
        return when (status) {
            BatteryStatus.CHARGING -> "CHARGING"
            BatteryStatus.NOT_CHARGING -> "NOT_CHARGING"
            BatteryStatus.DISCONNECTED -> "DISCONNECTED"
            BatteryStatus.OPTIMIZED_CHARGING -> "OPTIMIZED_CHARGING"
            else -> null
        }
    }
}

enum class NoiseControlMode {
    OFF,  NOISE_CANCELLATION, TRANSPARENCY, ADAPTIVE
}

class AirPodsNotifications {
    companion object {
        const val AIRPODS_CONNECTED = "me.kavishdevar.librepods.AIRPODS_CONNECTED"
        const val AIRPODS_L2CAP_CONNECTED = "me.kavishdevar.librepods.AIRPODS_CONNECTED"
        const val AIRPODS_DATA = "me.kavishdevar.librepods.AIRPODS_DATA"
        const val EAR_DETECTION_DATA = "me.kavishdevar.librepods.EAR_DETECTION_DATA"
        const val ANC_DATA = "me.kavishdevar.librepods.ANC_DATA"
        const val BATTERY_DATA = "me.kavishdevar.librepods.BATTERY_DATA"
        const val CA_DATA = "me.kavishdevar.librepods.CA_DATA"
        const val AIRPODS_DISCONNECTED = "me.kavishdevar.librepods.AIRPODS_DISCONNECTED"
        const val AIRPODS_CONNECTION_DETECTED = "me.kavishdevar.librepods.AIRPODS_CONNECTION_DETECTED"
        const val DISCONNECT_RECEIVERS = "me.kavishdevar.librepods.DISCONNECT_RECEIVERS"
        const val EQ_DATA = "me.kavishdevar.librepods.HEADPHONE_ACCOMMODATION"
        const val AIRPODS_INFORMATION_UPDATED = "me.kavishdevar.librepods.AIRPODS_INFORMATION_UPDATED"
    }

    class EarDetection {
        private val notificationBit = 6.toByte()
        private val notificationPrefix = Enums.PREFIX.value + notificationBit

        var status: List<Byte> = listOf(0x01, 0x01)

        fun setStatus(data: ByteArray) {
            status = listOf(data[6], data[7])
        }

        fun isEarDetectionData(data: ByteArray): Boolean {
            if (data.size != 8) {
                return false
            }
            val prefixHex = notificationPrefix.joinToString("") { "%02x".format(it) }
            val dataHex = data.joinToString("") { "%02x".format(it) }
            return dataHex.startsWith(prefixHex)
        }
    }

    class ANC {
        private val notificationPrefix = Enums.NOISE_CANCELLATION_PREFIX.value

        var status: Int = 1
            private set

        fun isANCData(data: ByteArray): Boolean {
            if (data.size != 11) {
                return false
            }
            val prefixHex = notificationPrefix.joinToString("") { "%02x".format(it) }
            val dataHex = data.joinToString("") { "%02x".format(it) }
            return dataHex.startsWith(prefixHex)
        }

        fun setStatus(data: ByteArray) {
            when (data.size) {
                // if the whole packet is given
                11 -> {
                    status = data[7].toInt()
                }
                // if only the data is given
                1 -> {
                    status = data[0].toInt()
                }
                // if the value of control command is given
                4 -> {
                    status = data[0].toInt()
                }
                else -> {
                    Log.d("ANC", "Invalid ANC data size: ${data.size}")
                }
            }
        }

        val name: String =
            when (status) {
                1 -> "OFF"
                2 -> "ON"
                3 -> "TRANSPARENCY"
                4 -> "ADAPTIVE"
                else -> "UNKNOWN"
            }

    }

    class BatteryNotification {
        private val batteries = linkedMapOf(
            BatteryComponent.LEFT to Battery(BatteryComponent.LEFT, -1, BatteryStatus.DISCONNECTED),
            BatteryComponent.RIGHT to Battery(BatteryComponent.RIGHT, -1, BatteryStatus.DISCONNECTED),
            BatteryComponent.CASE to Battery(BatteryComponent.CASE, -1, BatteryStatus.DISCONNECTED),
        )
        private val preciseComponents = mutableSetOf<Int>()
        private val liveStatuses = setOf(BatteryStatus.CHARGING, BatteryStatus.NOT_CHARGING, BatteryStatus.OPTIMIZED_CHARGING)
        private val prefix = byteArrayOf(4, 0, 4, 0, 4, 0)

        fun isBatteryData(data: ByteArray): Boolean = data.size == 22 && data.take(6) == prefix.toList()

        private fun update(component: Int, level: Int?, status: Int, onlyMissing: Boolean = false, estimated: Boolean = false) {
            val previous = batteries[component] ?: return
            if (onlyMissing && component in preciseComponents && previous.level in 0..100 && previous.status in liveStatuses) return
            batteries[component] = if (level != null && level in 0..100 && status in liveStatuses) {
                Battery(component, level, status, estimated)
            } else {
                // Disconnected 0 and 0xff are absence markers, not an empty battery.
                previous.copy(status = BatteryStatus.DISCONNECTED)
            }
        }

        @Synchronized fun setBatteryDirect(
            leftLevel: Int?, leftCharging: Boolean, rightLevel: Int?, rightCharging: Boolean,
            caseLevel: Int?, caseCharging: Boolean, preferExisting: Boolean = false, estimated: Boolean = true
        ): Boolean {
            val previous = batteries.values.toList()
            if (!preferExisting) preciseComponents.clear()
            update(BatteryComponent.LEFT,leftLevel,if(leftCharging)BatteryStatus.CHARGING else BatteryStatus.NOT_CHARGING,preferExisting,estimated)
            update(BatteryComponent.RIGHT,rightLevel,if(rightCharging)BatteryStatus.CHARGING else BatteryStatus.NOT_CHARGING,preferExisting,estimated)
            update(BatteryComponent.CASE,caseLevel,if(caseCharging)BatteryStatus.CHARGING else BatteryStatus.NOT_CHARGING,preferExisting,estimated)
            return previous != batteries.values.toList()
        }

        @Synchronized fun setBattery(data: ByteArray) {
            if (!isBatteryData(data)) return
            // Resolve component IDs, not packet ordering. Ignore unknown component IDs.
            for (offset in listOf(7,12,17)) {
                val component=data[offset].toInt() and 0xff
                val level=data[offset+2].toInt() and 0xff
                val status=data[offset+3].toInt() and 0xff
                update(component,level,status)
                if (level in 0..100 && status in liveStatuses) preciseComponents.add(component)
                else preciseComponents.remove(component)
            }
        }

        @Synchronized fun getBattery(): List<Battery> = batteries.values.toList()
    }

    class ConversationAwarenessNotification {
        @Suppress("PrivatePropertyName")
        private val NOTIFICATION_PREFIX = Enums.CONVERSATION_AWARENESS_RECEIVE_PREFIX.value

        var status: Byte = 0
            private set

        fun isConversationAwarenessData(data: ByteArray): Boolean {
            if (data.size != 10) {
                return false
            }
            val prefixHex = NOTIFICATION_PREFIX.joinToString("") { "%02x".format(it) }
            val dataHex = data.joinToString("") { "%02x".format(it) }
            return dataHex.startsWith(prefixHex)
        }

        fun setData(data: ByteArray) {
            status = data[9]
        }
    }
}

fun isHeadTrackingData(data: ByteArray): Boolean {
    if (data.size <= 60) return false

    val prefixPattern = byteArrayOf(
        0x04, 0x00, 0x04, 0x00, 0x17, 0x00, 0x00, 0x00,
        0x10, 0x00
    )

    for (i in prefixPattern.indices) {
        if (data[i] != prefixPattern[i]) return false
    }

    if (data[10] != 0x44.toByte() && data[10] != 0x45.toByte()) return false

    if (data[11] != 0x00.toByte()) return false

    return true
}
