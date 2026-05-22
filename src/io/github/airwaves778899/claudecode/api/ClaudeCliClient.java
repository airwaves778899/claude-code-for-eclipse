package io.github.airwaves778899.claudecode.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Claude Code for Eclipse - Claude CLI Client
 *
 * Replaces AnthropicClient. Instead of calling the Anthropic REST API directly,
 * this client invokes the locally installed Claude Code CLI as a subprocess:
 *
 *   claude -p "<prompt>" --output-format stream-json [--model <model>]
 *
 * Authentication is handled entirely by the Claude Code CLI, which supports:
 *   - claude.ai OAuth login (Claude Team / Enterprise accounts)
 *   - Anthropic API Key (if configured in Claude Code CLI)
 *   - AWS Bedrock / Google Vertex AI (if configured in Claude Code CLI)
 *
 * Prerequisites:
 *   1. Install Claude Code CLI:  npm install -g @anthropic-ai/claude-code
 *   2. Login:                    claude   (opens browser for Team OAuth)
 *   3. Set CLI path in Eclipse:  Window > Preferences > Claude Code
 *
 * Stream-JSON format (one JSON object per line):
 *   {"type":"system",    "subtype":"init", ...}
 *   {"type":"assistant", "message":{"content":[{"type":"text","text":"Hello"}]}, ...}
 *   {"type":"result",    "subtype":"success", "result":"Hello", ...}
 */
public class ClaudeCliClient {

    // ── Constants ─────────────────────────────────────────────────────────────

    /** System context prepended to every prompt. */
    private static final String SYSTEM_CONTEXT =
        "You are Claude Code, an AI coding assistant embedded in Eclipse IDE. " +
        "Help the user with Java development, debugging, code review, and architecture. " +
        "When showing code use proper formatting. Be concise but thorough. " +
        "IMPORTANT: Always respond in Traditional Chinese (zh-TW) unless the user explicitly writes in another language. " +
        "Never use Simplified Chinese.";

    /** Max characters of conversation history to include in the prompt. */
    private static final int MAX_HISTORY_CHARS = 20_000;

    /** Pattern to extract text from stream-json "assistant" events. */
    private static final Pattern TEXT_PATTERN =
            Pattern.compile("\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"");

    // ── Fields ────────────────────────────────────────────────────────────────

    private final String cliPath;   // e.g., "claude" or "C:\\...\\claude.cmd"
    private final String model;     // optional --model override, may be empty

    // ── Constructor ───────────────────────────────────────────────────────────

    public ClaudeCliClient(String cliPath, String model) {
        this.cliPath = (cliPath != null && !cliPath.isBlank()) ? cliPath.trim() : "claude";
        this.model   = (model   != null && !model.isBlank())   ? model.trim()   : "";
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Send the conversation to Claude CLI and stream the response.
     * Runs on a background thread; all callbacks arrive on that thread —
     * use Display.asyncExec() inside StreamCallback for SWT UI updates.
     *
     * @param messages  conversation history (user / assistant alternating)
     * @param callback  receives onToken / onComplete / onError
     */
    public void sendStream(List<ChatMessage> messages, StreamCallback callback) {
        new Thread(() -> {
            try {
                doSendStream(messages, callback);
            } catch (Exception e) {
                callback.onError(e);
            }
        }, "claude-cli-thread").start();
    }

    /**
     * Check whether the Claude CLI is reachable at the configured path.
     * Runs the CLI with --version and returns true on exit code 0.
     */
    public boolean isAvailable() {
        return isCliAvailable(cliPath);
    }

    /** Static version — can be called before creating an instance. */
    public static boolean isCliAvailable(String path) {
        if (path == null || path.isBlank()) return false;
        try {
            List<String> cmd = buildBaseCommand(path);
            cmd.add("--version");
            Process p = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
            p.waitFor();
            return true;   // version flag exists → CLI found
        } catch (Exception e) {
            return false;
        }
    }

    /** Return the CLI version string, or "Not found" if not available. */
    public static String getVersion(String path) {
        if (!isCliAvailable(path)) return "Not found";
        try {
            List<String> cmd = buildBaseCommand(path);
            cmd.add("--version");
            Process p = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
            String ver = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))
                    .readLine();
            p.waitFor();
            return ver != null ? ver.trim() : "Installed";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // ── Core stream implementation ────────────────────────────────────────────

    private void doSendStream(List<ChatMessage> messages, StreamCallback callback)
            throws Exception {

        String prompt = buildPrompt(messages);
        List<String> cmd = buildCommand(prompt);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);

        Process process = pb.start();

        // ── Read stderr on daemon thread to prevent pipe blocking ─────────────
        StringBuilder stderrBuf = new StringBuilder();
        Thread stderrReader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    stderrBuf.append(line).append("\n");
                }
            } catch (IOException ignored) {}
        }, "claude-cli-stderr");
        stderrReader.setDaemon(true);
        stderrReader.start();

        // ── Read stdout and parse stream-json ─────────────────────────────────
        StringBuilder fullResponse = new StringBuilder();
        String lastAssistantText   = "";   // track accumulated text for delta calc

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("{")) {
                    // ── JSON event ────────────────────────────────────────────
                    if (line.contains("\"type\":\"assistant\"")) {
                        // Extract accumulated text from assistant event
                        String currentText = extractLastTextField(line);
                        if (currentText != null && currentText.length() > lastAssistantText.length()) {
                            String delta = currentText.substring(lastAssistantText.length());
                            lastAssistantText = currentText;
                            fullResponse.append(delta);
                            callback.onToken(delta);
                        }

                    } else if (line.contains("\"type\":\"result\"")) {
                        // Final result event — use it if we got no streaming content
                        if (fullResponse.isEmpty()) {
                            String result = extractJsonFieldValue(line, "result");
                            if (result != null && !result.isBlank()) {
                                fullResponse.append(result);
                                callback.onToken(result);
                            }
                        }
                        // Stream is done
                        break;

                    } else if (line.contains("\"is_error\":true") ||
                               line.contains("\"subtype\":\"error\"")) {
                        String msg = extractJsonFieldValue(line, "message");
                        if (msg == null) msg = extractJsonFieldValue(line, "error");
                        throw new RuntimeException(buildCliError(msg));
                    }
                    // Ignore: system / user / init events

                } else {
                    // ── Plain text fallback (--output-format text) ─────────────
                    fullResponse.append(line).append("\n");
                    callback.onToken(line + "\n");
                }
            }
        }

        int exitCode = process.waitFor();
        stderrReader.join(3000);

        if (exitCode != 0 && fullResponse.isEmpty()) {
            throw new RuntimeException(
                buildCliError(stderrBuf.toString().trim()));
        }

        callback.onComplete(fullResponse.toString());
    }

    // ── Prompt builder ────────────────────────────────────────────────────────

    /**
     * Combine conversation history into a single prompt string.
     * The Claude Code CLI -p flag accepts a single string — we embed
     * the full history so Claude has conversation context.
     */
    private static String buildPrompt(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append(SYSTEM_CONTEXT).append("\n\n");

        // Include history (all but the last user message)
        if (messages.size() > 1) {
            sb.append("[Conversation history]\n");
            int historyCount = messages.size() - 1;
            // Truncate if too long
            List<ChatMessage> history = messages.subList(0, historyCount);
            String historyText = formatHistory(history);
            if (historyText.length() > MAX_HISTORY_CHARS) {
                historyText = "... [earlier messages omitted]\n" +
                              historyText.substring(historyText.length() - MAX_HISTORY_CHARS);
            }
            sb.append(historyText).append("\n");
        }

        // Current user message
        ChatMessage last = messages.get(messages.size() - 1);
        if (messages.size() > 1) sb.append("[Current question]\n");
        sb.append(last.getContent());

        return sb.toString();
    }

    private static String formatHistory(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : messages) {
            String role = "user".equals(m.getRole()) ? "User" : "Assistant";
            sb.append(role).append(": ").append(m.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    // ── Command builder ───────────────────────────────────────────────────────

    private List<String> buildCommand(String prompt) {
        List<String> cmd = buildBaseCommand(cliPath);
        cmd.add("-p");
        cmd.add(prompt);
        cmd.add("--output-format");
        cmd.add("stream-json");
        if (!model.isEmpty()) {
            cmd.add("--model");
            cmd.add(model);
        }
        return cmd;
    }

    /**
     * Build the base command list for Windows / Unix compatibility.
     * On Windows "claude" might be installed as "claude.cmd".
     */
    private static List<String> buildBaseCommand(String cliPath) {
        List<String> cmd = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            // On Windows, resolve "claude" → "claude.cmd" from PATH to avoid
            // cmd.exe /c wrapping (which mishandles special chars in prompts).
            String resolved = resolveWindowsCli(cliPath);
            cmd.add(resolved);
        } else {
            cmd.add(cliPath);
        }
        return cmd;
    }

    /**
     * On Windows, find the actual .cmd file for a bare command name like "claude".
     * Returns the full path (e.g. C:\Users\...\AppData\Roaming\npm\claude.cmd)
     * so ProcessBuilder can launch it directly without cmd.exe /c.
     */
    private static String resolveWindowsCli(String cliPath) {
        // Already a full path or explicit .cmd / .exe — use as-is
        if (cliPath.contains("\\") || cliPath.contains("/") ||
            cliPath.endsWith(".cmd") || cliPath.endsWith(".exe")) {
            return cliPath;
        }
        // Try resolving via WHERE command (check exit code — WHERE prints INFO on failure)
        try {
            Process p = new ProcessBuilder("cmd.exe", "/c", "where", cliPath + ".cmd")
                    .redirectErrorStream(false).start();
            String line = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(),
                            java.nio.charset.StandardCharsets.UTF_8))
                    .readLine();
            int exit = p.waitFor();
            if (exit == 0 && line != null && !line.isBlank()) return line.trim();
        } catch (Exception ignored) {}
        // Fallback: common npm global path
        String appData = System.getenv("APPDATA");
        if (appData != null) {
            java.io.File f = new java.io.File(appData + "\\npm\\" + cliPath + ".cmd");
            if (f.exists()) return f.getAbsolutePath();
        }
        // Last resort: wrap with cmd.exe
        return cliPath;
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    /**
     * Extract the LAST "text":"..." value in a JSON line.
     * In assistant events the last text field is the response content.
     */
    private static String extractLastTextField(String json) {
        Matcher m = TEXT_PATTERN.matcher(json);
        String last = null;
        while (m.find()) last = m.group(1);
        return last != null ? unescapeJson(last) : null;
    }

    /**
     * Extract a top-level field value like "result":"..." or "message":"...".
     */
    private static String extractJsonFieldValue(String json, String field) {
        Pattern p = Pattern.compile("\"" + field + "\":\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? unescapeJson(m.group(1)) : null;
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"':  out.append('"');  i += 2; continue;
                    case '\\': out.append('\\'); i += 2; continue;
                    case '/':  out.append('/');  i += 2; continue;
                    case 'n':  out.append('\n'); i += 2; continue;
                    case 'r':  out.append('\r'); i += 2; continue;
                    case 't':  out.append('\t'); i += 2; continue;
                    case 'u':
                        if (i + 5 < s.length()) {
                            try {
                                out.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                                i += 6; continue;
                            } catch (NumberFormatException ignored) {}
                        }
                        break;
                    default: break;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    // ── Error messages ────────────────────────────────────────────────────────

    private static String buildCliError(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Claude CLI execution failed (no error message).\n" +
                   "Please verify:\n" +
                   "  1. Claude Code CLI is installed: npm install -g @anthropic-ai/claude-code\n" +
                   "  2. You are logged in: run claude in a terminal (browser will open)\n" +
                   "  3. CLI path is correctly set: Window > Preferences > Claude Code";
        }
        if (raw.contains("not logged in") || raw.contains("unauthenticated") ||
            raw.contains("Please login") || raw.contains("401")) {
            return "Not logged in to Claude Code CLI.\n" +
                   "Run in a terminal: claude\n" +
                   "A browser will open — log in with your Claude Team account.";
        }
        if (raw.contains("command not found") || raw.contains("不是內部或外部命令") ||
            raw.contains("cannot find") || raw.contains("No such file")) {
            return "Claude Code CLI not found.\n" +
                   "Install: npm install -g @anthropic-ai/claude-code\n" +
                   "Or set the correct path at Window > Preferences > Claude Code.";
        }
        return "Claude CLI error:\n" + raw;
    }
}
