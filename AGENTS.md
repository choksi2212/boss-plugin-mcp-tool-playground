# AGENTS.md

Guidance for coding agents working on this repository.

## Project overview

`boss-plugin-mcp-tool-playground` is a side-panel + MCP-tools plugin for
the BOSS desktop application. It exposes every MCP tool contributed by
every loaded plugin to direct manual invocation from a single sidebar
panel.

The plugin lives at `~/boss-plugins/boss-plugin-mcp-tool-playground/`.

## Build commands

```bash
./gradlew buildPluginJar         # Build the plugin jar (local deps)
CI=true ./gradlew buildPluginJar # Build using the published 1.0.93 api jar
./gradlew build                  # Full build (depends on buildPluginJar)
```

The jar lands at `build/libs/boss-plugin-mcp-tool-playground-${version}.jar`.
The plugin's `version` lives in `build.gradle.kts`; `processResources`
mirrors it into `plugin.json` so there is one source of truth.

In CI (`CI=true` env var or running the `Tests` workflow), the api jar is
fetched from the public GitHub release at the pinned version. A gradle
task (`downloadBossPluginApi`) re-fetches it locally if a `clean` task
wiped the `build/downloaded-deps` directory, so a local CI-mode build
without the jar pre-downloaded still succeeds.

## Architecture

The plugin is a thin host adapter: `PlaygroundDynamicPlugin` registers
the panel and the MCP tools and shares one `PlaygroundDispatcher`
between them so the panel and the MCP tools see the same call history.

### Dispatch path

`PlaygroundDispatcher.invoke(toolName, argsJson)` reads
`mcpToolRegistry.allTools`, finds the named tool, parses the args JSON
into a `Map<String, Any?>`, wraps it in an `McpToolArgs`, and calls the
tool's `handler.call(args)`. The handler is invoked directly - the
plugin does NOT route through `mcpToolRegistry.invoke`, which would go
through the host's policy engine. The banner in the panel surfaces that
to the operator.

A throwing handler is caught and recorded as a `CallRecord` with
`isError = true` and the exception's message in `errorMessage`. A
handler that respects cancellation is bounded by `withTimeout(60_000ms)`
defensively; the host already wraps every tool call in its own timeout
and a handler that ignores cancellation would otherwise wedge the panel
until the host's timeout fires.

### History

`CallHistory` is an in-memory ring buffer of the last `MAX = 20`
records. Session-only by design. The history is held by the dispatcher
and read by both the panel ViewModel and the four MCP tools.

### Args editor defaults

`ArgsSchemaDefault` walks the tool's `inputSchema` and produces a JSON
object with one entry per property marked `required`, filled with a
type-appropriate empty value (`""` for string, `0` for number/integer,
`false` for boolean, `[]` for array, `{}` for object). Unknown types get
`""` because the host will report a missing-string argument as an
error, which is the safer failure mode than a silent zero-coercion.

### Plugin surface

- **Panel**: `PanelId("mcp-tool-playground", 74)` in `left.bottom` slot.
  Three sections: policy banner, two-column work area (tool list +
  editor/result), history.
- **MCP tools**: four tools, all `readOnly = true` except
  `mcp_playground_call` which is mutating (it can invoke mutating tools
  through the bypass path; the playground tool itself does not mutate
  host state but can trigger side effects in the called tool).

### Three-wrapper rule

`PluginContext` accesses go through `context.mcpToolRegistry` and
`context.clipboardProvider`. Both are nullable; the plugin null-checks
each call site and degrades gracefully (the list goes empty if no
registry; the copy button reports "Clipboard unavailable").

## Constraints

- No references to AI, Claude, Anthropic, automation, or LLMs in
  source, commits, PR descriptions, or any file in this repo.
- No Co-Authored-By lines in git commits or PR descriptions.
- Spaced hyphens (` - `) in prose, never em-dashes (U+2014). The
  em-dash is U+2014 specifically; en-dashes are fine.
- All Kotlin files end with a newline.
- No calls that write to MCP policy (`setToolEnabled`,
  `setToolPolicy`, etc.). The plugin is read-only against MCP state.
- The dispatch path goes through handler.call, not through
  mcpToolRegistry.invoke, by design.

## Local dev

The repo is meant to be a sibling of `boss-plugin-api`:

```
~/boss-plugins/
  boss-plugin-api/                # compiled jar at build/libs/boss-plugin-api-1.0.93.jar
  boss-plugin-mcp-tool-playground/   # this repo
```

`./gradlew buildPluginJar` resolves the api jar from the sibling repo
when `CI` is unset. Set `CI=true` to use the published GitHub release
instead.

## Tests workflow

The `Tests` workflow runs `./gradlew build` on every pull request.
Required checks: compile green, jar built, allTasks green.

The `Release` workflow delegates to
`risa-labs-inc/BossConsole-Releases/.github/workflows/plugin-release.yml@main`
and publishes a GitHub Release. The build.yml workflow declares
`permissions: contents: write` so the release dispatch is not refused.

## Files to read first

- `src/main/kotlin/ai/rever/boss/plugin/dynamic/playground/PlaygroundDynamicPlugin.kt`
- `src/main/kotlin/ai/rever/boss/plugin/dynamic/playground/PlaygroundDispatcher.kt`
- `src/main/kotlin/ai/rever/boss/plugin/dynamic/playground/PlaygroundMcpTools.kt`
- `README.md`
