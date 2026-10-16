package io.xbot.tdlib

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TdLibTest {
    init {
        // TDLib logs at verbosity 5 by default; keep test output readable.
        TdLib.execute("""{"@type":"setLogVerbosityLevel","new_verbosity_level":1}""")
    }

    @Test
    fun testCreateClientId() {
        val id = TdLib.createClientId()
        assertTrue(id > 0, "Client ID must be positive, got $id")
    }

    @Test
    fun testExecute() {
        val result = TdLib.execute("""{"@type":"getOption","name":"version"}""")
        assertNotNull(result, "execute() must return a non-null result")
        assertTrue(result.contains("optionValueString"), "Result type must be 'optionValueString': $result")
        assertTrue(result.contains("value"), "Result must contain 'value' field: $result")
    }

    @Test
    fun testMultipleClientsHaveDistinctIds() {
        val ids = (1..5).map { TdLib.createClientId() }
        assertEquals(ids.size, ids.toSet().size, "All client IDs must be distinct, got: $ids")
    }

    @Test
    fun testSendAndReceivePreservesExtra() {
        val clientId = TdLib.createClientId()
        val extra = "unique-extra-tag-${clientId}"

        TdLib.send(
            clientId,
            """{"@type":"getOption","name":"version","@extra":"$extra"}"""
        )

        var response: String? = null
        repeat(50) {
            if (response == null) {
                val incoming = TdLib.receive(0.5)
                if (incoming != null && incoming.contains(extra)) {
                    response = incoming
                }
            }
        }

        assertNotNull(response, "Must receive a response for the sent request within timeout")
        assertTrue(
            response.contains("optionValueString"),
            "Response type must be 'optionValueString': $response"
        )
        assertTrue(
            response.contains(extra),
            "@extra field must be echoed back in the response: $response"
        )
    }

    @Test
    fun testLogMessageHandler() {
        val marker = "tdlib-kmp-log-check"
        var received: String? = null
        TdLib.setLogMessageHandler(1) { _, message -> if (marker in message) received = message }
        try {
            TdLib.execute("""{"@type":"addLogMessage","verbosity_level":1,"text":"$marker"}""")
        } finally {
            TdLib.setLogMessageHandler(0, null)
        }
        assertNotNull(received, "The log message handler must receive addLogMessage output")
    }
}
