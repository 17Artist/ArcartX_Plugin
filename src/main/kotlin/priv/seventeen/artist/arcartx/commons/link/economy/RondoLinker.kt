/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.commons.link.economy

import priv.seventeen.artist.arcartx.commons.link.ArcartXLinkManager
import priv.seventeen.artist.rondo.api.RondoAPI

internal object RondoLinker {

    fun registerEconomyProviders(): Int {
        val currencyIds = RondoAPI.getAllCurrencyIds().sorted()
        currencyIds.forEach { currencyId ->
            ArcartXLinkManager.registerEconomyProvider(RondoEconomyProvider(currencyId))
        }
        return currencyIds.size
    }
}
