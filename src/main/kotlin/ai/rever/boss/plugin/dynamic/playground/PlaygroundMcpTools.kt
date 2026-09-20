package ai.rever.boss.plugin.dynamic.playground

import ai.rever.boss.plugin.api.McpToolArgs
import ai.rever.boss.plugin.api.McpToolDefinition
import ai.rever.boss.plugin.api.McpToolHandler
import ai.rever.boss.plugin.api.McpToolProvider
import ai.rever.boss.plugin.api.McpToolResult
import ai.rever.boss.plugin.api.RegisteredMcpTool

/**
 * MCP tools exposed by the playground itself, so an in-terminal agent can
 * list, describe, and invoke MCP tools without going through the host's
 * `mcp__boss__<tool>` path (which goes through policy). These tools share
 * the same dispatcher as the panel, so a call here lands in the same
 * history buffer as a click on Call in the UI.
 */
internal class PlaygroundMcpToolProvider(
    override val providerId: String,
    private val dispatcher: PlaygroundDispatcher,
) : McpToolProvider {

    override fun tools(): List<McpToolDefinition> = listOf(
        McpToolDefinition(
            name = "mcp_playground_list_tools",
            description = "List every MCP tool currently registered by any loaded plugin, " +
                "including those the current user is not permitted to call. Returns JSON " +
                "with each tool's name, providerId, description, readOnly flag, and inputSchema.",
            handler = McpToolHandler { _ -> listTools() },
        ),
        McpToolDefinition(
            name = "mcp_playground_schema",
            description = "Return the JSON Schema (as a string) describing one MCP tool's " +
                "arguments. Use mcp_playground_list_tools first to discover names.",
            inputSchema = NAME_SCHEMA,
            handler = McpToolHandler { args -> schemaFor(args) },
        ),
        McpToolDefinition(
            name = "mcp_playground_call",
            description = "Invoke an MCP tool by name with a JSON arguments string. " +
                "Bypasses the host's MCP policy engine and approval dialog. " +
                "Returns the tool's text payload, or an error if the tool is missing, " +
                "the arguments are malformed, or the handler throws.",
            inputSchema = CALL_SCHEMA,
            handler = McpToolHandler { args -> callViaDispatcher(args) },
        ),
        McpToolDefinition(
            name = "mcp_playground_history",
            description = "Return the last calls made through the playground in this session " +
                "(most-recent first, capped at 20). Read-only.",
            handler = McpToolHandler { _ -> history() },
        ),
    )

    private fun listTools(): McpToolResult {
        val tools = dispatcher.allToolsSnapshot()
        if (tools.isEmpty()) {
            return McpToolResult("[]")
        }
        val rendered = tools.joinToString(",\n") { tool ->
            """  {
    "name": ${jsonStr(tool.definition.name)},
    "providerId": ${jsonStr(tool.providerId)},
    "description": ${jsonStr(tool.definition.description)},
    "readOnly": ${tool.definition.readOnly},
    "inputSchema": ${tool.definition.inputSchema}
  }"""
        }
        return McpToolResult("[\n$rendered\n]")
    }

    private fun schemaFor(args: McpToolArgs): McpToolResult {
        val name = args.string("toolName")
            ?: return McpToolResult("Missing required argument: toolName", isError = true)
        val tool = dispatcher.allToolsSnapshot().firstOrNull { it.definition.name == name }
            ?: return McpToolResult("Tool not found: $name", isError = true)
        return McpToolResult(tool.definition.inputSchema)
    }

    private suspend fun callViaDispatcher(args: McpToolArgs): McpToolResult {
        val name = args.string("toolName")
            ?: return McpToolResult("Missing required argument: toolName", isError = true)
        val raw = args.raw
        val argsJson = args.string("argsJson")
            ?: extractArgsJsonFromRaw(raw)
            ?: return McpToolResult(
                "Missing required argument: argsJson (expected a JSON object string)",
                isError = true,
            )
        val record = dispatcher.invoke(name, argsJson)
        return McpToolResult(
            text = record.resultText ?: record.errorMessage ?: "(no content)",
            isError = record.isError,
        )
    }

    private fun history(): McpToolResult {
        val items = dispatcher.history.records.value
        if (items.isEmpty()) return McpToolResult("[]")
        val rendered = items.joinToString(",\n") { r ->
            """  {
    "toolName": ${jsonStr(r.toolName)},
    "providerId": ${jsonStr(r.providerId)},
    "isError": ${r.isError},
    "durationMs": ${r.durationMs},
    "timestamp": ${r.timestamp},
    "argsJson": ${jsonStr(r.argsJson)},
    "resultText": ${jsonStr(r.resultText)},
    "errorMessage": ${jsonStr(r.errorMessage)}
  }"""
        }
        return McpToolResult("[\n$rendered\n]")
    }

    private fun jsonStr(value: String?): String {
        if (value == null) return "null"
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    /**
     * The McpToolArgs.raw field is the entire arguments string the client
     * sent; if the client sent the args nested under a `argsJson` key, that
     * raw string still parses for us. If it didn't, fall back to the typed
     * `argsJson` getter (which only sees top-level strings).
     */
    private fun extractArgsJsonFromRaw(raw: String): String? {
        if (raw.isBlank() || raw == "{}") return null
        return try {
            kotlinx.serialization.json.Json
                .parseToJsonElement(raw)
                .let { element ->
                    val obj = element as? kotlinx.serialization.json.JsonObject
                    obj?.get("argsJson")?.let {
                        (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                    }
                }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val NAME_SCHEMA =
            """{"type":"object","properties":{"toolName":{"type":"string","description":"MCP tool name (e.g. git_status)."}},"required":["toolName"]}"""
        const val CALL_SCHEMA =
            """{"type":"object","properties":{"toolName":{"type":"string","description":"MCP tool name to invoke."},"argsJson":{"type":"string","description":"JSON arguments object as a string (e.g. {\"path\":\"README.md\"})."}},"required":["toolName","argsJson"]}"""
    }
}
