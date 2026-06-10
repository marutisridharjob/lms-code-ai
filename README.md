# LMS Code AI — Eclipse Plugin

An Eclipse plugin that connects the IDE to a **local LM Studio server** — or any
**OpenAI-compatible** or **Anthropic/Claude-compatible** endpoint — and adds an
**LMS Code** context menu with AI refactoring, dependency vulnerability analysis
and a chat view.

- Java 21+, Eclipse 2024-06 or newer
- Built with Maven Tycho (also imports directly into Eclipse as PDE projects)

## Features

Right-click any file, package, folder or project → **LMS Code**:

| Action | What it does |
|---|---|
| **Refactor** | Sends the selected code (recursing into subfolders/packages/projects) to the AI. By default each proposal opens in a **compare editor** so you review the diff and save to apply; a preference switches to direct apply. |
| **Dependency** | Finds the project's `pom.xml` / `build.gradle` / `build.gradle.kts`, asks the AI for known vulnerabilities and fixes, and shows the findings (severity, CVE, fixed version, suggested snippet) in the **LMS Dependency** view. |
| **Chat** | Opens the **LMS Chat** view seeded with the selected file/text as context. Every transcript entry carries a **timestamp and location** (the workspace path for your messages; `model @ host:port (provider)` for responses). |

## Configuration

`Window → Preferences → LMS Code AI`:

| Setting | Default | Notes |
|---|---|---|
| Provider / protocol | OpenAI-compatible | See endpoint mapping below |
| Host | `localhost` | Bare host/IP (`192.168.1.36`) or full URL (`https://gateway:8443`) |
| Port | `1234` | LM Studio's default; ignored if the host URL already has a port |
| API key | empty | Optional for local LM Studio; sent as `Authorization: Bearer` (OpenAI/LM Studio) or `x-api-key` (Anthropic) |
| Model | empty | Use **Fetch Models** to populate from the server |
| Timeout / Max tokens / Temperature | 120 s / 0 / 0.2 | `0` max tokens = server default (Anthropic falls back to 4096, which its API requires) |
| When refactoring | Preview | Compare-editor preview vs. direct apply |

**Test Connection** and **Fetch Models** run against the values currently in the
dialog, so you can verify before saving.

### Provider → endpoint mapping

| Provider | List models | Chat |
|---|---|---|
| OpenAI-compatible (LM Studio default) | `GET /v1/models` | `POST /v1/chat/completions` |
| LM Studio native REST | `GET /api/v1/models` | `POST /api/v1/chat` |
| Anthropic / Claude | `GET /v1/models` | `POST /v1/messages` (`x-api-key`, `anthropic-version: 2023-06-01`) |

The client layer is a small interface (`AiClient`), so additional providers are
one class + one factory case away.

## Building

```bash
mvn clean verify
```

Produces two installable artifacts under `com.lmscode.ai.repository/target/`:

| Artifact | Use |
|---|---|
| `lms-code-ai-<version>-dropins.zip` | Plain plugin + feature jars (`eclipse/plugins/`, `eclipse/features/`) for drop-in installation — no update site needed |
| `repository/` (and `com.lmscode.ai.repository-<version>.zip`) | p2 update site for `Help → Install New Software…` |

> The build resolves the Eclipse 2024-06 target platform from
> `download.eclipse.org`, so it needs network access on first run.

### Installing the jars directly (dropins)

The plugin jar is **self-contained** (Gson is embedded), so the jars work on any
Eclipse 2024-06+ install:

1. Extract `lms-code-ai-<version>-dropins.zip` into your Eclipse installation's
   `dropins` folder, so you end up with:

   ```
   <eclipse>/dropins/lms-code-ai/eclipse/plugins/com.lmscode.ai_<version>.jar
   <eclipse>/dropins/lms-code-ai/eclipse/features/com.lmscode.ai.feature_<version>.jar
   ```

   (Alternatively, copy the two jars straight into `<eclipse>/dropins/`.)

2. Restart Eclipse with `eclipse -clean` once so the p2 reconciler picks the
   bundles up.
3. Verify under `Help → About → Installation Details → Plug-ins`
   (look for `com.lmscode.ai`), then configure `Window → Preferences → LMS Code AI`.

> Copying jars into the top-level `<eclipse>/plugins` / `<eclipse>/features`
> folders of a p2-managed installation is **not** reliably picked up on modern
> Eclipse — use the `dropins` folder (or the update site) instead. The zip's
> `eclipse/plugins` + `eclipse/features` layout also matches the legacy
> extension-location format if you do need it.

### Installing from the update site

`Help → Install New Software… → Add… → Local…`, point at
`com.lmscode.ai.repository/target/repository/` (or the repository zip) and
select **LMS Code AI**.

## Developing in Eclipse

1. `File → Import → Existing Projects into Workspace`, select this repo root —
   `com.lmscode.ai`, `com.lmscode.ai.feature`, `com.lmscode.ai.repository`.
2. Requires the **Plug-in Development Environment** (PDE) and a Java 21 JRE.
3. Right-click `com.lmscode.ai` → `Run As → Eclipse Application` to launch a
   runtime workbench with the plugin.

## Project layout

```
com.lmscode.ai/             the plugin
  src/com/lmscode/ai/
    client/                 AiClient + OpenAI / LM Studio native / Anthropic implementations
    core/                   prompts, markdown/JSON extraction
    dependency/             build-file analysis job + finding model
    handlers/               context-menu handlers (Refactor / Dependency / Chat)
    preferences/            preference page, constants, defaults
    refactor/               refactor job + compare-editor input
    views/                  LMS Chat and LMS Dependency views
com.lmscode.ai.feature/     Eclipse feature
com.lmscode.ai.repository/  p2 update site (category.xml)
```
