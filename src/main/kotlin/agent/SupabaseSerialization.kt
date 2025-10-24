package com.waldirbaia.agent

import io.github.jan.supabase.SupabaseSerializer
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import kotlin.reflect.KType
import kotlin.reflect.full.createType

// Json compartilhado para toda a aplicação (compatível com native-image)
val supabaseJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

fun createSupabaseSerializer(): SupabaseSerializer = NativeImageSupabaseSerializer(supabaseJson)

private class NativeImageSupabaseSerializer(
    private val json: Json
) : SupabaseSerializer {

    private val delegate = KotlinXSerializer(json)

    private val jsonElementSerializer = JsonElement.serializer()
    private val jsonElementType = JsonElement::class.createType()
    private val jsonPrimitiveType = JsonPrimitive::class.createType()
    private val jsonLiteralTypeName = "kotlinx.serialization.json.JsonLiteral"

    override fun <T : Any> encode(type: KType, value: T): String {
        if (value is JsonElement) {
            return json.encodeToString(jsonElementSerializer, value)
        }
        if (type == jsonElementType || type == jsonPrimitiveType || type.isJsonLiteral()) {
            return json.encodeToString(jsonElementSerializer, value as JsonElement)
        }

        return try {
            delegate.encode(type, value)
        } catch (e: SerializationException) {
            when (value) {
                is JsonPrimitive -> json.encodeToString(jsonElementSerializer, value)
                is JsonObject -> json.encodeToString(jsonElementSerializer, value)
                is JsonArray -> json.encodeToString(jsonElementSerializer, value)
                else -> if (value.isJsonLiteralClass()) {
                    json.encodeToString(jsonElementSerializer, value as JsonElement)
                } else {
                    throw e
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> decode(type: KType, value: String): T {
        if (type == jsonElementType || type == jsonPrimitiveType || type.isJsonLiteral()) {
            return json.decodeFromString(jsonElementSerializer, value) as T
        }

        return try {
            delegate.decode(type, value)
        } catch (e: SerializationException) {
            if (type == jsonPrimitiveType || type.isJsonLiteral()) {
                return json.decodeFromString(jsonElementSerializer, value) as T
            }
            throw e
        }
    }

    private fun KType.isJsonLiteral(): Boolean {
        val typeName = toString()
        return typeName == jsonLiteralTypeName || typeName == "$jsonLiteralTypeName?"
    }

    private fun Any.isJsonLiteralClass(): Boolean {
        return this::class.qualifiedName == jsonLiteralTypeName
    }
}
