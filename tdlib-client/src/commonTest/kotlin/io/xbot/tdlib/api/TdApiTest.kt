package io.xbot.tdlib.api

import io.xbot.tdlib.client.testTdLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class TdApiTest {
    private val tdLib = testTdLib()

    private fun test(body: suspend () -> Unit) = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(60.seconds) {
                tdLib.execute(SetLogVerbosityLevel(newVerbosityLevel = 1).toTdJson())
                body()
            }
        }
    }

    private suspend fun <R : TdObject> execute(function: TdFunction<R>): TdObject =
        parseTdObject(checkNotNull(tdLib.execute(function.toTdJson())))

    @Test
    fun functionsEncodeWithTypeDiscriminator() {
        val json = GetChatHistory(chatId = 42, fromMessageId = 1L shl 40, limit = 10).toTdJson()
        assertTrue("\"@type\":\"getChatHistory\"" in json, json)
        assertTrue("\"chat_id\":42" in json, json)
    }

    @Test
    fun polymorphicResultsDecode() = test {
        val version = execute(GetOption(name = "version"))
        assertIs<OptionValueString>(version)
        assertTrue(version.value.isNotEmpty())
    }

    @Test
    fun nestedObjectsDecode() = test {
        val entities = execute(GetTextEntities(text = "@telegram /test https://telegram.org"))
        assertIs<TextEntities>(entities)
        assertEquals(
            listOf(TextEntityTypeMention, TextEntityTypeBotCommand, TextEntityTypeUrl),
            entities.entities.map { it.type },
        )
    }

    @Test
    fun errorsDecode() = test {
        val error = execute(GetOption(name = ""))
        assertIs<Error>(error)
    }

    @Test
    fun int64RoundTripsThroughTdlib() = test {
        // getPushReceiverId returns the first 8 bytes of "p": here Long.MAX_VALUE, beyond a double's precision.
        val id = execute(GetPushReceiverId(payload = """{"p":"_________38AAAAAAAAAAA=="}"""))
        assertEquals(PushReceiverId(id = Long.MAX_VALUE), id)

        val ids = TdJson.decodeFromString(EmojiStatusCustomEmojis.serializer(), """{"custom_emoji_ids":["1","-2"]}""")
        assertEquals(listOf(1L, -2L), ids.customEmojiIds)
    }

    @Test
    fun bytesAsBase64() {
        val json = SetTdlibParameters(databaseEncryptionKey = byteArrayOf(1, 2, 3)).toTdJson()
        assertTrue("\"database_encryption_key\":\"AQID\"" in json, json)
    }
}
