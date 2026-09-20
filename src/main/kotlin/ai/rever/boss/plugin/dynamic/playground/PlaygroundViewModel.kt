package ai.rever.boss.plugin.dynamic.playground

import ai.rever.boss.plugin.api.RegisteredMcpTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the MCP Tool Playground panel.
 *
 * Holds selection (tool + args JSON) and drives calls through
 * [PlaygroundDispatcher]. The tool list itself is sourced from the registry
 * live, so adding/removing plugins updates the panel without a refresh.
 */
class PlaygroundViewModel(
    private val dispatcher: PlaygroundDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Live tools as exposed by the registry (all providers, including denied ones). */
    val allTools: StateFlow<List<RegisteredMcpTool>> =
        dispatcher.allToolsFlow()?.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = dispatcher.allToolsSnapshot(),
        ) ?: MutableStateFlow(dispatcher.allToolsSnapshot())

    /** Search query for the tool list - case-insensitive substring on name + description. */
    private val _filter = MutableStateFlow("")
    val filter: StateFlow<String> = _filter.asStateFlow()

    /** Selected tool name; null means "nothing picked". */
    private val _selectedToolName = MutableStateFlow<String?>(null)
    val selectedToolName: StateFlow<String?> = _selectedToolName.asStateFlow()

    /** Args JSON in the editor; bound to the text field. */
    private val _argsText = MutableStateFlow("")
    val argsText: StateFlow<String> = _argsText.asStateFlow()

    /** True while a tool call is in flight; disables the Call button. */
    private val _isCalling = MutableStateFlow(false)
    val isCalling: StateFlow<Boolean> = _isCalling.asStateFlow()

    /** Last call result - mirrored in the history but also surfaced in the result panel. */
    private val _lastCall = MutableStateFlow<CallRecord?>(null)
    val lastCall: StateFlow<CallRecord?> = _lastCall.asStateFlow()

    /** Latest non-recoverable error to show as a toast. */
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    /** Call history, exposed for the history list. */
    val history: StateFlow<List<CallRecord>> = dispatcher.history.records

    /** Selected tool, derived from [allTools] and [selectedToolName]. */
    val selectedTool: StateFlow<RegisteredMcpTool?> = combine(allTools, selectedToolName) {
            tools: List<RegisteredMcpTool>, name: String? ->
        if (name == null) {
            null
        } else {
            tools.firstOrNull { tool: RegisteredMcpTool -> tool.definition.name == name }
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    /** Filtered tools grouped by provider id, ready to render. */
    val groupedTools: StateFlow<Map<String, List<RegisteredMcpTool>>> =
        combine(allTools, _filter) { tools: List<RegisteredMcpTool>, query: String ->
            buildGrouped(tools, query)
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyMap(),
        )

    private fun buildGrouped(
        tools: List<RegisteredMcpTool>,
        query: String,
    ): Map<String, List<RegisteredMcpTool>> {
        val q = query.trim().lowercase()
        val filtered: List<RegisteredMcpTool> =
            if (q.isEmpty()) tools else tools.filter { tool: RegisteredMcpTool ->
                tool.definition.name.lowercase().contains(q) ||
                    tool.definition.description.lowercase().contains(q) ||
                    tool.providerId.lowercase().contains(q)
            }
        val byProvider: Map<String, List<RegisteredMcpTool>> =
            filtered.groupBy { tool: RegisteredMcpTool -> tool.providerId }
        val sorted = java.util.TreeMap<String, List<RegisteredMcpTool>>()
        for ((provider, list) in byProvider) {
            sorted[provider] = list.sortedBy { it.definition.name }
        }
        return sorted
    }

    fun setFilter(value: String) {
        _filter.value = value
    }

    fun selectTool(toolName: String) {
        _selectedToolName.value = toolName
        val tool = allTools.value.firstOrNull { t: RegisteredMcpTool -> t.definition.name == toolName }
        _argsText.value = tool?.let { t: RegisteredMcpTool ->
            ArgsSchemaDefault.defaultsFor(t.definition.inputSchema)
        } ?: "{}"
        _lastCall.value = null
    }

    fun setArgsText(value: String) {
        _argsText.value = value
    }

    fun clearArgs() {
        val tool = selectedTool.value
        _argsText.value = tool?.let { t: RegisteredMcpTool ->
            ArgsSchemaDefault.defaultsFor(t.definition.inputSchema)
        } ?: "{}"
    }

    fun call() {
        val toolName = _selectedToolName.value ?: return
        val args = _argsText.value
        if (_isCalling.value) return
        _isCalling.value = true
        _statusMessage.value = null
        scope.launch {
            try {
                val record = dispatcher.invoke(toolName, args)
                _lastCall.value = record
                if (record.isError) {
                    _statusMessage.value = record.errorMessage ?: record.resultText ?: "Tool returned an error"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Dispatcher threw: ${e.message ?: e::class.simpleName}"
            } finally {
                _isCalling.value = false
            }
        }
    }

    fun copyResult(text: String) {
        val ok = dispatcher.copyToClipboard(text)
        _statusMessage.value = if (ok) "Copied result to clipboard" else "Clipboard unavailable"
    }

    fun copyRecord(record: CallRecord) {
        val text = record.resultText ?: record.errorMessage ?: "(no content)"
        copyResult(text)
    }

    fun dismissStatus() {
        _statusMessage.value = null
    }

    fun onDispose() {
        scope.cancel()
    }
}
