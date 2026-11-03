package io.xbot.tdlib

import io.xbot.tdlib.worker.createDurableTestWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.w3c.dom.Worker
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private fun terminate(worker: Worker): Unit = js("worker.terminate()")

class WorkerTdLibTest {
    private fun test(body: suspend () -> Unit) = runTest {
        withContext(Dispatchers.Default) { withTimeout(60.seconds) { body() } }
    }

    private suspend fun TdLib.until(limit: Duration = 30.seconds, predicate: (String) -> Boolean): String? {
        val deadline = TimeSource.Monotonic.markNow() + limit
        while (deadline.hasNotPassedNow()) {
            val event = receive(1.seconds) ?: continue
            if (predicate(event)) return event
        }
        return null
    }

    private suspend fun TdLib.authorized(id: Int) {
        send(id, """{"@type":"getOption","name":"version"}""")
        assertNotNull(until { "authorizationStateWaitTdlibParameters" in it })
        send(
            id,
            """{"@type":"setTdlibParameters","use_test_dc":true,"database_directory":"/durable/db","api_id":1,""" +
                """"api_hash":"00000000000000000000000000000000","system_language_code":"en","device_model":"test","application_version":"1.0"}""",
        )
        assertNotNull(until { "authorizationStateWaitPhoneNumber" in it })
    }

    @Test
    fun databaseIsDurableOnceCloseIsReported() = test {
        val firstWorker = createDurableTestWorker()
        val first = WorkerTdLib(firstWorker)
        val firstId = first.createClientId()
        first.authorized(firstId)
        first.send(firstId, """{"@type":"setOption","name":"ignore_inline_thumbnails","value":{"@type":"optionValueBoolean","value":true}}""")
        first.send(firstId, """{"@type":"close"}""")
        assertNotNull(first.until { "authorizationStateClosed" in it })
        terminate(firstWorker) // at once: the data must already be in IndexedDB

        val secondWorker = createDurableTestWorker()
        val second = WorkerTdLib(secondWorker)
        val secondId = second.createClientId()
        second.authorized(secondId)
        second.send(secondId, """{"@type":"getOption","name":"ignore_inline_thumbnails","@extra":"probe"}""")
        val answer = second.until { "\"probe\"" in it }
        assertTrue(answer?.contains("\"value\":true") == true, "Option must survive the worker: $answer")
        second.send(secondId, """{"@type":"close"}""")
        assertNotNull(second.until { "authorizationStateClosed" in it })
        terminate(secondWorker)
    }
}
