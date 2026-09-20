# MCP Tool Playground

A BOSS plugin that opens every MCP tool contributed by every loaded plugin
to direct, manual invocation from a single sidebar panel.

## What it does

- Lists every MCP tool currently registered with the host MCP registry,
  grouped by the plugin (`providerId`) that contributed it.
- Lets the operator edit the tool's args JSON inline, with required fields
  pre-filled from the tool's input schema.
- Calls the tool's handler directly, shows the result text (with a marker
  for error vs ok and the wall-clock duration), and copies it to the
  clipboard with one click.
- Keeps a session-scoped ring buffer of the last 20 calls; each entry can
  be re-copied by clicking it.
- Also exposes four MCP tools of its own so an in-terminal agent can list,
  describe, and call the same tool set without going through the host's
  `mcp__boss__<tool>` path.

## What it is NOT

- **It is not a policy surface.** The plugin reads the registry but never
  writes to it - no `setToolEnabled`, no `setToolPolicy`, no editing of
  the user's MCP rules. The plugin's UI and its MCP tools share one
  in-memory call history; nothing is persisted.
- **It is not a guarded dispatcher.** Calls made through this panel BYPASS
  the host's MCP policy engine (Always Allow / Always Deny) and approval
  dialog. The panel surfaces this in a banner at the top: "Calls made here
  BYPASS host MCP policy. Use only for plugin development." Use the panel
  for testing and debugging a tool while you build it; use the normal
  `mcp__boss__<tool>` path for any production traffic.

## Why a tool call from this panel bypasses policy

The plugin reads `context.mcpToolRegistry.allTools` and calls each tool's
handler directly. The host's `invoke(toolName, argumentsJson)` path goes
through the policy engine; calling the handler directly skips it.

This is a deliberate choice: the playground's whole point is to call a
tool *by hand*, including before an Always Allow rule has been written.
The banner says so. If you want a tool's call to count against a
persistent rule, run it from an MCP client instead.

## MCP tools exposed by the playground

| Tool | Description |
|---|---|
| `mcp_playground_list_tools()` | Every tool in the registry (including denied ones), with name, providerId, description, readOnly flag, and inputSchema. |
| `mcp_playground_schema(toolName)` | The inputSchema for one tool, returned as the JSON Schema string the registry holds. |
| `mcp_playground_call(toolName, argsJson)` | Invoke a tool by name. argsJson is a JSON object string. Bypasses host MCP policy. |
| `mcp_playground_history()` | The last 20 calls (most-recent first). Read-only. |

The four tools share one history buffer with the panel: a call made from
the MCP tools above appears in the panel's history list, and vice versa.

## Panel

The panel sits in the left sidebar's bottom slot (`PanelId("mcp-tool-playground", 74)`).
It is a three-row layout:

1. A warning banner reminding the operator of the policy bypass.
2. A two-column work area:
   - Left: filter box and a tool list grouped by provider.
   - Right: tool name + description, args editor with Call/Clear buttons,
     result panel with copy button.
3. A history list below the result panel showing the last 20 calls with
   durationMs.

The result panel marks success with a `[ok]` prefix in green and failure
with `[error]` in red, and reports the duration next to the copy button.

## Install

```bash
./gradlew buildPluginJar
```

The plugin JAR is produced at `build/libs/boss-plugin-mcp-tool-playground-0.1.0.jar`.

In BOSS, open the Toolbox and install from the local file, or drag the
JAR onto the Plugins window. The host loads it as `mixed` (panel + MCP
tools).

## Compatibility

- `boss-plugin-api` 1.0.93 (host resolves `mcpToolRegistry` and
  `clipboardProvider`; both are nullable, the panel degrades gracefully
  if either is absent).
- BOSS host 9.4.2 or later.
- JVM 17.

## Plugin layout

```
src/main/kotlin/ai/rever/boss/plugin/dynamic/playground/
  PlaygroundDynamicPlugin.kt        entry point (DynamicPlugin)
  PlaygroundInfo.kt                 PanelInfo (id, slot, icon)
  PlaygroundComponent.kt            PanelComponentWithUI
  PlaygroundContent.kt              composables (banner, list, editor, result, history)
  PlaygroundViewModel.kt            selection + filter + history state
  PlaygroundDispatcher.kt           direct handler invocation, throws caught
  PlaygroundMcpTools.kt             the four MCP tools
  ArgsSchemaDefault.kt              JSON Schema -> default values for the editor
  CallHistory.kt                    in-memory ring buffer of CallRecord
```

## Notes

- The plugin does not write to disk; closing the session clears the
  history. Twenty entries is a session-only convenience for re-copying a
  recent result, not a record.
- A throwing handler is caught and recorded with the exception message in
  the `errorMessage` field. The panel still works.
- A handler that runs longer than 60 seconds is interrupted by a
  `withTimeout` and the result is recorded as a timeout error. This is
  defensive: the host already wraps every tool call in its own timeout.
- Optional dependencies are reported as "missing" by the host's
  dependency dialog when the plugin is first enabled. The playground has
  none of its own - it depends only on the host's MCP registry.
