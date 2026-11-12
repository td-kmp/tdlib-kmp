package io.xbot.tdlib.client

import io.xbot.tdlib.TdLib
import io.xbot.tdlib.TdLibUnavailableException
import io.xbot.tdlib.api.AuthorizationStateClosed
import io.xbot.tdlib.api.TdFunction
import io.xbot.tdlib.api.TdJson
import io.xbot.tdlib.api.TdObject
import io.xbot.tdlib.api.Update
import io.xbot.tdlib.api.UpdateAuthorizationState
import io.xbot.tdlib.api.anyFunction
import io.xbot.tdlib.api.anyObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Duration.Companion.seconds

// TdLib.receive serves every client of a TdLib and allows one receiver, so one loop per TdLib
// dispatches all its events: answers by "@extra", updates by "@client_id".
internal class TdReceiver private constructor(val tdLib: TdLib) {
    private val mutex = Mutex()
    private val clients = mutableMapOf<Int, TdClient>()
    private val pending = mutableMapOf<Long, CompletableDeferred<String>>()
    private var nextExtra = 1L
    private var running = false

    suspend fun register(client: TdClient) {
        val start = mutex.withLock {
            clients[client.id] = client
            !running.also { running = true }
        }
        if (start) startLoop()
    }

    suspend fun request(client: TdClient, function: TdFunction<*>): TdObject {
        val answer = CompletableDeferred<String>()
        val request = TdJson.encodeToString(anyFunction, function).dropLast(1)
        var extra = 0L
        try {
            // Sent under the lock, so requests reach TDLib in the order they were made.
            mutex.withLock {
                extra = nextExtra++
                pending[extra] = answer
                tdLib.send(client.id, "$request,\"@extra\":$extra}")
            }
            // Decoded here rather than in the receive loop, and never on the caller's main thread.
            return withContext(Dispatchers.Default) { TdJson.decodeFromString(anyObject, answer.await()) }
        } finally {
            if (!answer.isCompleted) withContext(NonCancellable) { mutex.withLock { pending.remove(extra) } }
        }
    }

    private fun startLoop() {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                while (true) {
                    val event = tdLib.receive(1.seconds)
                    if (event != null) {
                        runCatching { dispatch(event) }
                    } else if (mutex.withLock { clients.isEmpty().also { if (it) running = false } }) {
                        break
                    }
                }
            } catch (e: TdLibUnavailableException) {
                fail(e)
            }
        }
    }

    private suspend fun dispatch(event: String) {
        val extra = event.trailingExtra()
        if (extra != null) return answer(extra, event)
        val update = runCatching { TdJson.decodeFromString(anyObject, event) }.getOrNull() as? Update
        if (update == null) {
            TdJson.parseToJsonElement(event).jsonObject["@extra"]?.jsonPrimitive?.longOrNull?.let { answer(it, event) }
            return
        }
        val clientId = event.trailingClientId()
            ?: TdJson.parseToJsonElement(event).jsonObject["@client_id"]?.jsonPrimitive?.intOrNull
            ?: return
        val client = mutex.withLock { clients[clientId] } ?: return
        client.onUpdate(update)
        if (update is UpdateAuthorizationState && update.authorizationState is AuthorizationStateClosed) {
            mutex.withLock { clients.remove(clientId) }
        }
    }

    private suspend fun answer(extra: Long, event: String) {
        mutex.withLock { pending.remove(extra) }?.complete(event)
    }

    private suspend fun fail(cause: TdLibUnavailableException) {
        val (answers, orphans) = mutex.withLock {
            running = false
            val answers = pending.values.toList().also { pending.clear() }
            val orphans = clients.values.toList().also { clients.clear() }
            answers to orphans
        }
        answers.forEach { it.completeExceptionally(cause) }
        orphans.forEach { it.onUpdate(UpdateAuthorizationState(AuthorizationStateClosed)) }
    }

    companion object {
        private val mutex = Mutex()
        private val receivers = HashMap<TdLib, TdReceiver>()

        suspend fun of(tdLib: TdLib): TdReceiver = mutex.withLock { receivers.getOrPut(tdLib) { TdReceiver(tdLib) } }
    }
}

// TDLib appends "@extra" and then "@client_id" to the object it returns (td/telegram/ClientJson.cpp), so both are
// read off the end and the event is decoded straight from the string; an event with them elsewhere is parsed whole.
private const val CLIENT_ID = ",\"@client_id\":"
private const val EXTRA = ",\"@extra\":"
private const val MAX_LONG_LENGTH = 20

internal fun String.trailingClientId(): Int? {
    val at = lastIndexOf(CLIENT_ID)
    return if (at < 0) null else substring(at + CLIENT_ID.length, length - 1).toIntOrNull()
}

internal fun String.trailingExtra(): Long? {
    val end = lastIndexOf(CLIENT_ID).takeIf { it >= 0 } ?: (length - 1)
    val at = indexOf(EXTRA, (end - EXTRA.length - MAX_LONG_LENGTH).coerceAtLeast(0))
    return if (at < 0 || at >= end) null else substring(at + EXTRA.length, end).toLongOrNull()
}
