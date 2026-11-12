package io.xbot.tdlib.client

import io.xbot.tdlib.api.TdJson
import io.xbot.tdlib.api.TdObject
import io.xbot.tdlib.api.anyObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlin.jvm.JvmInline

// What TdLib.receive returns: an update or an answer. TDLib appends "@extra" and then "@client_id" to the object
// (td/telegram/ClientJson.cpp), so both are read off the event's end and it is decoded straight from its string;
// an event that carries them elsewhere is parsed whole for them.
@JvmInline
internal value class TdEvent(private val json: String) {
    val extra: Long?
        get() {
            val end = json.lastIndexOf(TRAILING_CLIENT_ID).takeIf { it >= 0 } ?: (json.length - 1)
            val at = json.indexOf(TRAILING_EXTRA, (end - TRAILING_EXTRA.length - MAX_LONG_LENGTH).coerceAtLeast(0))
            return if (at < 0 || at >= end) null else json.substring(at + TRAILING_EXTRA.length, end).toLongOrNull()
        }

    val clientId: Int?
        get() {
            val at = json.lastIndexOf(TRAILING_CLIENT_ID)
            return if (at < 0) null else json.substring(at + TRAILING_CLIENT_ID.length, json.length - 1).toIntOrNull()
        }

    val parsedExtra: Long? get() = parsed(EXTRA)?.longOrNull

    val parsedClientId: Int? get() = parsed(CLIENT_ID)?.intOrNull

    fun decode(): TdObject = TdJson.decodeFromString(anyObject, json)

    private fun parsed(field: String): JsonPrimitive? =
        TdJson.parseToJsonElement(json).jsonObject[field] as? JsonPrimitive
}

/** A request as TDLib takes it: with the "@extra" its answer comes back with, at the end as TDLib itself writes it. */
internal fun String.withExtra(extra: Long): String = "${dropLast(1)}$TRAILING_EXTRA$extra}"

private const val EXTRA = "@extra"
private const val CLIENT_ID = "@client_id"
private const val TRAILING_EXTRA = ",\"$EXTRA\":"
private const val TRAILING_CLIENT_ID = ",\"$CLIENT_ID\":"
private const val MAX_LONG_LENGTH = 20
