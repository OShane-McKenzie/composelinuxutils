package pkg.virdin.composelinuxutils.json

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.contextual
import pkg.virdin.composelinuxutils.json.*


object SerializationConfig {
    val serializationModule = SerializersModule {
        contextual(Any::class, AnySerializer)
        contextual(IntRangeSerializer)
        contextual(LongRangeSerializer)
        contextual(CharRangeSerializer)
        contextual(ClosedFloatingPointRange::class, ClosedFloatingPointRangeSerializer)
    }

    val json = Json {
        serializersModule = serializationModule
        ignoreUnknownKeys = true
        isLenient = true
    }
}