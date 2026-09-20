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

package me.kavishdevar.librepods.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.kavishdevar.librepods.R

class FOSSBillingProvider(context: Context): BillingProvider {
    // All advanced features are available immediately in this fork's FOSS builds.
    // Start enabled so observers never disable device settings during initialization.
    override val isPremium: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    private val _price = MutableStateFlow(context.getString(R.string.name_your_own_price))
    override val price: StateFlow<String> = _price

    override fun purchase(activity: Activity) {
        // Supporting the upstream project is optional and does not gate features.
        activity.startActivity(
            Intent(Intent.ACTION_VIEW, "https://github.com/sponsors/kavishdevar".toUri())
        )
    }

    override fun queryPurchases() = Unit

    override fun restorePurchases() = Unit
}
