package ai.rever.boss.plugin.dynamic.playground

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Fills a JSON Schema argument object with empty defaults so the operator has
 * a starting point to edit.
 *
 * Only the surface the operator typically touches is filled - the schema's
 * `properties` map, restricted to those marked `required`. Properties of
 * unknown type get an empty string, which is the safest stand-in (a number
 * `""` reads as a missing required argument, which the host reports as an
 * error rather than silently zero-coercing).
 *
 * Returns an empty object (`{}`) for an empty/malformed schema so the
 * editor starts in a valid state.
 */
object ArgsSchemaDefault {

    /**
     * Produce a JSON object string with one entry per required property of
     * [inputSchema], each filled with a type-appropriate empty value.
     *
     * @param inputSchema JSON Schema fragment (must be an object schema).
     * @return a JSON object string suitable for the args editor.
     */
    fun defaultsFor(inputSchema: String): String {
        val parsed = parseObject(inputSchema) ?: return EMPTY_OBJECT
        val properties = parsed["properties"]?.jsonObject ?: return EMPTY_OBJECT
        val required = parsed["required"]?.jsonArray
            ?.mapNotNull { (it as? JsonPrimitive)?.content }
            ?.toSet()
            ?: emptySet()
        if (properties.isEmpty()) return EMPTY_OBJECT

        val filled = buildMap<String, JsonElement> {
            properties.forEach { (name, schema) ->
                if (required.isEmpty() || name in required) {
                    put(name, defaultFor(schema))
                }
            }
        }
        return JsonObject(filled).toString()
    }

    /** Same as [defaultsFor] but parses the result to a `Map<String, Any?>`. */
    fun defaultsMapFor(inputSchema: String): Map<String, Any?> {
        val parsed = parseObject(inputSchema) ?: return emptyMap()
        val properties = parsed["properties"]?.jsonObject ?: return emptyMap()
        val required = parsed["required"]?.jsonArray
            ?.mapNotNull { (it as? JsonPrimitive)?.content }
            ?.toSet()
            ?: emptySet()
        if (properties.isEmpty()) return emptyMap()

        val filled = buildMap {
            properties.forEach { (name, schema) ->
                if (required.isEmpty() || name in required) {
                    put(name, jsonElementToKotlin(schema))
                }
            }
        }
        return filled
    }

    /**
     * Convert a kotlinx-serialization [JsonElement] to a plain Kotlin value
     * compatible with [ai.rever.boss.plugin.api.McpToolArgs]'s expected
     * `Map<String, Any?>` shape.
     */
    fun jsonElementToKotlin(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            element.booleanOrNull != null -> element.boolean
            element.intOrNull != null -> element.intOrNull
            else -> element.content
        }
        is JsonArray -> element.map { jsonElementToKotlin(it) }
        is JsonObject -> element.mapValues { (_, v) -> jsonElementToKotlin(v) }
    }

    private fun defaultFor(schema: JsonElement): JsonElement {
        val obj = schema as? JsonObject ?: return JsonPrimitive("")
        val type = obj["type"]?.let { (it as? JsonPrimitive)?.content } ?: return JsonPrimitive("")
        return when (type) {
            "string" -> JsonPrimitive("")
            "integer" -> JsonPrimitive(0)
            "number" -> JsonPrimitive(0)
            "boolean" -> JsonPrimitive(false)
            "array" -> JsonArray(emptyList())
            "object" -> JsonObject(emptyMap())
            else -> JsonPrimitive("")
        }
    }

    private fun parseObject(inputSchema: String): JsonObject? = try {
        val element = PARSER.parseToJsonElement(inputSchema)
        element as? JsonObject ?: element.jsonObject["properties"]?.let {
            // Tolerate a schema that starts as a {"properties":...} object
            // whose own type/required fields sit at the outer level.
            element as? JsonObject
        }
    } catch (e: Exception) {
        null
    }

    private const val EMPTY_OBJECT = "{}"
}

/** Convenience: parse [jsonString] to a `Map<String, Any?>` or fail. */
internal fun parseArgsJson(jsonString: String): Map<String, Any?> = try {
    val parsed = PARSER.parseToJsonElement(jsonString)
    val obj = parsed as? JsonObject ?: return emptyMap()
    obj.mapValues { (_, v) -> ArgsSchemaDefault.jsonElementToKotlin(v) }
} catch (e: Exception) {
    emptyMap()
}

private val PARSER = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    isLenient = true
}
