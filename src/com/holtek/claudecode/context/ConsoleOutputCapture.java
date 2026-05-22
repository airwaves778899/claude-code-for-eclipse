package com.holtek.claudecode.context;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.TextConsole;

/**
 * Claude Code for Eclipse - Console Output Capture (Phase 5)
 *
 * Reads the content of Eclipse console views (Run/Debug output).
 * Useful for capturing stack traces and runtime errors to send to Claude.
 */
public final class ConsoleOutputCapture {

    /** Maximum lines to capture from console (avoid flooding Claude's context). */
    public static final int DEFAULT_MAX_LINES = 150;

    /** Maximum characters of console content to include in a prompt. */
    public static final int MAX_CHARS = 8000;

    private ConsoleOutputCapture() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the text content of the most recently active Eclipse console,
     * limited to the last {@code maxLines} lines.
     *
     * @param maxLines  maximum number of lines to return
     * @return console text, or an empty string if no console is open
     */
    public static String getLatestOutput(int maxLines) {
        IConsoleManager mgr = getConsoleManager();
        if (mgr == null) return "";

        IConsole[] consoles = mgr.getConsoles();
        if (consoles == null || consoles.length == 0) return "";

        // Prefer the last (most recently added) console
        for (int i = consoles.length - 1; i >= 0; i--) {
            IConsole console = consoles[i];
            if (console instanceof TextConsole) {
                String text = ((TextConsole) console).getDocument().get();
                if (text != null && !text.isBlank()) {
                    return lastNLines(text.trim(), maxLines);
                }
            }
        }
        return "";
    }

    /**
     * Returns the text of ALL open consoles, separated by headers.
     *
     * @param maxLinesEach  max lines per console
     */
    public static String getAllConsoleOutput(int maxLinesEach) {
        IConsoleManager mgr = getConsoleManager();
        if (mgr == null) return "";

        IConsole[] consoles = mgr.getConsoles();
        if (consoles == null || consoles.length == 0) return "";

        StringBuilder sb = new StringBuilder();
        for (IConsole c : consoles) {
            if (c instanceof TextConsole) {
                String text = ((TextConsole) c).getDocument().get();
                if (text != null && !text.isBlank()) {
                    sb.append("### Console: ").append(c.getName()).append("\n");
                    sb.append(lastNLines(text.trim(), maxLinesEach)).append("\n\n");
                }
            }
        }
        return sb.toString().trim();
    }

    /**
     * Returns console names for display in a selection UI.
     */
    public static List<String> getConsoleNames() {
        IConsoleManager mgr = getConsoleManager();
        List<String> names = new ArrayList<>();
        if (mgr == null) return names;
        IConsole[] consoles = mgr.getConsoles();
        if (consoles == null) return names;
        for (IConsole c : consoles) {
            names.add(c.getName());
        }
        return names;
    }

    /**
     * True if at least one console is open and has content.
     */
    public static boolean hasConsoleOutput() {
        IConsoleManager mgr = getConsoleManager();
        if (mgr == null) return false;
        IConsole[] consoles = mgr.getConsoles();
        if (consoles == null) return false;
        for (IConsole c : consoles) {
            if (c instanceof TextConsole) {
                String t = ((TextConsole) c).getDocument().get();
                if (t != null && !t.isBlank()) return true;
            }
        }
        return false;
    }

    /**
     * Try to extract a stack trace from the console output.
     * Returns the detected stack trace block, or the full output if none found.
     */
    public static String extractStackTrace(String consoleText) {
        if (consoleText == null || consoleText.isBlank()) return "";

        // Look for common exception patterns
        String[] exceptionMarkers = {
            "Exception in thread",
            "Caused by:",
            "java.lang.",
            "org.springframework.",
            "com.holtek.",
            "ERROR",
            "FATAL"
        };

        String[] lines = consoleText.split("\n");
        int startLine = -1;

        for (int i = 0; i < lines.length; i++) {
            for (String marker : exceptionMarkers) {
                if (lines[i].contains(marker)) {
                    startLine = Math.max(0, i - 3);  // include 3 lines before exception
                    break;
                }
            }
            if (startLine >= 0) break;
        }

        if (startLine < 0) {
            // No exception found — return last portion
            return lastNLines(consoleText, 60);
        }

        // Capture from startLine onwards (up to MAX_CHARS)
        StringBuilder sb = new StringBuilder();
        for (int i = startLine; i < lines.length; i++) {
            sb.append(lines[i]).append("\n");
            if (sb.length() > MAX_CHARS) {
                sb.append("... [截斷]");
                break;
            }
        }
        return sb.toString().trim();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static IConsoleManager getConsoleManager() {
        try {
            ConsolePlugin plugin = ConsolePlugin.getDefault();
            return plugin != null ? plugin.getConsoleManager() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Return the last {@code n} lines of {@code text}.
     */
    static String lastNLines(String text, int n) {
        if (text == null || text.isEmpty()) return "";
        String[] lines = text.split("\n", -1);
        if (lines.length <= n) {
            return text.length() > MAX_CHARS
                    ? "... [截斷]\n" + text.substring(text.length() - MAX_CHARS)
                    : text;
        }
        StringBuilder sb = new StringBuilder();
        if (lines.length > n) {
            sb.append("... [前 ").append(lines.length - n).append(" 行已省略]\n");
        }
        for (int i = lines.length - n; i < lines.length; i++) {
            sb.append(lines[i]).append("\n");
        }
        String result = sb.toString();
        return result.length() > MAX_CHARS
                ? "... [截斷]\n" + result.substring(result.length() - MAX_CHARS)
                : result;
    }
}
