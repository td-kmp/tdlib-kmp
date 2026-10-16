package io.xbot.tdlib.client

import io.xbot.tdlib.TdLib
import io.xbot.tdlib.api.AuthorizationStateClosed
import io.xbot.tdlib.api.TdFunction
import io.xbot.tdlib.api.TdJson
import io.xbot.tdlib.api.TdObject
import io.xbot.tdlib.api.Update
import io.xbot.tdlib.api.UpdateAuthorizationState
import io.xbot.tdlib.api.anyFunction
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// td_receive serves every client of the process and must be called from one thread only,
// so a single loop dispatches all events: answers by "@extra", updates by "@client_id".
internal object TdReceiver {
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
        TdLib.send(client.id, JsonObject(json + ("@extra" to JsonPrimitive(extra))).toString())
        try {
            return answer.await()
        } finally {
            if (!answer.isCompleted) withContext(NonCancellable) { mutex.withLock { pending.remove(extra) } }
        }
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private fun startLoop() {
        val thread: CloseableCoroutineDispatcher = newSingleThreadContext("tdlib-receiver")
        CoroutineScope(thread).launch {
            try {
                while (true) {
                    val event = TdLib.receive(1.0)
                    if (event != null) {
                        runCatching { dispatch(event) }
                    } else if (mutex.withLock { clients.isEmpty().also { if (it) running = false } }) {
                        break
                    }
                }
            } finally {
                thread.close()
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
}
