package io.xbot.tdlib.worker

import org.w3c.dom.ErrorEvent
import org.w3c.dom.Worker

/** Takes over `worker.onmessage` and `worker.onerror`. */
internal abstract class WebWorkerWrapper(private val worker: Worker) {
    init {
        worker.onmessage = { event ->
            @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
            onMessage(event.data as WorkerMessage)
        }
        worker.onerror = { event -> onError((event as? ErrorEvent)?.message ?: "An unknown error has occurred in the worker") }
    }

    protected abstract fun onMessage(message: WorkerMessage)

    protected abstract fun onError(error: String)

    protected fun postMessage(message: JsAny) = worker.postMessage(message)
}
