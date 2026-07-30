/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.commons.link.economy

import org.bukkit.entity.Player
import priv.seventeen.artist.arcartx.ArcartX
import priv.seventeen.artist.rondo.api.RondoAPI
import java.math.BigDecimal

class RondoEconomyProvider(private val identifier: String) : EconomyProvider {

    override fun getIdentifier(): String {
        return identifier
    }

    override fun getDisplayName(): String {
        return ArcartX.configs.economySetting.root[identifier]
            ?: RondoAPI.getCurrency(identifier)?.displayName
            ?: identifier
    }

    override fun addEconomy(player: Player, amount: Double): Boolean {
        val rondoAmount = amount.toRondoAmount() ?: return false
        return RondoAPI.deposit(
            player.uniqueId,
            identifier,
            rondoAmount,
            TRANSACTION_SOURCE
        )
    }

    override fun getEconomy(player: Player): Double {
        return RondoAPI.getBalance(player.uniqueId, identifier).toDouble()
    }

    override fun takeEconomy(player: Player, amount: Double): Boolean {
        val rondoAmount = amount.toRondoAmount() ?: return false
        return RondoAPI.withdraw(
            player.uniqueId,
            identifier,
            rondoAmount,
            TRANSACTION_SOURCE
        )
    }

    private fun Double.toRondoAmount(): BigDecimal? {
        return if (isFinite()) BigDecimal.valueOf(this) else null
    }

    private companion object {
        private const val TRANSACTION_SOURCE = "arcartx:link"
    }
}
