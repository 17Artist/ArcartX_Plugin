/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.network.packet.client

import com.google.gson.annotations.SerializedName
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.arcartx.ArcartX
import priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager
import priv.seventeen.artist.arcartx.core.entity.data.ArcartXPlayer
import priv.seventeen.artist.arcartx.event.client.ClientExtraSlotClickEvent
import priv.seventeen.artist.arcartx.network.packet.client.CPackClickSlot.SlotClickType.*
import priv.seventeen.artist.arcartx.script.ScriptManager
import kotlin.math.min

/** 额外槽位点击包，处理物品交互逻辑 */
class CPackClickSlot() : ClientPacket {

    @SerializedName("slotName")
    var slotName: String? = null

    @SerializedName("buttonID")
    var buttonID: Int = 0


    override fun handle(player: Player) {
        if (slotName == null) {
            return
        }
        handlePlayerClick(player, slotName ?: return, buttonID)
    }

    override val isAsync = false

    private fun handlePlayerClick(player: Player, slotName: String, buttonID: Int) {
        if (!ClientExtraSlotClickEvent(player, slotName, buttonID).call()) {
            return
        }
        if (!ArcartX.configs.slotFolder.setting.containsKey(slotName)) {
            return
        }
        ArcartX.configs.slotFolder.setting[slotName]?.limitScriptArgs?.forEach {
            if(!ScriptManager.executeScript(it.first,player,player.itemOnCursor,it.second)){
                return
            }
        }
        val aPlayer : ArcartXPlayer?  = ArcartXEntityManager.getPlayer(player);
        val mouseItemStack: ItemStack = player.itemOnCursor.clone()
        val slotItemStack: ItemStack = aPlayer?.getSlotItemStack(slotName) ?: ItemStack(Material.AIR)
        val clickType = getClickType(buttonID, mouseItemStack, slotItemStack)
        when (clickType) {
            CLONE -> {
                // 复制到鼠标
                if (player.isOp && player.gameMode == GameMode.CREATIVE) {
                    player.setItemOnCursor(slotItemStack.clone())
                }
            }

            PUT -> {
                val amount = slotItemStack.maxStackSize - slotItemStack.amount // 堆叠剩余空间
                val addAmount =
                    min(amount.toDouble(), mouseItemStack.amount.toDouble()).toInt() // 将要放入的数量
                if (addAmount > 0) {
                    // 新数据
                    slotItemStack.amount += addAmount
                    // 写入数据库
                    var sendBack = mouseItemStack
                    if (sendBack.amount - addAmount == 0) {
                        sendBack = ItemStack(Material.AIR)
                    } else {
                        sendBack.amount -= addAmount
                    }
                    player.setItemOnCursor(sendBack)
                    aPlayer?.setSlotItemStack(slotName, slotItemStack)
                }
            }

            PUT_ONE -> {
                val newItem = mouseItemStack.clone()
                newItem.amount = 1
                var sendBack = mouseItemStack
                if (sendBack.amount - 1 == 0) {
                    sendBack = ItemStack(Material.AIR)
                } else {
                    sendBack.amount -= 1
                }
                player.setItemOnCursor(sendBack)
                aPlayer?.setSlotItemStack(slotName, newItem)
            }

            REPLACE -> {
                player.setItemOnCursor(slotItemStack)
                aPlayer?.setSlotItemStack(slotName, mouseItemStack)
            }

            GET_HALF -> {
                val amount1 = slotItemStack.amount / 2 //amount 为取出的数量
                slotItemStack.amount -= amount1

                val sendBack = slotItemStack.clone()
                sendBack.amount = amount1

                player.setItemOnCursor(sendBack)
                aPlayer?.setSlotItemStack(slotName, slotItemStack)
            }

            ADD_ONE -> {
                // 新数据
                slotItemStack.amount += 1

                var sendBack = mouseItemStack
                if (sendBack.amount - 1 == 0) {
                    sendBack = ItemStack(Material.AIR)
                } else {
                    sendBack.amount -= 1
                }
                player.setItemOnCursor(sendBack)
                aPlayer?.setSlotItemStack(slotName, slotItemStack)
            }

            NONE -> {

            }
        }
    }

    private fun getClickType(buttonID: Int, mouseItemStack: ItemStack, slotItemStack: ItemStack): SlotClickType {
        // 操作类型
        var operationType = NONE


        // 鼠标中键 > 复制到鼠标 不改变数据 返回退回物品和更新物品相同
        if (buttonID == 2) {
            if (slotItemStack.type != Material.AIR && mouseItemStack.type == Material.AIR) {
                operationType = CLONE
            }
        }
        // 鼠标左键 > 全量替换 或者放入
        if (buttonID == 0) {
            //相同物品则把手上物品放入
            if (slotItemStack.type != Material.AIR && mouseItemStack.type != Material.AIR) {
                operationType = if (slotItemStack.isSimilar(mouseItemStack)) {
                    PUT
                } else {
                    // 替换
                    REPLACE
                }
            } else if (mouseItemStack.type != Material.AIR || slotItemStack.type != Material.AIR) {
                operationType = REPLACE
            }
        }
        //右键点击槽位
        if (buttonID == 1) {
            //如果格子是空的，且手上有物品  ->  放置一个进去
            if (slotItemStack.type == Material.AIR && mouseItemStack.type != Material.AIR) {
                operationType = PUT_ONE
            }
            //如果格子不是空的，手上是空的  ->  取出格子的一半物品到手上
            if (slotItemStack.type != Material.AIR && mouseItemStack.type == Material.AIR) {
                operationType = if (slotItemStack.amount == 1) {
                    //如果格子只有一个物品，直接交换物品即可
                    REPLACE
                } else {
                    // 取出1/2
                    GET_HALF
                }
            }
            //如果格子不是空的，手上也不是空的  ->  判断格子与手上物品是否相同，且数量还未满  若满足则放置一个物品到格子内
            if (slotItemStack.type != Material.AIR && mouseItemStack.type != Material.AIR) {
                if (slotItemStack.amount < slotItemStack.maxStackSize) {
                    operationType = if (slotItemStack.isSimilar(mouseItemStack)) {
                        // 槽位 +1 退回-1
                        ADD_ONE
                    } else {
                        REPLACE
                    }
                }
            }
        }
        return operationType
    }


    private enum class SlotClickType {
        CLONE, PUT, REPLACE, PUT_ONE, GET_HALF, ADD_ONE, NONE
    }

}
