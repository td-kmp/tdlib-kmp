package io.xbot.tdlib.client

import kotlin.test.Test
import kotlin.test.assertEquals

class TdEventTest {
    @Test
    fun aRequestGetsItsExtraAtTheEnd() {
        assertEquals("""{"@type":"getMe","@extra":7}""", """{"@type":"getMe"}""".withExtra(7))
    }

    @Test
    fun anEventsExtraAndClientIdAreReadOffItsEnd() {
        val answer = TdEvent("""{"@type":"ok","@extra":7,"@client_id":2}""")
        assertEquals(7L, answer.extra)
        assertEquals(2, answer.clientId)
        val update = TdEvent(
            """{"@type":"updateOption","name":"a,\"@extra\":5","value":{"@type":"optionValueEmpty"},"@client_id":3}""",
        )
        assertEquals(null, update.extra)
        assertEquals(3, update.clientId)
    }

    @Test
    fun anEventLaidOutAnotherWayIsParsedWhole() {
        val answer = TdEvent("""{ "@extra": 7, "@client_id": 2, "@type": "ok" }""")
        assertEquals(null, answer.extra)
        assertEquals(null, answer.clientId)
        assertEquals(7L, answer.parsedExtra)
        assertEquals(2, answer.parsedClientId)
    }
}
