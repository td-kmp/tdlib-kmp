package io.xbot.tdlib.client

import io.xbot.tdlib.TdLib
import io.xbot.tdlib.TdLibUnavailableException
import io.xbot.tdlib.api.AuthorizationStateClosed
import io.xbot.tdlib.api.TdFunction
import io.xbot.tdlib.api.TdObject
import io.xbot.tdlib.api.Update
import io.xbot.tdlib.api.UpdateAuthorizationState
import io.xbot.tdlib.api.toTdJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

// TdLib.receive serves every client of a TdLib and allows one receiver, so one loop per TdLib
// dispatches all its events: answers by "@extra", updates by "@client_id".
internal class TdReceiver private constructor(val tdLib: TdLib) {
    private val mutex = Mutex()
    private val clients = mutableMapOf<Int, TdClient>()
    private val pending = mutableMapOf<Long, CompletableDeferred<TdEvent>>()
    private var nextExtra = 1L
    private var running = false

    suspend fun register(client: TdClient) = mutex.withLock {
        clients[client.id] = client
        ensureLoop()
    }

    suspend fun request(client: TdClient, function: TdFunction<*>): TdObject {
        val answer = CompletableDeferred<TdEvent>()
        val request = function.toTdJson()
        // Sent under the lock, so requests reach TDLib in the order they were made.
        val extra = mutex.withLock {
            nextExtra++.also {
                tdLib.send(client.id, request.withExtra(it))
                pending[it] = answer
                ensureLoop()
            }
        }
        try {
            // Decoded here rather than in the receive loop, and never on the caller's main thread.
            return withContext(Dispatchers.Default) { answer.await().decode() }
        } finally {
            if (!answer.isCompleted) withContext(NonCancellable) { mutex.withLock { pending.remove(extra) } }
        }
    }

    // The TdLib's loop, not a caller's: started under the lock, it runs while a client or a request expects an event.
    @OptIn(DelicateCoroutinesApi::class)
    private fun ensureLoop() {
        if (running) return
        running = true
        GlobalScope.launch(Dispatchers.Default) {
            try {
                while (true) {
                    val event = tdLib.receive(1.seconds)
                    if (event != null) runCatching { dispatch(TdEvent(event)) } else if (stopIfIdle()) break
                }
            } catch (e: TdLibUnavailableException) {
                fail(e)
            }
        }
    }

    private suspend fun stopIfIdle(): Boolean = mutex.withLock {
        (clients.isEmpty() && pending.isEmpty()).also { idle -> if (idle) running = false }
    }

    private suspend fun dispatch(event: TdEvent) {
        val extra = event.extra
        if (extra != null) return answer(extra, event)
        when (val update = runCatching { event.decode() }.getOrNull()) {
            is Update -> deliver(update, event.clientId ?: event.parsedClientId ?: return)
            // Not an update: an answer framed another way, or an object this td_api doesn't know.
            else -> event.parsedExtra?.let { answer(it, event) }
        }
    }

    private suspend fun answer(extra: Long, event: TdEvent) {
        mutex.withLock { pending.remove(extra) }?.complete(event)
    }

    private suspend fun deliver(update: Update, clientId: Int) {
        val client = mutex.withLock { clients[clientId] } ?: return
        client.onUpdate(update)
        if (update is UpdateAuthorizationState && update.authorizationState is AuthorizationStateClosed) {
            mutex.withLock { clients.remove(clientId) }
        }
    }

    private suspend fun fail(cause: TdLibUnavailableException) {
        val (answers, orphans) = mutex.withLock {
            running = false
            pending.drain() to clients.drain()
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

private fun <V> MutableMap<*, V>.drain(): List<V> = values.toList().also { clear() }
