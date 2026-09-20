package ai.rever.boss.plugin.dynamic.playground

import ai.rever.boss.plugin.api.ClipboardProvider
import ai.rever.boss.plugin.api.McpToolArgs
import ai.rever.boss.plugin.api.McpToolRegistry
import ai.rever.boss.plugin.api.McpToolResult
import ai.rever.boss.plugin.api.RegisteredMcpTool
import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

/**
 * Invokes MCP tools directly through their handlers, bypassing the host's
 * policy engine (Always Allow / Always Deny / approval dialog).
 *
 * The banner in the panel surfaces that to the operator - the playground is a
 * development aid, and using it for production traffic is exactly what the
 * warning is for. The plugin does NOT touch [McpToolRegistry.setToolEnabled]
 * or any other policy state, so its surface to the policy engine is read-only.
 *
 * A throwing handler is caught and returned as [CallRecord.errorMessage] so
 * one misbehaving tool cannot take the panel down. The timeout here is
 * defensive only: the host already wraps every tool call in its own
 * [withTimeout] and a handler that respects cancellation does not need this,
 * but a misbehaving handler that runs a tight loop with no suspension point
 * would otherwise wedge the panel until the host's outer timeout fires.
 */
class PlaygroundDispatcher(
    private val mcpToolRegistry: McpToolRegistry?,
    val history: CallHistory,
    private val clipboardProvider: ClipboardProvider?,
) {
    private val logger = BossLogger.forComponent("PlaygroundDispatcher")

    /** Snapshot of every tool currently registered, grouped by provider id. */
    fun allToolsSnapshot(): List<RegisteredMcpTool> {
        return try {
            mcpToolRegistry?.allTools?.value ?: emptyList()
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Failed to read allTools", error = e)
            emptyList()
        }
    }

    /** Tools that pass RBAC checks for the current user, in registry order. */
    fun availableToolsSnapshot(): List<RegisteredMcpTool> {
        return try {
            mcpToolRegistry?.tools?.value ?: emptyList()
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Failed to read tools", error = e)
            emptyList()
        }
    }

    /** State flow of every tool (including denied ones) so the UI can update live. */
    fun allToolsFlow(): StateFlow<List<RegisteredMcpTool>>? = mcpToolRegistry?.allTools

    /**
     * Invoke [toolName] with [argsJson]. Records the call in [history].
     *
     * Parsing failures do NOT throw; the call is recorded with
     * [CallRecord.errorMessage] so the operator sees what went wrong. JSON
     * parse errors round-trip through the same path as a tool failure, with
     * the parser exception as the message.
     */
    suspend fun invoke(toolName: String, argsJson: String): CallRecord {
        val started = System.currentTimeMillis()
        val tool = findTool(toolName)
        val baseRecord = CallRecord(
            id = started,
            toolName = toolName,
            providerId = tool?.providerId ?: "",
            argsJson = argsJson,
            resultText = null,
            isError = false,
            errorMessage = null,
            durationMs = 0L,
            timestamp = started,
        )
        if (tool == null) {
            return baseRecord.copy(
                isError = true,
                errorMessage = "Tool not found: $toolName",
                durationMs = System.currentTimeMillis() - started,
            ).also(history::add)
        }
        val args = try {
            McpToolArgs(parseArgsJson(argsJson), raw = argsJson)
        } catch (e: Exception) {
            return baseRecord.copy(
                providerId = tool.providerId,
                isError = true,
                errorMessage = "Args parse error: ${e.message}",
                durationMs = System.currentTimeMillis() - started,
            ).also(history::add)
        }
        val result = try {
            withTimeout(HARD_TIMEOUT_MS) {
                tool.definition.handler.call(args)
            }
        } catch (e: TimeoutCancellationException) {
            McpToolResult("Tool timed out after ${HARD_TIMEOUT_MS}ms", isError = true)
        } catch (e: Exception) {
            logger.error(
                LogCategory.SYSTEM,
                "Tool handler threw",
                data = mapOf("tool" to toolName),
                error = e,
            )
            McpToolResult("Handler threw: ${e.message ?: e::class.simpleName}", isError = true)
        }
        return baseRecord.copy(
            providerId = tool.providerId,
            resultText = result.text,
            isError = result.isError,
            errorMessage = if (result.isError && result.text.isNotBlank()) result.text else null,
            durationMs = System.currentTimeMillis() - started,
        ).also(history::add)
    }

    /** Place a successful result text on the system clipboard. */
    fun copyToClipboard(text: String): Boolean {
        val cp = clipboardProvider ?: return false
        return try {
            cp.setText(text)
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Clipboard write failed", error = e)
            false
        }
    }

    private fun findTool(toolName: String): RegisteredMcpTool? {
        val tools = mcpToolRegistry?.allTools?.value ?: return null
        return tools.firstOrNull { it.definition.name == toolName }
    }

    companion object {
        private const val HARD_TIMEOUT_MS: Long = 60_000L
    }
}
