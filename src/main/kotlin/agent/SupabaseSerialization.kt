package com.waldirbaia.agent

import io.github.jan.supabase.SupabaseSerializer
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
            encodeJsonElementFallback(value) ?: throw e
        }
    }

    override fun <T : Any> decode(type: KType, value: String): T {
        return try {
            delegate.decode(type, value)
        } catch (e: SerializationException) {
            decodeJsonElementFallback(type, value) ?: throw e
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> encodeJsonElementFallback(value: T): String? {
        val element = value as? JsonElement ?: return null
        return json.encodeToString(JsonElement.serializer(), element)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> decodeJsonElementFallback(type: KType, value: String): T? {
        if (type.classifier != JsonElement::class) return null
        val element = json.decodeFromString(JsonElement.serializer(), value)
        return element as T
    }
}

fun createSupabaseSerializer(): SupabaseSerializer = GraalFriendlySerializer(SupabaseJson)
