package com.holtek.claudecode.terminal;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility: launch Claude Code CLI in a real terminal window.
 *
 * Strategy (Windows):
 *   1. Windows Terminal (wt.exe)  — new-tab with -d <workDir>
 *   2. cmd.exe fallback           — start cmd /k "cd /d <workDir> && claude"
 */
public final class TerminalLauncher {

    private TerminalLauncher() {}

    /**
     * Open Claude interactively in a new terminal window at workDir.
     */
    public static void launch(String workDir, String claude) throws IOException {
        if (tryWindowsTerminal(workDir, claude, null, null)) return;
        tryCmdFallback(workDir, claude, null, null);
    }

    /**
     * Open Claude with an initial prompt/file argument.
     * e.g. "src/com/Foo.java" → claude src/com/Foo.java
     */
    public static void launchWithArg(String workDir, String claude, String arg) throws IOException {
        if (tryWindowsTerminal(workDir, claude, arg, null)) return;
        tryCmdFallback(workDir, claude, arg, null);
    }

    /**
     * Open Claude with multiple project directories via --add-dir.
     *
     * @param workDir   Main working directory (primary project)
     * @param claude    Resolved path to claude.cmd
     * @param addDirs   Additional project paths (each becomes --add-dir)
     */
    public static void launchWithProjects(String workDir, String claude,
                                          java.util.List<String> addDirs) throws IOException {
        if (tryWindowsTerminal(workDir, claude, null, addDirs)) return;
        tryCmdFallback(workDir, claude, null, addDirs);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private static boolean tryWindowsTerminal(String workDir, String claude,
                                               String arg, java.util.List<String> addDirs)
            throws IOException {
        String wt = findWindowsTerminal();
        if (wt == null) return false;

        List<String> cmd = new ArrayList<>();
        cmd.add(wt);
        cmd.add("new-tab");
        cmd.add("-d");
        cmd.add(workDir);
        cmd.add("--");
        cmd.add("cmd.exe");
        cmd.add("/k");
        cmd.add(buildClaudeCmd(claude, arg, addDirs));

        new ProcessBuilder(cmd).start();
        return true;
    }

    private static void tryCmdFallback(String workDir, String claude,
                                        String arg, java.util.List<String> addDirs)
            throws IOException {
        String inner = "cd /d \"" + workDir + "\" && " + buildClaudeCmd(claude, arg, addDirs);
        new ProcessBuilder("cmd.exe", "/c", "start", "cmd.exe", "/k", inner).start();
    }

    /** Build: claude ["arg"] [--add-dir "dir1" --add-dir "dir2" ...] */
    private static String buildClaudeCmd(String claude, String arg,
                                          java.util.List<String> addDirs) {
        StringBuilder sb = new StringBuilder("\"").append(claude).append("\"");
        if (arg != null && !arg.isBlank()) {
            sb.append(" \"").append(arg).append("\"");
        }
        if (addDirs != null) {
            for (String d : addDirs) {
                if (d != null && !d.isBlank())
                    sb.append(" --add-dir \"").append(d).append("\"");
            }
        }
        return sb.toString();
    }

    // ── Windows Terminal detection ─────────────────────────────────────────────

    public static String findWindowsTerminal() {
        // Common install location (Microsoft Store)
        String local = System.getenv("LOCALAPPDATA");
        if (local != null) {
            File wt = new File(local + "\\Microsoft\\WindowsApps\\wt.exe");
            if (wt.exists()) return wt.getAbsolutePath();
        }
        // PATH lookup
        try {
            Process p = new ProcessBuilder("cmd.exe", "/c", "where", "wt.exe")
                    .redirectErrorStream(false).start();
            String line = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(),
                            java.nio.charset.StandardCharsets.UTF_8))
                    .readLine();
            int exit = p.waitFor();
            if (exit == 0 && line != null && !line.isBlank()) return line.trim();
        } catch (Exception ignored) {}
        return null;
    }
}
