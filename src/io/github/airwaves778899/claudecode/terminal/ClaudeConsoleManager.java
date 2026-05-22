package io.github.airwaves778899.claudecode.terminal;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.ui.console.*;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.IWorkbenchPage;

import io.github.airwaves778899.claudecode.Activator;

/**
 * Manages an interactive Claude Code CLI session inside Eclipse's Console view.
 *
 * Architecture (REPL loop):
 *   1. Show "You: " prompt in the Console
 *   2. Read a line of input from the user
 *   3. Run: claude -p "<input>" --output-format stream-json [--continue]
 *   4. Stream the response back to the Console
 *   5. Repeat
 *
 * The --continue flag (from the 2nd message onward) tells the Claude CLI to
 * resume the most recent conversation session, preserving context.
 *
 * This approach avoids the need for a PTY (pseudo-terminal). Java's
 * ProcessBuilder creates a pipe for stdin, which causes the Claude CLI to
 * enter --print mode — exactly what we want here.
 */
public class ClaudeConsoleManager {

    private static final String CONSOLE_NAME = "Claude Code Terminal";
    private static final String CONSOLE_TYPE  = "io.github.airwaves778899.claudecode.terminal";

    private static ClaudeConsoleManager instance;

    private IOConsole         console;
    private Thread            replThread;
    private volatile boolean  running = false;

    /** Pattern to extract the last "text":"..." value from a stream-json line. */
    private static final Pattern TEXT_PATTERN =
            Pattern.compile("\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"");

    // ── Singleton ──────────────────────────────────────────────────────────────

    public static synchronized ClaudeConsoleManager getInstance() {
        if (instance == null) instance = new ClaudeConsoleManager();
        return instance;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public void openTerminal() {
        if (running) {
            showConsole();
            return;
        }
        startSession();
    }

    public boolean isRunning() { return running; }

    public void stopSession() {
        running = false;
        if (replThread != null) replThread.interrupt();
    }

    // ── Session startup ────────────────────────────────────────────────────────

    private void startSession() {
        try {
            String cliPath = Activator.getPref(Activator.PREF_CLI_PATH);
            if (cliPath == null || cliPath.isBlank()) cliPath = "claude";
            final String resolved = resolveCli(cliPath);

            console = findOrCreateConsole();
            console.clearConsole();
            IOConsoleOutputStream out = console.newOutputStream();

            writeInfo(out, "╔══════════════════════════════════════════════════════╗\n");
            writeInfo(out, "║            Claude Code Terminal                      ║\n");
            writeInfo(out, "╚══════════════════════════════════════════════════════╝\n");
            writeInfo(out, "CLI: " + resolved + "\n");
            writeInfo(out, "Type your message and press Enter.\n");
            writeInfo(out, "Type 'exit' or 'quit' to close. Type 'new' to start a fresh conversation.\n");
            writeInfo(out, "──────────────────────────────────────────────────────\n\n");

            showConsole();

            replThread = new Thread(() -> runRepl(out, resolved), "claude-repl");
            replThread.setDaemon(true);
            replThread.start();
            running = true;

        } catch (Exception e) {
            System.err.println("[ClaudeConsole] Failed to start: " + e.getMessage());
        }
    }

    // ── REPL loop ──────────────────────────────────────────────────────────────

    private void runRepl(IOConsoleOutputStream out, String resolved) {
        try {
            InputStream  consoleIn = console.getInputStream();
            BufferedReader reader  = new BufferedReader(
                    new InputStreamReader(consoleIn, StandardCharsets.UTF_8));

            boolean firstMessage = true;
            writeInfo(out, "You: ");

            String line;
            while (running && (line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty()) {
                    writeInfo(out, "You: ");
                    continue;
                }
                if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
                    writeInfo(out, "\nGoodbye!\n");
                    break;
                }
                if ("new".equalsIgnoreCase(line)) {
                    firstMessage = true;
                    writeInfo(out, "\n[New conversation started]\n\nYou: ");
                    continue;
                }

                writeInfo(out, "\nClaude: ");
                runQuery(out, resolved, line, !firstMessage);
                firstMessage = false;
                writeInfo(out, "\n\nYou: ");
            }
        } catch (IOException ignored) {
        } finally {
            running = false;
            writeInfo(null, "");   // no-op, session ended
        }
    }

    // ── Single query ───────────────────────────────────────────────────────────

    private void runQuery(IOConsoleOutputStream out,
                          String resolved,
                          String prompt,
                          boolean continueSession) {
        try {
            List<String> cmd = buildCmd(resolved);
            cmd.add("-p");
            cmd.add(prompt);
            cmd.add("--output-format");
            cmd.add("stream-json");
            if (continueSession) {
                cmd.add("--continue");
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("NO_COLOR",    "1");
            pb.environment().put("FORCE_COLOR", "0");
            pb.environment().put("TERM",        "dumb");
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder lastText = new StringBuilder();

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String jsonLine;
                while ((jsonLine = br.readLine()) != null) {
                    jsonLine = jsonLine.trim();
                    if (jsonLine.isEmpty()) continue;

                    if (jsonLine.startsWith("{")) {
                        if (jsonLine.contains("\"type\":\"assistant\"")) {
                            String text = extractLastTextField(jsonLine);
                            if (text != null && text.length() > lastText.length()) {
                                String delta = text.substring(lastText.length());
                                lastText = new StringBuilder(text);
                                writeRaw(out, stripAnsi(delta));
                            }
                        } else if (jsonLine.contains("\"type\":\"result\"")) {
                            if (lastText.length() == 0) {
                                // fallback: use result field
                                String result = extractField(jsonLine, "result");
                                if (result != null && !result.isBlank()) {
                                    writeRaw(out, stripAnsi(result));
                                }
                            }
                            break;
                        } else if (jsonLine.contains("\"is_error\":true")) {
                            String msg = extractField(jsonLine, "message");
                            if (msg == null) msg = extractField(jsonLine, "error");
                            writeInfo(out, "\n[Error] " + (msg != null ? msg : "unknown error") + "\n");
                            break;
                        }
                    } else {
                        // Plain text fallback
                        writeRaw(out, stripAnsi(jsonLine) + "\n");
                    }
                }
            }

            process.waitFor();

        } catch (Exception e) {
            writeInfo(out, "\n[Error] " + e.getMessage() + "\n");
        }
    }

    // ── JSON helpers ───────────────────────────────────────────────────────────

    private static String extractLastTextField(String json) {
        Matcher m = TEXT_PATTERN.matcher(json);
        String last = null;
        while (m.find()) last = m.group(1);
        return last != null ? unescapeJson(last) : null;
    }

    private static String extractField(String json, String field) {
        Pattern p = Pattern.compile("\"" + field + "\":\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? unescapeJson(m.group(1)) : null;
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                switch (n) {
                    case '"':  sb.append('"');  i += 2; continue;
                    case '\\': sb.append('\\'); i += 2; continue;
                    case '/':  sb.append('/');  i += 2; continue;
                    case 'n':  sb.append('\n'); i += 2; continue;
                    case 'r':  sb.append('\r'); i += 2; continue;
                    case 't':  sb.append('\t'); i += 2; continue;
                    case 'u':
                        if (i + 5 < s.length()) {
                            try {
                                sb.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                                i += 6; continue;
                            } catch (NumberFormatException ignored) {}
                        }
                        break;
                    default: break;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    // ── ANSI stripper ──────────────────────────────────────────────────────────

    private static final Pattern ANSI_PATTERN = Pattern.compile(
            "(?:[@-Z\\\\-_]|\\[[0-?]*[ -/]*[@-~])");

    private static String stripAnsi(String s) {
        return ANSI_PATTERN.matcher(s).replaceAll("");
    }

    // ── Command builder ────────────────────────────────────────────────────────

    private static List<String> buildCmd(String resolved) {
        List<String> cmd = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win") && resolved.endsWith(".cmd")) {
            cmd.add("cmd.exe");
            cmd.add("/c");
        }
        cmd.add(resolved);
        return cmd;
    }

    /**
     * Resolve "claude" → full path of claude.cmd on Windows.
     */
    public static String resolveCli(String cliPath) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) return cliPath;
        if (cliPath.contains("\\") || cliPath.endsWith(".cmd") || cliPath.endsWith(".exe"))
            return cliPath;
        // Try WHERE (check exit code — WHERE prints an INFO message on failure)
        try {
            Process p = new ProcessBuilder("cmd.exe", "/c", "where", cliPath + ".cmd")
                    .redirectErrorStream(false).start();
            String line = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))
                    .readLine();
            int exit = p.waitFor();
            if (exit == 0 && line != null && !line.isBlank()) return line.trim();
        } catch (Exception ignored) {}
        // Fallback: APPDATA\npm
        String appData = System.getenv("APPDATA");
        if (appData != null) {
            File f = new File(appData + "\\npm\\" + cliPath + ".cmd");
            if (f.exists()) return f.getAbsolutePath();
        }
        return cliPath;
    }

    // ── Console management ─────────────────────────────────────────────────────

    private IOConsole findOrCreateConsole() {
        IConsoleManager mgr = ConsolePlugin.getDefault().getConsoleManager();
        for (IConsole c : mgr.getConsoles()) {
            if (CONSOLE_TYPE.equals(c.getType())) return (IOConsole) c;
        }
        IOConsole c = new IOConsole(CONSOLE_NAME, CONSOLE_TYPE, null,
                StandardCharsets.UTF_8.name(), true);
        mgr.addConsoles(new IConsole[]{ c });
        return c;
    }

    private void showConsole() {
        if (console == null) return;
        ConsolePlugin.getDefault().getConsoleManager().showConsoleView(console);
        try {
            IWorkbenchPage page = PlatformUI.getWorkbench()
                    .getActiveWorkbenchWindow().getActivePage();
            page.showView(IConsoleConstants.ID_CONSOLE_VIEW);
        } catch (Exception ignored) {}
    }

    private static void writeInfo(OutputStream out, String msg) {
        if (out == null) return;
        try {
            out.write(msg.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {}
    }

    private static void writeRaw(OutputStream out, String text) {
        if (out == null || text == null || text.isEmpty()) return;
        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {}
    }
}
