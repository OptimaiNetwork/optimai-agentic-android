package com.test.agenttrade.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Small, forgiving readers over `JsonObject` — the server mixes decimal
 * strings ("223.95") with real JSON numbers in one payload, and omits or
 * nulls optional fields freely, so every reader accepts either form and
 * returns null instead of throwing.
 */
internal fun JsonObject.str(key: String): String? {
    val value = this[key] ?: return null
    if (value is JsonNull || value !is JsonPrimitive) return null
    return value.contentOrNull
}

internal fun JsonObject.req(key: String): String = str(key) ?: throw ApiDecodeException("Missing '$key'")

internal fun JsonObject.dbl(key: String): Double? = str(key)?.toDoubleOrNull()

internal fun JsonObject.reqDbl(key: String): Double = dbl(key) ?: throw ApiDecodeException("Expected a decimal at '$key'")

internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() }

internal fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonObject.instant(key: String): Instant? = str(key)?.let(::parseInstant)

internal fun JsonObject.strings(key: String): List<String> =
    arr(key)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

internal fun JsonObject.doubles(key: String): List<Double>? =
    arr(key)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() }

internal fun JsonArray.objects(): List<JsonObject> = mapNotNull { it as? JsonObject }

internal val JsonElement.asObject: JsonObject get() = this as? JsonObject ?: throw ApiDecodeException("Expected an object")

/**
 * Python's `datetime` serializes with fractional seconds and either "Z" or
 * "+00:00" — `Instant.parse` only takes the former, so fall back to the
 * offset-aware parser.
 */
internal fun parseInstant(text: String): Instant? =
    runCatching { Instant.parse(text) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()

class ApiDecodeException(message: String) : Exception(message)
