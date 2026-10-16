package io.xbot.tdlib.api

import io.xbot.tdlib.TdLib
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TdApiTest {
    init {
        TdLib.execute(SetLogVerbosityLevel(newVerbosityLevel = 1).toTdJson())
    }

    private fun <R : TdObject> execute(function: TdFunction<R>): TdObject =
        parseTdObject(checkNotNull(TdLib.execute(function.toTdJson())))

    @Test
    fun functionsEncodeWithTypeDiscriminator() {
        val json = GetChatHistory(chatId = 42, fromMessageId = 1L shl 40, limit = 10).toTdJson()
        assertTrue("\"@type\":\"getChatHistory\"" in json, json)
        assertTrue("\"chat_id\":42" in json, json)
    }

    @Test
    fun polymorphicResultsDecode() {
        val version = execute(GetOption(name = "version"))
        assertIs<OptionValueString>(version)
        assertEquals("1.8.68", version.value)
    }

    @Test
    fun nestedObjectsDecode() {
        val entities = execute(GetTextEntities(text = "@telegram /test https://telegram.org"))
        assertIs<TextEntities>(entities)
        assertEquals(
            listOf(TextEntityTypeMention, TextEntityTypeBotCommand, TextEntityTypeUrl),
            entities.entities.map { it.type },
        )
    }

    @Test
    fun errorsDecode() {
        val error = execute(GetOption(name = ""))
        assertIs<Error>(error)
    }

    @Test
    fun int64RoundTripsThroughTdlib() {
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
