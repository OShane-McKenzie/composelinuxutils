package pkg.virdin.composelinuxutils.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

import kotlin.toString

// ── Standalone range serializers ────────────────────────────────────────────

object IntRangeSerializer : KSerializer<IntRange> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IntRange") {
        element<Int>("first")
        element<Int>("last")
    }

    override fun serialize(encoder: Encoder, value: IntRange) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.first)
            encodeIntElement(descriptor, 1, value.last)
        }
    }

    override fun deserialize(decoder: Decoder): IntRange {
        return decoder.decodeStructure(descriptor) {
            var first = 0
            var last = 0
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> first = decodeIntElement(descriptor, 0)
                    1 -> last = decodeIntElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            IntRange(first, last)
        }
    }
}

object LongRangeSerializer : KSerializer<LongRange> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LongRange") {
        element<Long>("first")
        element<Long>("last")
    }

    override fun serialize(encoder: Encoder, value: LongRange) {
        encoder.encodeStructure(descriptor) {
            encodeLongElement(descriptor, 0, value.first)
            encodeLongElement(descriptor, 1, value.last)
        }
    }

    override fun deserialize(decoder: Decoder): LongRange {
        return decoder.decodeStructure(descriptor) {
            var first = 0L
            var last = 0L
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> first = decodeLongElement(descriptor, 0)
                    1 -> last = decodeLongElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            LongRange(first, last)
        }
    }
}

object CharRangeSerializer : KSerializer<CharRange> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CharRange") {
        element<Int>("first")
        element<Int>("last")
    }

    override fun serialize(encoder: Encoder, value: CharRange) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.first.code)
            encodeIntElement(descriptor, 1, value.last.code)
        }
    }

    override fun deserialize(decoder: Decoder): CharRange {
        return decoder.decodeStructure(descriptor) {
            var first = 0
            var last = 0
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> first = decodeIntElement(descriptor, 0)
                    1 -> last = decodeIntElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            CharRange(first.toChar(), last.toChar())
        }
    }
}

object ClosedFloatingPointRangeSerializer : KSerializer<ClosedFloatingPointRange<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ClosedFloatingPointRange") {
        element<String>("type")
        element<Double>("first")
        element<Double>("last")
    }

    override fun serialize(encoder: Encoder, value: ClosedFloatingPointRange<*>) {
        encoder.encodeStructure(descriptor) {
            when (val first = value.start) {
                is Double -> {
                    encodeStringElement(descriptor, 0, "Double")
                    encodeDoubleElement(descriptor, 1, first)
                    encodeDoubleElement(descriptor, 2, value.endInclusive as Double)
                }
                is Float -> {
                    encodeStringElement(descriptor, 0, "Float")
                    encodeDoubleElement(descriptor, 1, first.toDouble())
                    encodeDoubleElement(descriptor, 2, (value.endInclusive as Float).toDouble())
                }
                else -> error("Unsupported ClosedFloatingPointRange type: ${first::class}")
            }
        }
    }

    override fun deserialize(decoder: Decoder): ClosedFloatingPointRange<*> {
        return decoder.decodeStructure(descriptor) {
            var type = "Double"
            var first = 0.0
            var last = 0.0
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> type  = decodeStringElement(descriptor, 0)
                    1 -> first = decodeDoubleElement(descriptor, 1)
                    2 -> last  = decodeDoubleElement(descriptor, 2)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            when (type) {
                "Float" -> first.toFloat()..last.toFloat()
                else    -> first..last
            }
        }
    }
}

// ── AnySerializer ────────────────────────────────────────────────────────────

object AnySerializer : KSerializer<Any> {
    private val jsonElementSerializer = JsonElement.Companion.serializer()

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Any", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Any) {
        val jsonElement = when (value) {
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is IntRange -> rangeToJsonObject("IntRange", value.first, value.last)
            is LongRange -> rangeToJsonObject("LongRange", value.first, value.last)
            is CharRange -> rangeToJsonObject("CharRange", value.first.code, value.last.code)
            is ClosedFloatingPointRange<*> -> {
                val first = value.start
                val last = value.endInclusive
                when {
                    first is Double -> rangeToJsonObject("DoubleRange", first, last as Double)
                    first is Float  -> rangeToJsonObject("FloatRange", first, last as Float)
                    else            -> JsonPrimitive(value.toString())
                }
            }
            is Map<*, *> -> {
                val map = value.entries.associate { (k, v) ->
                    k.toString() to convertAnyToJsonElement(v)
                }
                JsonObject(map)
            }
            is List<*> -> {
                val list = value.map { convertAnyToJsonElement(it) }
                JsonArray(list)
            }
            null -> JsonNull
            else -> {
                JsonPrimitive(value.toString())
            }
        }

        encoder.encodeSerializableValue(jsonElementSerializer, jsonElement)
    }

    private fun rangeToJsonObject(type: String, first: Number, last: Number): JsonObject =
        JsonObject(mapOf(
            "__range" to JsonPrimitive(type),
            "first"   to JsonPrimitive(first),
            "last"    to JsonPrimitive(last),
        ))

    private fun convertAnyToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is IntRange -> rangeToJsonObject("IntRange", value.first, value.last)
            is LongRange -> rangeToJsonObject("LongRange", value.first, value.last)
            is CharRange -> rangeToJsonObject("CharRange", value.first.code, value.last.code)
            is ClosedFloatingPointRange<*> -> {
                val first = value.start
                val last = value.endInclusive
                when {
                    first is Double -> rangeToJsonObject("DoubleRange", first, last as Double)
                    first is Float  -> rangeToJsonObject("FloatRange", first, last as Float)
                    else            -> JsonPrimitive(value.toString())
                }
            }
            is Map<*, *> -> {
                val map = value.entries.associate { (k, v) ->
                    k.toString() to convertAnyToJsonElement(v)
                }
                JsonObject(map)
            }
            is List<*> -> {
                val list = value.map { convertAnyToJsonElement(it) }
                JsonArray(list)
            }
            else -> JsonPrimitive(value.toString())
        }
    }

    override fun deserialize(decoder: Decoder): Any {
        val jsonElement = decoder.decodeSerializableValue(jsonElementSerializer)
        return convertJsonElementToAny(jsonElement)
    }

    private fun convertJsonElementToAny(element: JsonElement): Any {
        return when (element) {
            is JsonPrimitive -> when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.boolean
                element.longOrNull != null -> element.long
                element.doubleOrNull != null -> element.double
                else -> element.content
            }
            is JsonObject -> {
                val rangeType = (element["__range"] as? JsonPrimitive)?.content
                when (rangeType) {
                    "IntRange"    -> IntRange(
                        (element["first"] as JsonPrimitive).int,
                        (element["last"]  as JsonPrimitive).int,
                    )
                    "LongRange"   -> LongRange(
                        (element["first"] as JsonPrimitive).long,
                        (element["last"]  as JsonPrimitive).long,
                    )
                    "CharRange"   -> CharRange(
                        (element["first"] as JsonPrimitive).int.toChar(),
                        (element["last"]  as JsonPrimitive).int.toChar(),
                    )
                    "DoubleRange" -> (element["first"] as JsonPrimitive).double..(element["last"] as JsonPrimitive).double
                    "FloatRange"  -> (element["first"] as JsonPrimitive).double.toFloat()..(element["last"] as JsonPrimitive).double.toFloat()
                    else          -> element.mapValues { convertJsonElementToAny(it.value) }
                }
            }
            is JsonArray -> element.map { convertJsonElementToAny(it) }
            JsonNull -> "null"
        }
    }
}