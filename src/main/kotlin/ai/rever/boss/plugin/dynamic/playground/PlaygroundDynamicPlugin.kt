package ai.rever.boss.plugin.dynamic.playground

import ai.rever.boss.plugin.api.ClipboardProvider
import ai.rever.boss.plugin.api.DynamicPlugin
import ai.rever.boss.plugin.api.McpToolRegistry
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory

/**
 * MCP Tool Playground dynamic plugin - loaded from external JAR.
 *
 * Side panel for browsing MCP tools contributed by every loaded plugin,
 * editing their args JSON inline, invoking them, and seeing the result.
 *
 * Dispatches tool calls by reading [McpToolRegistry.allTools] and calling
 * each tool's handler directly - the panel is a development aid, and the
 * banner surfaces the implication to the operator. The plugin itself does
 * not read or write MCP policy state (no [McpToolRegistry.setToolEnabled]).
 */
class PlaygroundDynamicPlugin : DynamicPlugin {
    override val pluginId: String = "ai.rever.boss.plugin.dynamic.mcptoolplayground"
    override val displayName: String = "MCP Tool Playground"
    override val version: String = "0.1.0"
    override val description: String =
        "Browse and invoke MCP tools from any loaded plugin. Calls BYPASS host MCP policy."
    override val author: String = "Risa Labs"
    override val url: String = "https://github.com/choksi2212/boss-plugin-mcp-tool-playground"

    private var dispatcher: PlaygroundDispatcher? = null

    override fun register(context: PluginContext) {
        val registry = context.mcpToolRegistry
        val clipboard = context.clipboardProvider
        val history = CallHistory()
        val disp = PlaygroundDispatcher(registry, history, clipboard)

        context.panelRegistry.registerPanel(PlaygroundInfo) { ctx, panelInfo ->
            PlaygroundComponent(ctx, panelInfo, disp)
        }
        context.registerMcpToolProvider(
            PlaygroundMcpToolProvider(pluginId, disp),
        )
        dispatcher = disp

        logger.info(
            LogCategory.SYSTEM,
            "MCP Tool Playground registered",
            mapOf(
                "registryAvailable" to (registry != null),
                "clipboardAvailable" to (clipboard != null),
            ),
        )
    }

    override fun dispose() {
        dispatcher = null
        logger.info(LogCategory.SYSTEM, "MCP Tool Playground disposed")
    }

    companion object {
        private val logger = BossLogger.forComponent("McpToolPlayground")
    }
}
