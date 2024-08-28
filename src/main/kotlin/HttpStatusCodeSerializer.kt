package com.sonefall.blt

import io.ktor.http.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object HttpStatusCodeSerializer : KSerializer<HttpStatusCode> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("HttpStatusCode", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): HttpStatusCode {
        val statusCode = decoder.decodeString()
        return HttpStatusCode(statusCode.take(3).toInt(), statusCode.drop(4))
    }

    override fun serialize(encoder: Encoder, value: HttpStatusCode)
        = encoder.encodeString(HttpStatusCode.fromValue(value.value).toString())
}