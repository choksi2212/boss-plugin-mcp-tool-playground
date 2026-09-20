package ai.rever.boss.plugin.dynamic.playground

import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.left
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import compose.icons.FeatherIcons
import compose.icons.feathericons.Terminal

/**
 * MCP Tool Playground panel info.
 *
 * Sidebar slot for browsing and invoking MCP tools from any loaded plugin.
 */
object PlaygroundInfo : PanelInfo {
    override val id = PanelId("mcp-tool-playground", 74)
    override val displayName = "MCP Playground"
    override val icon = FeatherIcons.Terminal
    override val defaultSlotPosition = left.bottom
}
