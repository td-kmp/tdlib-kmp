package io.xbot.tdlib.worker

import org.w3c.dom.Worker

fun createTestWorker(): Worker =
    js("""new Worker(new URL("tdlib-test-worker/worker.js", import.meta.url), { type: "module" })""")

/** Its storage is /durable: two workers must not write to one storage at the same time. */
fun createDurableTestWorker(): Worker =
    js("""new Worker(new URL("tdlib-test-worker/worker.js", import.meta.url), { type: "module", name: "durable" })""")
