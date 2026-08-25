/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.hook.mythicmobs.hitbox

import org.bukkit.entity.Entity
import org.bukkit.event.Listener
import priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager
import priv.seventeen.artist.arcartx.nms.EntityBridge
import priv.seventeen.artist.asteroid.entity.CustomEntity
import priv.seventeen.artist.asteroid.entity.ability.*
import priv.seventeen.artist.asteroid.entity.ability.MountAbility.MountType
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/** 交互代理管理器 */
object InteractionProxyManager : Listener {

    private val proxyEntities: MutableMap<UUID, CustomEntity> = ConcurrentHashMap()
    private val proxyToOwner: MutableMap<UUID, UUID> = ConcurrentHashMap()

    fun createProxy(owner: Entity, width: Double, height: Double): Entity {
        removeProxy(owner)

        val proxy = EntityBridge.createCustomEntity(owner, width, height)

        proxy.apply {
            // Owner-backed custom entities only remember their owner at creation;
            // the FollowOwner marker is what makes Asteroid synchronize position.
            // Asteroid suspends this branch automatically while mountType != NONE.
            EntityBridge.addFollowOwner(proxy, removeOnOwnerInvalid = true)
            EntityBridge.addHitbox(proxy, width, height)

            EntityBridge.addResize(proxy) { w, h ->
                ArcartXEntityManager.getOrCreateEntity(proxy.bukkitEntity)?.setSize(w, h)
            }

            EntityBridge.addRemove(proxy) {
                removeProxy(proxy.owner!!)
            }

            proxy.bukkitEntity.customName = "<arcartx_proxy>"
            ArcartXEntityManager.getOrCreateEntity(proxy.bukkitEntity)?.setSize(width, height)
        }

        proxyEntities[owner.uniqueId] = proxy
        proxyToOwner[proxy.uniqueId] = owner.uniqueId

        return proxy.bukkitEntity
    }

    fun removeProxy(owner: Entity) {
        val proxy = proxyEntities.remove(owner.uniqueId)
        if (proxy != null) {
            proxyToOwner.remove(proxy.uniqueId)
            proxy.remove()
            ArcartXEntityManager.removeEntity(proxy.bukkitEntity)
        }
    }

    fun getProxy(owner: Entity): CustomEntity? {
        return proxyEntities[owner.uniqueId]
    }

    fun getOwner(proxy: Entity): Entity? {
        val ownerUUID = proxyToOwner[proxy.uniqueId] ?: return null
        return proxyEntities[ownerUUID]?.owner
    }

    fun getProxyFromEntity(proxy: Entity): CustomEntity? {
        val ownerUUID = proxyToOwner[proxy.uniqueId] ?: return null
        return proxyEntities[ownerUUID]
    }

    fun getOwner(proxy: UUID): Entity? {
        val ownerUUID = proxyToOwner[proxy] ?: return null
        return proxyEntities[ownerUUID]?.owner
    }

    fun isOwner(ownerUUID: UUID): Boolean {
        return proxyEntities[ownerUUID] != null
    }

    fun isProxy(entity: Entity): Boolean {
        return proxyToOwner.containsKey(entity.uniqueId)
    }

    fun isProxy(entity: UUID): Boolean {
        return proxyToOwner.containsKey(entity)
    }

    fun hasProxy(entity: Entity): Boolean {
        return proxyEntities.containsKey(entity.uniqueId)
    }

    fun updateProxySize(owner: Entity, width: Double, height: Double) {
        val proxy = proxyEntities[owner.uniqueId] ?: return
        val hitbox = proxy.getAbility(HitboxAbility::class.java)
        if (hitbox != null) {
            hitbox.setSize(proxy, width, height)
        } else {
            EntityBridge.addHitbox(proxy, width, height)
        }
    }

    fun setProxyMountType(owner: Entity, type: String) {
        val proxy = proxyEntities[owner.uniqueId] ?: return
        val t = when (type.uppercase(Locale.ROOT)) {
            "GROUND" -> MountType.GROUND
            "FLY" -> MountType.FLY
            "BOAT" -> MountType.BOAT
            "CAR" -> MountType.CAR
            "DIVING" -> MountType.DIVING
            else -> MountType.NONE
        }
        if (t != MountType.NONE) {
            EntityBridge.addInteract(proxy) { entity, hand ->
                if (hand) proxy.nmsAddPassenger(entity)
            }
            EntityBridge.addMount(proxy, t)
        } else {
            proxy.removeAbility(InteractAbility::class.java)
            proxy.removeAbility(MountAbility::class.java)
        }
    }

    fun setMountSpeed(owner: Entity, moveSpeed: Float, flyUpSpeed: Float, flyDownSpeed: Float, boatTurnSpeed: Float) {
        val proxy = proxyEntities[owner.uniqueId] ?: return
        val mountAbility = proxy.getAbility(MountAbility::class.java) ?: return
        mountAbility.setSpeed(proxy, moveSpeed, flyUpSpeed, flyDownSpeed, boatTurnSpeed)
    }

    fun addSeat(owner: Entity, x: Double, y: Double, z: Double) {
        val proxy = proxyEntities[owner.uniqueId] ?: return
        EntityBridge.addSeatOffset(proxy, x, y, z)
    }

    fun setDamageHandler(owner: Entity, callback: DamageAbility.Callback) {
        val proxy = proxyEntities[owner.uniqueId] ?: return
        EntityBridge.addDamageHandler(proxy, callback)
    }
}
