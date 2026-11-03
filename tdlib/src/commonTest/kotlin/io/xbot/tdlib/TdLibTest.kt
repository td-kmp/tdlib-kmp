package io.xbot.tdlib

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class TdLibTest {
    private val tdLib = testTdLib()

    // TDLib answers on its own thread (or from a worker): run on real time, not runTest's virtual clock.
    private fun test(body: suspend () -> Unit) = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(60.seconds) {
                tdLib.execute("""{"@type":"setLogVerbosityLevel","new_verbosity_level":1}""")
                body()
            }
        }
    }

    /** The next event matching [predicate] within [limit], or null. */
    private suspend fun receiveUntil(limit: Duration, predicate: (String) -> Boolean): String? {
        val deadline = TimeSource.Monotonic.markNow() + limit
        while (deadline.hasNotPassedNow()) {
            val event = tdLib.receive(1.seconds) ?: continue
            if (predicate(event)) return event
        }
        return null
    }

    @Test
    fun testCreateClientId() {
        val id = tdLib.createClientId()
        assertTrue(id > 0, "Client ID must be positive, got $id")
    }

    @Test
    fun testExecute() = test {
        val result = tdLib.execute("""{"@type":"getOption","name":"version"}""")
        assertNotNull(result, "execute() must return a non-null result")
        assertTrue(result.contains("optionValueString"), "Result type must be 'optionValueString': $result")
        assertTrue(result.contains("value"), "Result must contain 'value' field: $result")
    }

    @Test
    fun testMultipleClientsHaveDistinctIds() {
        val ids = (1..5).map { tdLib.createClientId() }
        assertEquals(ids.size, ids.toSet().size, "All client IDs must be distinct, got: $ids")
    }

    @Test
    fun testSendAndReceivePreservesExtra() = test {
        val clientId = tdLib.createClientId()
        val extra = "unique-extra-tag-$clientId"

        tdLib.send(clientId, """{"@type":"getOption","name":"version","@extra":"$extra"}""")
        val response = receiveUntil(30.seconds) { extra in it }

        assertNotNull(response, "Must receive a response for the sent request within timeout")
        assertTrue(response.contains("optionValueString"), "Response type must be 'optionValueString': $response")
        assertTrue(response.contains("\"@client_id\":$clientId"), "Response must carry the client id: $response")

        // Exiting with a running instance aborts: atexit tears OpenSSL down under TDLib's threads.
        tdLib.send(clientId, """{"@type":"close"}""")
        val closed = receiveUntil(30.seconds) { "authorizationStateClosed" in it && "\"@client_id\":$clientId" in it }
        assertNotNull(closed, "The client must close")
    }

    @Test
    fun testLogMessageHandler() = test {
        val marker = "tdlib-kmp-log-check"
        var received: String? = null
        tdLib.setLogMessageHandler(1) { _, message -> if (marker in message) received = message }
        try {
            tdLib.execute("""{"@type":"addLogMessage","verbosity_level":1,"text":"$marker"}""")
        } finally {
            tdLib.setLogMessageHandler(0, null)
        }
        assertNotNull(received, "The log message handler must receive addLogMessage output")
    }
}
