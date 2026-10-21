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

@file:OptIn(ExperimentalEncodingApi::class)

package me.kavishdevar.librepods.presentation.components

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
fun BatteryView(batteryList: List<Battery>, budsRes: Int, caseRes: Int) {
    val left = batteryList.find { it.component == BatteryComponent.LEFT }
    val right = batteryList.find { it.component == BatteryComponent.RIGHT }
    val case = batteryList.find { it.component == BatteryComponent.CASE }
    val leftLevel = left?.level ?: -1
    val rightLevel = right?.level ?: -1
    val combined = leftLevel in 0..100 && rightLevel in 0..100 &&
        left?.estimated == right?.estimated && left?.status == right?.status && (leftLevel - rightLevel) in -3..3
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(Modifier.widthIn(max = 500.dp), horizontalArrangement = Arrangement.Center) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(ImageBitmap.imageResource(budsRes), stringResource(R.string.buds),
                    Modifier.fillMaxWidth().padding(8.dp))
                if (combined) {
                    BatteryIndicator(minOf(leftLevel,rightLevel),left?.status ?: BatteryStatus.DISCONNECTED,estimated=left?.estimated==true)
                } else {
                    Row(horizontalArrangement = Arrangement.Center) {
                        BatteryIndicator(leftLevel,left?.status ?: BatteryStatus.DISCONNECTED,"L",estimated=left?.estimated==true)
                        Spacer(Modifier.width(16.dp))
                        BatteryIndicator(rightLevel,right?.status ?: BatteryStatus.DISCONNECTED,"R",estimated=right?.estimated==true)
                    }
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(ImageBitmap.imageResource(caseRes), stringResource(R.string.case_alt),
                    Modifier.fillMaxWidth().padding(8.dp))
                BatteryIndicator(case?.level ?: -1,case?.status ?: BatteryStatus.DISCONNECTED,estimated=case?.estimated==true)
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BatteryViewPreview() {
    val fakeBattery = listOf(
        Battery(BatteryComponent.LEFT, 85, BatteryStatus.CHARGING),
        Battery(BatteryComponent.RIGHT, 40, BatteryStatus.OPTIMIZED_CHARGING),
        Battery(BatteryComponent.CASE, 60, BatteryStatus.NOT_CHARGING)
    )

    val bg = if (isSystemInDarkTheme()) Color.Black else Color(0xFFF2F2F7)
    LibrePodsTheme(m3eEnabled = false) {
        Box(
            modifier = Modifier
                .background(bg)
                .padding(16.dp)
        ) {
            BatteryView(
                batteryList = fakeBattery,
                budsRes = R.drawable.airpods_5_wireless_buds,
                caseRes = R.drawable.airpods_5_wireless_case
            )
        }
    }
}
