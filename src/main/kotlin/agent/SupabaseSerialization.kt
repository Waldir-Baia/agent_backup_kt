package com.waldirbaia.agent

import io.github.jan.supabase.SupabaseSerializer
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import kotlin.reflect.KType

val SupabaseJson: Json = Json(Json.Default) {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
}

@OptIn(ExperimentalSerializationApi::class)
private class GraalFriendlySerializer(private val json: Json) : SupabaseSerializer {
    private val delegate = KotlinXSerializer(json)

    override fun <T : Any> encode(type: KType, value: T): String {
        return try {
            delegate.encode(type, value)
        } catch (e: SerializationException) {
            val primitive = value as? JsonPrimitive
            if (primitive != null) {
                json.encodeToString(JsonPrimitive.serializer(), primitive)
            } else {
                throw e
            }
        }
    }

    override fun <T : Any> decode(type: KType, value: String): T {
        return delegate.decode(type, value)
    }
}

fun createSupabaseSerializer(): SupabaseSerializer = GraalFriendlySerializer(SupabaseJson)
