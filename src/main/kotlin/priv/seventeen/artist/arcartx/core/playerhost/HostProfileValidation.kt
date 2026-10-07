/*
 * Copyright (c) 2026 17Artist
 *
 * This file is part of ArcartX, licensed under the ArcartX Source-Available
 * License 1.0. Use of this software requires acceptance of the ArcartX EULA:
 * https://arcartx.com/resources/eula/view
 * See the LICENSE file for full terms. Provided "AS IS", without warranty.
 */

package priv.seventeen.artist.arcartx.core.playerhost

import com.google.gson.GsonBuilder
import java.util.Collections

/** Validates sparse server overrides while preserving explicit runtime-map deletions. */
object HostProfileValidation {
    private val idPattern = Regex("[a-z][a-z0-9_.-]{0,63}")
    private val fields = setOf("schema", "model", "parts", "semantic_bones", "locators", "slots")
    private val bodyParts = setOf("HEAD", "BODY", "LEGS", "FEET", "OTHER")
    private val locatorKeys = setOf("left_hand", "right_hand", "elytra", "elytra_left", "elytra_right")
    private val gson = GsonBuilder().serializeNulls().create()

    @JvmStatic fun requireId(id: String) {
        require(idPattern.matches(id)) { "ID 必须以小写字母开头，最多 64 位，仅使用小写字母、数字、点、下划线和短横线" }
    }

    @JvmStatic fun requireName(name: String) {
        require(name.isNotBlank() && name.length <= 256 && '\u0000' !in name) { "模型、骨骼或定位器名称必须为 1 至 256 位非空文本" }
    }

    @JvmStatic fun requireHide(parts: List<String>, bones: List<String>) {
        require(parts.size <= 4096 && bones.size <= 4096) { "隐藏部位或骨骼数量过多" }
        require(parts.all { it in bodyParts }) { "隐藏部位仅支持 HEAD、BODY、LEGS、FEET、OTHER" }
        bones.forEach(::requireName)
    }

    @JvmStatic fun validate(id: String, patch: Map<String, Any?>): Map<String, Any?> {
        requireId(id)
        require(patch.keys.all { it in fields }) { "宿主配置包含未知字段" }
        require(patch["model"] is String) { "宿主配置需要 model" }
        requireName(patch["model"] as String)
        require(gson.toJson(patch).length <= 524288) { "宿主配置过大" }
        val active = withoutDeletes(patch)
        active["schema"]?.let { require(it is Number && it.toDouble() == 1.0) { "宿主配置 schema 必须是 1" } }
        fun strings(value: Any?): List<String> {
            require(value is List<*> && value.size <= 4096 && value.all { it is String }) { "骨骼或分部列表无效" }
            return value.filterIsInstance<String>().onEach(::requireName)
        }
        fun map(value: Any?, maximum: Int = 4096): Map<*, *> {
            require(value is Map<*, *> && value.size <= maximum && value.keys.all { it is String }) { "宿主配置需要字符串键映射，且数量不能超过 $maximum" }
            return value
        }
        val assigned = HashSet<String>()
        if (active.containsKey("parts")) map(active["parts"]).forEach { (key, value) ->
            require(key in bodyParts && key != "OTHER") { "分部仅支持 HEAD、BODY、LEGS、FEET" }
            strings(value).forEach { require(assigned.add(it)) { "骨骼不能重复归属: $it" } }
        }
        if (active.containsKey("semantic_bones")) map(active["semantic_bones"]).forEach { (key, value) ->
            requireName(key as String)
            require(value is String) { "语义骨骼名称无效" }
            requireName(value)
        }
        if (active.containsKey("locators")) {
            val locators = map(active["locators"], 32)
            require(!(locators.containsKey("elytra") && (locators.containsKey("elytra_left") || locators.containsKey("elytra_right")))) { "整体鞘翅定位器不能与双翼定位器混用" }
            locators.forEach { (key, value) ->
                require(key in locatorKeys) { "未知附件定位器: $key" }
                val locator = map(value)
                require(locator.keys.all { it == "bone" || it == "locator" }) { "定位器配置包含未知字段" }
                require(locator["bone"] is String && locator["locator"] is String) { "定位器需要骨骼和 locator 名称" }
                requireName(locator["bone"] as String)
                requireName(locator["locator"] as String)
            }
        }
        if (active.containsKey("slots")) map(active["slots"], 64).forEach { (key, value) ->
            requireId(key as String)
            val slot = map(value)
            require(slot.keys.all { it in setOf("label", "order", "default_hide") }) { "时装槽位配置包含未知字段" }
            if (slot.containsKey("label")) require(slot["label"] is String && (slot["label"] as String).length <= 128) { "槽位名称不能超过 128 位" }
            if (slot.containsKey("order")) require(slot["order"] is Number && (slot["order"] as Number).toDouble().let { it.isFinite() && it % 1.0 == 0.0 && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE }) { "槽位排序必须是整数" }
            if (slot.containsKey("default_hide")) {
                val hide = map(slot["default_hide"])
                require(hide.keys.all { it == "parts" || it == "bones" }) { "隐藏配置包含未知字段" }
                requireHide(if (hide.containsKey("parts")) strings(hide["parts"]) else emptyList(),
                    if (hide.containsKey("bones")) strings(hide["bones"]) else emptyList())
            }
        }
        @Suppress("UNCHECKED_CAST")
        return freeze(patch) as Map<String, Any?>
    }

    private fun withoutDeletes(value: Map<String, Any?>): Map<String, Any?> = value.filterValues { it != null }.mapValues { (_, item) ->
        if (item is Map<*, *>) {
            require(item.keys.all { it is String }) { "宿主配置需要字符串键映射" }
            @Suppress("UNCHECKED_CAST")
            withoutDeletes(item as Map<String, Any?>)
        } else item
    }

    private fun freeze(value: Any?): Any? = when (value) {
        is Map<*, *> -> Collections.unmodifiableMap(value.entries.associateTo(LinkedHashMap()) { it.key to freeze(it.value) })
        is List<*> -> Collections.unmodifiableList(value.map(::freeze))
        else -> value
    }
}
