package com.example.ledger.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 金额序列化器。
 * 业务层统一使用 [BigDecimal]，与 PostgreSQL numeric(12,2) 交互时输出两位小数的 JSON 数字，
 * 避免 double 带来的精度丢失；读取时兼容数字与字符串两种形式。
 */
@OptIn(ExperimentalSerializationApi::class)
object BigDecimalSerializer : KSerializer<BigDecimal> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.math.BigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        val text = value.setScale(2, RoundingMode.HALF_UP).toPlainString()
        if (encoder is JsonEncoder) {
            // 输出不带引号的数字，PostgREST 会按 numeric 类型写入
            encoder.encodeJsonElement(JsonUnquotedLiteral(text))
        } else {
            encoder.encodeString(text)
        }
    }

    override fun deserialize(decoder: Decoder): BigDecimal {
        // numeric 字段在 JSON 中可能是数字（如 10.00）或字符串，两种都要兼容
        val text = if (decoder is JsonDecoder) {
            decoder.decodeJsonElement().jsonPrimitive.content
        } else {
            decoder.decodeString()
        }
        return text.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP) ?: BigDecimal.ZERO
    }
}
