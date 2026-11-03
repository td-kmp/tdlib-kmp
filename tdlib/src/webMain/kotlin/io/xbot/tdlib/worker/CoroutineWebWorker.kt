package io.xbot.tdlib.worker

import io.xbot.tdlib.TdLibUnavailableException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.Worker
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Requests that suspend until the worker answers, matched by `id`. After a failure every request throws. */
internal abstract class CoroutineWebWorker(worker: Worker) : WebWorkerWrapper(worker) {
    private var nextMessageId = 0
    private val pending = mutableMapOf<Int, CancellableContinuation<JsAny?>>()
    private var failure: TdLibUnavailableException? = null

    /** A message that is not an answer to a request. */
    protected abstract fun onEvent(message: WorkerMessage)

    protected abstract fun onFailure(cause: TdLibUnavailableException)

    /** A one-way request. */
    fun sendRequest(command: WorkerCommand) {
        failure?.let { throw it }
        postMessage(workerRequest(command))
    }

    /** A request and its answer's `data`. */
    suspend fun awaitRequest(command: WorkerCommand): JsAny? {
        failure?.let { throw it }
        val id = nextMessageId++
        return suspendCancellableCoroutine { continuation ->
            pending[id] = continuation
            continuation.invokeOnCancellation { pending.remove(id) }
            postMessage(workerRequest(id, command))
        }
    }

    override fun onMessage(message: WorkerMessage) {
        val id = message.id
        val error = message.error
        when {
            id != null -> {
                val continuation = pending.remove(id.toInt()) ?: return
                if (error != null) continuation.resumeWithException(TdLibUnavailableException(error))
                else continuation.resume(message.data)
            }
            error != null -> onError(error)
            else -> onEvent(message)
        }
    }

    override fun onError(error: String) {
        if (failure != null) return
        val cause = TdLibUnavailableException(error)
        failure = cause
        pending.values.toList().forEach { it.resumeWithException(cause) }
        pending.clear()
        onFailure(cause)
    }
}
