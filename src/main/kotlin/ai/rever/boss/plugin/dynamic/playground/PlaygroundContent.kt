package ai.rever.boss.plugin.dynamic.playground

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.rever.boss.plugin.api.RegisteredMcpTool
import ai.rever.boss.plugin.scrollbar.getPanelScrollbarConfig
import ai.rever.boss.plugin.scrollbar.lazyListScrollbar
import ai.rever.boss.plugin.ui.BossThemeColors

/**
 * Top-level composable for the MCP Tool Playground panel.
 *
 * Three sections, stacked vertically:
 *  1. Policy banner - always visible, makes the security implication explicit.
 *  2. Two-column tool list + editor/result area.
 *  3. Optional history list below the editor.
 */
@Composable
fun PlaygroundContent(viewModel: PlaygroundViewModel) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PolicyBanner()
            Divider(color = BossThemeColors.BorderColor.copy(alpha = 0.3f))
            StatusToast(viewModel.statusMessage.collectAsState().value, onDismiss = viewModel::dismissStatus)
            MainSplit(viewModel)
        }
    }
}

@Composable
private fun PolicyBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BossThemeColors.WarningColor.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = BossThemeColors.WarningColor,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Calls made here BYPASS host MCP policy. Use only for plugin development.",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = BossThemeColors.WarningColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusToast(message: String?, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
    ) {
        val text = message ?: return@AnimatedVisibility
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BossThemeColors.SurfaceColor)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                color = BossThemeColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "Dismiss",
                    modifier = Modifier.size(12.dp),
                    tint = BossThemeColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun MainSplit(viewModel: PlaygroundViewModel) {
    Row(modifier = Modifier.fillMaxSize()) {
        ToolListColumn(
            viewModel = viewModel,
            modifier = Modifier
                .weight(0.42f)
                .fillMaxSize(),
        )
        Divider(
            color = BossThemeColors.BorderColor.copy(alpha = 0.3f),
            modifier = Modifier.width(1.dp).fillMaxSize(),
        )
        EditorColumn(
            viewModel = viewModel,
            modifier = Modifier
                .weight(0.58f)
                .fillMaxSize(),
        )
    }
}

@Composable
private fun ToolListColumn(
    viewModel: PlaygroundViewModel,
    modifier: Modifier = Modifier,
) {
    val filter by viewModel.filter.collectAsState()
    val grouped by viewModel.groupedTools.collectAsState()
    val selected by viewModel.selectedToolName.collectAsState()
    val all by viewModel.allTools.collectAsState()

    Column(modifier = modifier) {
        FilterRow(
            value = filter,
            onValueChange = viewModel::setFilter,
            count = all.size,
        )
        Divider(color = BossThemeColors.BorderColor.copy(alpha = 0.3f))
        if (all.isEmpty()) {
            EmptyToolList()
        } else if (grouped.isEmpty()) {
            NoMatches(filter)
        } else {
            ToolGroupList(
                grouped = grouped,
                selectedToolName = selected,
                onSelect = viewModel::selectTool,
            )
        }
    }
}

@Composable
private fun FilterRow(
    value: String,
    onValueChange: (String) -> Unit,
    count: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = BossThemeColors.TextSecondary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 12.sp,
                color = BossThemeColors.TextPrimary,
            ),
            cursorBrush = SolidColor(MaterialTheme.colors.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Filter tools...",
                            fontSize = 12.sp,
                            color = BossThemeColors.TextMuted,
                        )
                    }
                    inner()
                }
            },
        )
        Text(
            text = "$count",
            fontSize = 10.sp,
            color = BossThemeColors.TextMuted,
        )
    }
}

@Composable
private fun EmptyToolList() {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No MCP tools available",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = BossThemeColors.TextSecondary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Load a plugin that contributes MCP tools.",
                fontSize = 10.sp,
                color = BossThemeColors.TextMuted,
            )
        }
    }
}

@Composable
private fun NoMatches(filter: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No tools match \"$filter\"",
            fontSize = 11.sp,
            color = BossThemeColors.TextMuted,
        )
    }
}

@Composable
private fun ToolGroupList(
    grouped: Map<String, List<RegisteredMcpTool>>,
    selectedToolName: String?,
    onSelect: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .lazyListScrollbar(
                    listState = listState,
                    direction = Orientation.Vertical,
                    config = getPanelScrollbarConfig(),
                ),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            grouped.forEach { (providerId, tools) ->
                item(key = "h-$providerId") {
                    SectionHeader(providerId, tools.size)
                }
                items(tools, key = { "t-${it.providerId}-${it.definition.name}" }) { tool ->
                    ToolRow(
                        tool = tool,
                        selected = tool.definition.name == selectedToolName,
                        onClick = { onSelect(tool.definition.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(providerId: String, count: Int) {
    val label = providerId.substringAfterLast('.')
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = BossThemeColors.TextSecondary,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "($count)",
            fontSize = 10.sp,
            color = BossThemeColors.TextMuted,
        )
    }
}

@Composable
private fun ToolRow(
    tool: RegisteredMcpTool,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colors.primary.copy(alpha = 0.15f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tool.definition.name,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = BossThemeColors.TextPrimary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!tool.definition.readOnly) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "mutating",
                        fontSize = 9.sp,
                        color = BossThemeColors.WarningColor,
                    )
                }
            }
            Text(
                text = tool.definition.description,
                fontSize = 10.sp,
                color = BossThemeColors.TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EditorColumn(
    viewModel: PlaygroundViewModel,
    modifier: Modifier = Modifier,
) {
    val tool by viewModel.selectedTool.collectAsState()
    val argsText by viewModel.argsText.collectAsState()
    val isCalling by viewModel.isCalling.collectAsState()
    val lastCall by viewModel.lastCall.collectAsState()
    val history by viewModel.history.collectAsState()

    Column(modifier = modifier) {
        if (tool == null) {
            NoToolSelected()
        } else {
            EditorHeader(toolName = tool!!.definition.name, providerId = tool!!.providerId)
            Divider(color = BossThemeColors.BorderColor.copy(alpha = 0.3f))
            ToolDescription(text = tool!!.definition.description)
            Divider(color = BossThemeColors.BorderColor.copy(alpha = 0.3f))
            ArgsEditor(
                value = argsText,
                onValueChange = viewModel::setArgsText,
                onCall = viewModel::call,
                onClear = viewModel::clearArgs,
                isCalling = isCalling,
            )
            Divider(color = BossThemeColors.BorderColor.copy(alpha = 0.3f))
            ResultPanel(
                record = lastCall,
                onCopy = { viewModel.copyResult(it) },
            )
            if (history.isNotEmpty()) {
                Divider(color = BossThemeColors.BorderColor.copy(alpha = 0.3f))
                HistoryPanel(
                    history = history,
                    onCopyRecord = viewModel::copyRecord,
                )
            }
        }
    }
}

@Composable
private fun NoToolSelected() {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Select a tool on the left to begin",
            fontSize = 12.sp,
            color = BossThemeColors.TextMuted,
        )
    }
}

@Composable
private fun EditorHeader(toolName: String, providerId: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = toolName,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = BossThemeColors.TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = providerId.substringAfterLast('.'),
            fontSize = 9.sp,
            color = BossThemeColors.TextMuted,
            maxLines = 1,
        )
    }
}

@Composable
private fun ToolDescription(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface.copy(alpha = 0.3f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            color = BossThemeColors.TextSecondary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArgsEditor(
    value: String,
    onValueChange: (String) -> Unit,
    onCall: () -> Unit,
    onClear: () -> Unit,
    isCalling: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.surface)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Arguments (JSON)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = BossThemeColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onClear,
                enabled = !isCalling,
            ) {
                Text("Clear", fontSize = 10.sp, color = BossThemeColors.TextSecondary)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isCalling) BossThemeColors.BorderColor else MaterialTheme.colors.primary)
                    .clickable(enabled = !isCalling, onClick = onCall)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isCalling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        strokeWidth = 1.5.dp,
                        color = BossThemeColors.TextPrimary,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CallReceived,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = BossThemeColors.TextPrimary,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Call",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = BossThemeColors.TextPrimary,
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(MaterialTheme.colors.background)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = BossThemeColors.TextPrimary,
                ),
                cursorBrush = SolidColor(MaterialTheme.colors.primary),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ResultPanel(
    record: CallRecord?,
    onCopy: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.surface)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Result",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = BossThemeColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            if (record != null) {
                Text(
                    text = "${record.durationMs}ms",
                    fontSize = 10.sp,
                    color = BossThemeColors.TextMuted,
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val text = record.resultText ?: record.errorMessage ?: "(no content)"
                        onCopy(text)
                    },
                    modifier = Modifier.size(20.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy result",
                        modifier = Modifier.size(12.dp),
                        tint = BossThemeColors.TextSecondary,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(MaterialTheme.colors.background)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            if (record == null) {
                Text(
                    text = "No calls yet for this tool.",
                    fontSize = 11.sp,
                    color = BossThemeColors.TextMuted,
                )
            } else {
                val text = record.resultText ?: record.errorMessage ?: "(no content)"
                val state = when {
                    record.isError -> BossThemeColors.ErrorColor
                    else -> BossThemeColors.SuccessColor
                }
                val annotated = buildAnnotatedString {
                    pushStyle(SpanStyle(color = state, fontWeight = FontWeight.SemiBold))
                    append(
                        if (record.isError) "[error] " else "[ok] ",
                    )
                    pop()
                    append(text)
                }
                val scroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll),
                ) {
                    Text(
                        text = annotated,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = BossThemeColors.TextPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryPanel(
    history: List<CallRecord>,
    onCopyRecord: (CallRecord) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.surface)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = BossThemeColors.TextSecondary,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "History (${history.size}/${CallHistory.MAX})",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = BossThemeColors.TextSecondary,
            )
        }
        val listState = rememberLazyListState()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(MaterialTheme.colors.background),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .lazyListScrollbar(
                        listState = listState,
                        direction = Orientation.Vertical,
                        config = getPanelScrollbarConfig(),
                    ),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                items(history, key = { "h-${it.id}" }) { record ->
                    HistoryRow(record = record, onCopy = { onCopyRecord(record) })
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(record: CallRecord, onCopy: () -> Unit) {
    val state = when {
        record.isError -> BossThemeColors.ErrorColor
        else -> BossThemeColors.SuccessColor
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(state),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.toolName,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = BossThemeColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${record.durationMs}ms",
                fontSize = 9.sp,
                color = BossThemeColors.TextMuted,
            )
        }
        Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = "Copy",
            modifier = Modifier.size(11.dp),
            tint = BossThemeColors.TextSecondary,
        )
    }
}
