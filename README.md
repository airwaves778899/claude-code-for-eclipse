# Claude Code for Eclipse

An Eclipse IDE plugin that integrates [Claude Code CLI](https://docs.anthropic.com/en/docs/claude-code/overview) directly into your workspace. No API key required — uses your locally installed and authenticated `claude` CLI as the backend.

![Claude Code for Eclipse](icons/claude.png)

---

## Features

| Feature | Description |
|---------|-------------|
| 💬 **Claude Chat Panel** | Chat with Claude in a docked Eclipse view with full multi-turn conversation support |
| 📁 **Auto Working Directory** | Automatically follows the active project, or set a fixed directory |
| 📄 **Active File Context** | Automatically includes the currently open file path in every query |
| 🖱️ **Ask Claude about selection** | Select code in any editor → right-click → send directly to Claude |
| ⚙️ **Preferences Page** | Centralized settings for CLI path, model, and behavior toggles |
| 🔑 **No API Key Needed** | Authenticates via your local Claude CLI session |

---

## Requirements

- Eclipse IDE 2023-03 or later (tested on Eclipse 2024-06 JEE)
- Java 17+
- [Claude Code CLI](https://docs.anthropic.com/en/docs/claude-code/overview) installed and authenticated

---

## Installation

### Option 1 — Manual JAR (simplest)

1. Download the latest `com.holtek.claudecode_1.0.0.jar` from [Releases](../../releases/latest)
2. Copy the JAR to the `dropins/` folder inside your Eclipse installation directory  
   e.g. `C:\eclipse\eclipse-jee-2024-06-R-win32-x86_64\dropins\`
3. Restart Eclipse

### Option 2 — Update Site (Install New Software)

1. Go to **Help → Install New Software…**
2. Click **Add…** and enter:
   - Name: `Claude Code for Eclipse`
   - Location: `https://your-github-pages-url/updatesite/`
3. Select **Claude Code for Eclipse** and click **Next** to install

---

## Getting Started

### 1. Install Claude Code CLI

```bash
npm install -g @anthropic-ai/claude-code
claude   # follow the prompts to log in
```

### 2. Configure Preferences

Go to **Window → Preferences → Claude Code**:

| Setting | Description |
|---------|-------------|
| **CLI Path** | Leave empty for auto-detection, or enter the full path to `claude.exe` |
| **Auto Detect** button | Searches common install locations for the `claude` executable |
| **Test Connection** button | Runs `claude --version` to verify the CLI is reachable |
| **Default Working Directory** | Leave empty to auto-follow the active project |
| **Default Model** | Select the Claude model (default: claude-sonnet-4-5) |
| **Auto-switch working directory on tab change** | Updates working directory when you switch editor tabs |
| **Include active file path in context** | Prepends the open file path to every message sent to Claude |
| **Auto-allow all file operations** | Lets Claude read/write files without confirmation (⚠ test environments only) |

---

## Usage

### Opening the Claude Chat Panel

- Toolbar: click the ![claude icon](icons/claude.png) icon
- Keyboard shortcut: `Alt+Shift+C`
- Menu: **Claude → Open Claude Chat**

### Basic Chat

1. Type your question in the input box at the bottom
2. Press `Enter` or click **Send**
3. Claude's response appears in the conversation area above

### Ask Claude about selected code

1. Select any code in a Java editor
2. Right-click → **Ask Claude about selection**
3. The selected code is automatically inserted into the input box, ready to send

### Switching Models

Use the dropdown in the top-right corner of the chat panel to switch models at any time — no restart required.

### Slash Commands

Type `/` in the input box to see available commands:

| Command | Description |
|---------|-------------|
| `/explain` | Explain the current class — purpose, design, key methods |
| `/fix` | Find and fix bugs in the current file |
| `/doc` | Add Javadoc to all public methods |
| `/test` | Generate JUnit 5 unit tests |
| `/review` | Code review — find issues and suggest improvements |
| `/refactor` | Refactor for readability and maintainability |
| `/optimize` | Identify performance bottlenecks |
| `/fields` | List all fields with their types and purpose |
| `/new` | Start a new conversation |
| `/history` | Browse past conversations |

### @ File References

Type `@` followed by a filename to include its contents as context:

```
What does @UserService.java do and how does it interact with @UserRepository.java?
```

---

## Supported Models

| Model ID | Notes |
|----------|-------|
| `claude-sonnet-4-5` | Recommended — balanced speed and capability |
| `claude-opus-4-5` | Most capable, slower |
| `claude-haiku-4-5` | Fastest, best for simple queries |
| `claude-sonnet-4-6` | Latest Sonnet |
| `claude-opus-4-6` | Opus generation 6 |
| `claude-opus-4-7` | Latest Opus — most capable |

---

## Building from Source

### Prerequisites

- JDK 21 (bundled with Eclipse 2024-06+)
- Eclipse installation (for platform JARs)

### Build Steps

```powershell
cd Claude_Code_for_eclipse

# Compile and package (uses Eclipse's bundled JDK 21)
.\build.ps1

# Compile, package, and deploy to Eclipse dropins
.\build.ps1 -Deploy

# Compile, package, deploy, and rebuild the p2 Update Site
.\build.ps1 -Deploy -UpdateSite
```

### Project Structure

```
Claude_Code_for_eclipse/
├── src/com/holtek/claudecode/
│   ├── Activator.java                            # OSGi Bundle Activator
│   ├── actions/
│   │   └── OpenChatViewAction.java               # Right-click project action
│   ├── api/
│   │   └── ClaudeCliClient.java                  # Claude CLI wrapper
│   ├── handlers/
│   │   ├── OpenChatViewHandler.java              # Open chat command handler
│   │   ├── OpenChatViewForProjectHandler.java
│   │   └── AskClaudeAboutSelectionHandler.java   # Editor selection handler
│   ├── preferences/
│   │   ├── ClaudePreferencePage.java             # Preferences UI
│   │   └── ClaudePreferenceInitializer.java      # Default values
│   └── views/
│       └── ClaudeTerminalView.java               # Main chat panel
├── feature/
│   ├── feature.xml                               # Eclipse feature descriptor
│   └── category.xml                              # p2 category
├── updatesite/                                   # Generated p2 repository
│   ├── content.jar
│   ├── artifacts.jar
│   ├── features/
│   └── plugins/
├── icons/
│   └── claude.png
├── META-INF/
│   └── MANIFEST.MF
├── plugin.xml
├── build.ps1                                     # Build script
└── README.md
```

---

## Known Limitations

- Claude CLI output is received as a complete batch (not streamed token-by-token); there is a brief wait for longer responses
- Conversation history is not persisted across Eclipse restarts
- The Update Site URL in `feature.xml` must be updated before publishing to GitHub Pages

---

## License

MIT License — contributions welcome.

---

## Acknowledgements

This plugin uses [Claude Code CLI](https://github.com/anthropics/claude-code) as its backend, developed by Anthropic.
