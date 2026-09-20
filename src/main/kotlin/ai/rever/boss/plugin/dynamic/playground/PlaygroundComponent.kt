package ai.rever.boss.plugin.dynamic.playground

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.ui.BossTheme
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext

/**
 * MCP Tool Playground panel component.
 *
 * Delegates rendering to [PlaygroundContent] so this class stays a thin
 * host adapter. The dispatcher is shared with the MCP tool provider, so a
 * call made from the panel and a call made from an MCP client of the
 * playground's own tools land in the same history buffer.
 */
class PlaygroundComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    dispatcher: PlaygroundDispatcher,
) : PanelComponentWithUI, ComponentContext by ctx {

    private val viewModel = PlaygroundViewModel(dispatcher)

    @Composable
    override fun Content() {
        BossTheme {
            PlaygroundContent(viewModel)
        }
    }
}
