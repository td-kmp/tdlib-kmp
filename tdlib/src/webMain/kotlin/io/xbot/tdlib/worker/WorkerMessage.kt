package io.xbot.tdlib.worker

/** What the worker posts: an answer (`id` with `data` or `error`), or a message of its own. */
internal external interface WorkerMessage : JsAny {
    val id: JsNumber?
    val data: JsAny?
    val error: String?
    val wake: Boolean?
    val level: JsNumber?
    val message: String?
}

internal fun workerRequest(data: WorkerCommand): JsAny = js("({ data: data })")
internal fun workerRequest(id: Int, data: WorkerCommand): JsAny = js("({ id: id, data: data })")
