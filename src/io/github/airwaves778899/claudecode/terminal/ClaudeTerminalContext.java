package io.github.airwaves778899.claudecode.terminal;

import java.util.Collections;
import java.util.List;

/**
 * Static context holder — passes configuration from OpenClaudeTerminalHandler
 * to ClaudeTerminalView / ClaudeTerminalWindow without needing constructor params.
 *
 * Usage:
 *   Handler:  ClaudeTerminalContext.set(workDir, addDirs, claude);
 *   View:     ClaudeTerminalContext.getWorkDir() etc.
 */
public final class ClaudeTerminalContext {

    private ClaudeTerminalContext() {}

    private static volatile String       workDir   = System.getProperty("user.home");
    private static volatile List<String> addDirs   = Collections.emptyList();
    private static volatile String       claudeExe = "claude";

    public static void set(String wd, List<String> dirs, String exe) {
        workDir   = wd   != null ? wd   : System.getProperty("user.home");
        addDirs   = dirs != null ? dirs : Collections.emptyList();
        claudeExe = exe  != null ? exe  : "claude";
    }

    public static String       getWorkDir()   { return workDir;   }
    public static List<String> getAddDirs()   { return addDirs;   }
    public static String       getClaudeExe() { return claudeExe; }
}
