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
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

/** Keep delete markers only in the new profile field, without changing legacy JSON. */
class PlayerHostProfileMapAdapter : TypeAdapter<Map<String, Map<String, Any?>>>() {
    private val gson = GsonBuilder().serializeNulls().create()
    private val type = object : TypeToken<Map<String, Map<String, Any?>>>() {}.type
    override fun write(output: JsonWriter, value: Map<String, Map<String, Any?>>?) {
        gson.toJson(value, type, output)
    }
    override fun read(input: JsonReader): Map<String, Map<String, Any?>> = gson.fromJson(input, type)
}
