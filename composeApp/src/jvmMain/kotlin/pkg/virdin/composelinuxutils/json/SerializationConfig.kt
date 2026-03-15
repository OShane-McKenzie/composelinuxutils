package pkg.virdin.composelinuxutils.json

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.contextual



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
        encodeDefaults = true        // serializes fields even when they hold their default value
        explicitNulls = false        // omits null fields entirely instead of writing "key": null
        coerceInputValues = true     // bad/unknown enum values fall back to the declared default
        allowSpecialFloatingPointValues = true  // allows NaN / Infinity (needed for AnySerializer)
    }

    // Inherits every setting above — only adds pretty printing.
    // Use for logging, debug output, or writing human-readable files.
    val prettyJson = Json(json) {
        prettyPrint = true
    }
}