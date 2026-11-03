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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    private val pending = mutableMapOf<Long, CompletableDeferred<TdObject>>()
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
        val answer = CompletableDeferred<TdObject>()
        val extra = mutex.withLock { nextExtra++.also { pending[it] = answer } }
        val json = TdJson.encodeToJsonElement(anyFunction, function).jsonObject
        try {
            tdLib.send(client.id, JsonObject(json + ("@extra" to JsonPrimitive(extra))).toString())
            return answer.await()
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
        val json = TdJson.parseToJsonElement(event).jsonObject
        val extra = json["@extra"]?.jsonPrimitive?.longOrNull
        val value = runCatching { TdJson.decodeFromJsonElement(TdObject.serializer(), json) }
        if (extra != null) {
            val answer = mutex.withLock { pending.remove(extra) } ?: return
            value.fold(answer::complete, answer::completeExceptionally)
            return
        }
        val update = value.getOrNull() as? Update ?: return
        val clientId = json["@client_id"]?.jsonPrimitive?.intOrNull ?: return
        val client = mutex.withLock { clients[clientId] } ?: return
        client.onUpdate(update)
        if (update is UpdateAuthorizationState && update.authorizationState is AuthorizationStateClosed) {
            mutex.withLock { clients.remove(clientId) }
        }
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
