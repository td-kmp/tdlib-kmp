package io.xbot.tdlib.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

/** TDLib's JSON dialect: "@type" discriminator, defaults and nulls omitted, unknown fields ignored. */
public val TdJson: Json = Json {
    classDiscriminator = "@type"
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
}

// TDLib accepts bytes only as a base64 string; kotlinx's default is a JSON array.
public object TdBytesSerializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("io.xbot.tdlib.api.Bytes", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ByteArray): Unit = encoder.encodeString(Base64.encode(value))
    override fun deserialize(decoder: Decoder): ByteArray = Base64.decode(decoder.decodeString())
}

internal val anyFunction = TdFunction.serializer(TdObject.serializer())

public fun TdFunction<*>.toTdJson(): String = TdJson.encodeToString(anyFunction, this)

public fun parseTdObject(json: String): TdObject = TdJson.decodeFromString(TdObject.serializer(), json)
