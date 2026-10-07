/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.core.playerhost

import com.google.gson.JsonParseException
import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.util.Collections
import kotlin.jvm.internal.DefaultConstructorMarker
import kotlin.math.abs

data class PlayerHostAnchor @JvmOverloads constructor(
    val bone: String,
    val locator: String? = null
)

data class PlayerHostOffset @JvmOverloads constructor(
    val position: List<Double> = listOf(0.0, 0.0, 0.0),
    val rotation: List<Double> = listOf(0.0, 0.0, 0.0),
    val scale: List<Double> = listOf(1.0, 1.0, 1.0)
)

data class PlayerHostBinding @JvmOverloads constructor(
    val mode: String = "mapped",
    @SerializedName("source_root") val sourceRoot: String = "",
    val anchor: PlayerHostAnchor? = null,
    @SerializedName("bone_map") val boneMap: Map<String, String> = emptyMap(),
    val offset: PlayerHostOffset = PlayerHostOffset()
)

data class PlayerHostHide @JvmOverloads constructor(
    val parts: List<String> = emptyList(),
    val bones: List<String> = emptyList()
)

data class PlayerHostCostume @JvmOverloads constructor(
    val model: String,
    val binding: PlayerHostBinding = PlayerHostBinding(),
    val hide: PlayerHostHide? = null,
    @SerializedName("render_roots")
    @field:JsonAdapter(PlayerHostRenderRootsAdapter::class)
    val renderRoots: List<String>? = null
) {
    /** Binary bridge for Kotlin callers compiled against the three-property DTO. */
    @Deprecated("Binary compatibility bridge", level = DeprecationLevel.HIDDEN)
    @Suppress("UNUSED_PARAMETER")
    constructor(model: String, binding: PlayerHostBinding?, hide: PlayerHostHide?, mask: Int,
        marker: DefaultConstructorMarker?) : this(
        model,
        if ((mask and 2) != 0) PlayerHostBinding() else requireNotNull(binding),
        if ((mask and 4) != 0) null else hide,
        null
    )

    fun copy(model: String, binding: PlayerHostBinding, hide: PlayerHostHide?): PlayerHostCostume =
        PlayerHostCostume(model, binding, hide, renderRoots)

    companion object {
        @JvmStatic
        @JvmName("copy\$default")
        @Deprecated("Binary compatibility bridge", level = DeprecationLevel.HIDDEN)
        @Suppress("UNUSED_PARAMETER")
        fun legacyCopyDefault(value: PlayerHostCostume, model: String?, binding: PlayerHostBinding?,
            hide: PlayerHostHide?, mask: Int, marker: Any?): PlayerHostCostume = PlayerHostCostume(
            if ((mask and 1) != 0) value.model else requireNotNull(model),
            if ((mask and 2) != 0) value.binding else requireNotNull(binding),
            if ((mask and 4) != 0) value.hide else hide,
            value.renderRoots
        )
    }
}

/** Keeps Gson from converting numbers or booleans into render-root names before validation. */
class PlayerHostRenderRootsAdapter : TypeAdapter<List<String>>() {
    override fun read(reader: JsonReader): List<String>? {
        if (reader.peek() == JsonToken.NULL) { reader.nextNull(); return null }
        if (reader.peek() != JsonToken.BEGIN_ARRAY) throw JsonParseException("render_roots 必须是字符串数组")
        val roots = ArrayList<String>()
        reader.beginArray()
        while (reader.hasNext()) {
            if (roots.size >= 4096) throw JsonParseException("render_roots 骨骼数量超过 4096")
            if (reader.peek() != JsonToken.STRING) throw JsonParseException("render_roots 中的骨骼名称必须是字符串")
            roots += reader.nextString()
        }
        reader.endArray()
        return roots
    }

    override fun write(writer: JsonWriter, roots: List<String>?) {
        if (roots == null) { writer.nullValue(); return }
        writer.beginArray()
        roots.forEach { writer.value(it) }
        writer.endArray()
    }
}

private fun validatedRenderRoots(values: List<*>?): List<String>? {
    if (values == null) return null
    require(values.size in 1..4096) { "render_roots 需要 1 至 4096 个骨骼名称" }
    val roots = values.map {
        require(it is String) { "render_roots 中的骨骼名称必须是字符串" }
        HostProfileValidation.requireName(it)
        it
    }
    return Collections.unmodifiableList(ArrayList(roots))
}

data class LegacyCostumeEntry(val model: String, val hide: Boolean)

data class LegacyCostumeState @JvmOverloads constructor(
    val suit: String? = null,
    val suitHide: Boolean = false,
    val slots: Map<String, LegacyCostumeEntry> = emptyMap()
)

data class PlayerAppearance @JvmOverloads constructor(
    val model: String = "",
    val scale: Double = 1.0,
    val variant: String = "DEFAULT_BBMODEL",
    @SerializedName("profile_id") val profileId: String = "",
    @SerializedName("pack_id") val packId: String = "",
    val slots: Map<String, PlayerHostCostume> = emptyMap(),
    @SerializedName("legacy_costume") val legacyCostume: LegacyCostumeState = LegacyCostumeState()
) {
    /** True for an explicitly selected non-built-in player host, not an ordinary body-only model. */
    fun isCustomHost(): Boolean = variant == "DEFAULT_BBMODEL" &&
        (profileId.isNotEmpty() || (model.isNotEmpty() && model != "__default_player__"))

    /** Copies every collection before an event, state commit or asynchronous serialization. */
    fun validated(): PlayerAppearance {
        require(scale.isFinite() && scale > 0.0 && scale <= 1024.0) { "玩家模型缩放必须大于零且不超过 1024" }
        require(variant == "DEFAULT_BBMODEL" || variant == "CUSTOM_BBMODEL") { "玩家模型变体无效" }
        require(model.length <= 256 && packId.length <= 256 && '\u0000' !in model && '\u0000' !in packId) { "模型或动画包 ID 无效" }
        if (profileId.isNotEmpty()) HostProfileValidation.requireId(profileId)
        require(model.isNotBlank() || (slots.isEmpty() && profileId.isEmpty())) { "动态时装需要先设置宿主模型" }
        require(slots.size <= 64) { "时装槽位过多" }
        val frozenSlots = slots.mapValues { (id, costume) ->
            HostProfileValidation.requireId(id)
            HostProfileValidation.requireName(costume.model)
            val binding = costume.binding
            require(binding.mode == "attach" || binding.mode == "mapped") { "时装绑定模式必须是 attach 或 mapped" }
            binding.anchor?.let { HostProfileValidation.requireName(it.bone); it.locator?.let(HostProfileValidation::requireName) }
            if (binding.mode == "attach") {
                HostProfileValidation.requireName(binding.sourceRoot)
                require(binding.anchor != null && binding.anchor.bone.isNotBlank()) { "attach 时装需要宿主骨骼" }
            }
            require(binding.boneMap.size <= 4096) { "时装骨骼映射过多" }
            binding.boneMap.forEach { (source, target) ->
                HostProfileValidation.requireName(source)
                HostProfileValidation.requireName(target)
            }
            val offset = binding.offset
            fun vector(values: List<Double>, invertible: Boolean = false): List<Double> {
                require(values.size == 3 && values.all { it.isFinite() && abs(it) <= 1000000.0 && (!invertible || abs(it) >= 0.000001) }) {
                    "时装偏移需要三个有效数，缩放绝对值不能小于 0.000001"
                }
                return Collections.unmodifiableList(ArrayList(values))
            }
            val hide = costume.hide
            hide?.let { HostProfileValidation.requireHide(it.parts, it.bones) }
            costume.copy(
                binding = binding.copy(
                    boneMap = Collections.unmodifiableMap(LinkedHashMap(binding.boneMap)),
                    offset = offset.copy(vector(offset.position), vector(offset.rotation), vector(offset.scale, true))
                ),
                hide = hide?.copy(
                    parts = Collections.unmodifiableList(ArrayList(hide.parts)),
                    bones = Collections.unmodifiableList(ArrayList(hide.bones))
                ),
                renderRoots = validatedRenderRoots(costume.renderRoots)
            )
        }
        val legacy = legacyCostume
        require(legacy.slots.keys.all { it in LEGACY_SLOTS }) { "旧时装槽位无效" }
        legacy.slots.values.forEach { HostProfileValidation.requireName(it.model) }
        legacy.suit?.let { if (it.isNotEmpty()) HostProfileValidation.requireName(it) }
        require(legacy.suit.isNullOrEmpty() || legacy.slots.isEmpty()) { "套装和分槽时装不能同时装备" }
        return copy(
            slots = Collections.unmodifiableMap(LinkedHashMap(frozenSlots)),
            legacyCostume = legacy.copy(slots = Collections.unmodifiableMap(LinkedHashMap(legacy.slots)))
        )
    }

    companion object {
        @JvmField val LEGACY_SLOTS = setOf("HEAD", "BODY", "LEGS", "FEET", "DECORATION")
    }
}

/** Missing capability lists mean an old client. Malformed entries are rejected before DH work. */
object PlayerHostCapabilities {
    const val CUSTOM_PLAYER_HOST = "custom_player_host_v1"
    const val COSTUME_RENDER_ROOTS = "custom_costume_render_roots_v1"
    private val pattern = Regex("[a-z][a-z0-9_.-]{0,63}")

    @JvmStatic fun parse(values: List<String?>?): Set<String>? {
        if (values == null) return emptySet()
        if (values.size > 64 || values.any { it == null || !pattern.matches(it) }) return null
        return Collections.unmodifiableSet(LinkedHashSet(values.filterNotNull()))
    }
}

/** Old host-capable receivers must not draw a selected subtree as the whole costume. */
internal fun PlayerAppearance.forRenderRootsCapability(supported: Boolean): PlayerAppearance {
    if (supported || slots.values.none { it.renderRoots != null }) return this
    val compatibleSlots = LinkedHashMap<String, PlayerHostCostume>()
    slots.forEach { (id, costume) -> if (costume.renderRoots == null) compatibleSlots[id] = costume }
    return copy(slots = Collections.unmodifiableMap(compatibleSlots))
}

/** A short-lived builder. updateAppearance commits only after this builder has returned. */
class PlayerAppearanceBuilder internal constructor(appearance: PlayerAppearance) {
    internal var hostSelectionChanged = false
        private set
    var model = appearance.model
        set(value) { field = value; hostSelectionChanged = true }
    var scale = appearance.scale
    var variant = appearance.variant
    var profileId = appearance.profileId
        set(value) { field = value; hostSelectionChanged = true }
    var packId = appearance.packId
    var legacyCostume = appearance.legacyCostume
    val slots: MutableMap<String, PlayerHostCostume> = LinkedHashMap(appearance.slots)

    fun build(): PlayerAppearance = PlayerAppearance(
        model, scale, variant, profileId, packId, slots, legacyCostume
    ).validated()
}

/** Server-only intent: using a legacy setter never opts an old client into the new host mode. */
internal data class LegacyAppearanceProjection(
    val bodyOnly: Boolean,
    val variant: String,
    val costume: LegacyCostumeState,
    val packId: String
)

internal fun PlayerAppearance.legacyProjection(explicitHostSelection: Boolean): LegacyAppearanceProjection {
    val bodyOnly = isCustomHost() && (explicitHostSelection || profileId.isNotEmpty())
    return LegacyAppearanceProjection(bodyOnly, if (bodyOnly) "CUSTOM_BBMODEL" else variant,
        if (bodyOnly) LegacyCostumeState() else legacyCostume, if (bodyOnly) "" else packId)
}

/** Body identity changes supply a default, never override an explicitly requested false. */
internal fun legacyAppearanceModelReset(requested: Boolean?, hasPrevious: Boolean, modelChanged: Boolean): Boolean =
    requested ?: (!hasPrevious || modelChanged)
