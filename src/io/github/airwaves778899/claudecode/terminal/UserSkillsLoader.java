package io.github.airwaves778899.claudecode.terminal;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads user-defined slash commands from ~/.claude/eclipse-skills.json.
 *
 * File format:
 * <pre>
 * [
 *   {
 *     "command":     "/mycommand",
 *     "description": "Short description shown in popup",
 *     "prompt":      "Prompt sent to Claude. Use {file} for the open file path."
 *   }
 * ]
 * </pre>
 *
 * The file is re-read on every popup open, so changes take effect immediately
 * without restarting Eclipse.
 */
public class UserSkillsLoader {

    /** Default location: ~/.claude/eclipse-skills.json */
    private static final String DEFAULT_PATH =
            System.getProperty("user.home").replace('\\', '/') + "/.claude/eclipse-skills.json";

    /** Pattern to find JSON objects inside the top-level array */
    private static final Pattern OBJ_PAT =
            Pattern.compile("\\{(.*?)\\}", Pattern.DOTALL);

    /** Pattern to extract string key-value pairs inside a JSON object */
    private static final Pattern KV_PAT =
            Pattern.compile("\"(command|description|prompt)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Load skills from the default location.
     * Returns an empty list (not null) if the file is missing or invalid.
     * Each entry is String[3]: { command, description, prompt-template }.
     * The prompt-template uses %s as the file placeholder (safe for String.format).
     */
    public static List<String[]> load() {
        return loadFrom(DEFAULT_PATH);
    }

    public static List<String[]> loadFrom(String path) {
        List<String[]> result = new ArrayList<>();
        File f = new File(path);
        if (!f.exists() || !f.canRead()) return result;
        try {
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            parseArray(json, result);
        } catch (Exception ignored) {
            // Silently skip parse errors — user will notice commands are missing
        }
        return result;
    }

    /** Absolute path to the default skills file. */
    public static String getDefaultPath() {
        return DEFAULT_PATH;
    }

    /**
     * Create a sample skills file at the default location if it does not exist.
     * Does nothing if the file already exists.
     */
    public static void createSampleIfAbsent() {
        File f = new File(DEFAULT_PATH);
        if (f.exists()) return;
        try {
            f.getParentFile().mkdirs();
            String sample =
                "[\n" +
                "  {\n" +
                "    \"command\":     \"/analyze\",\n" +
                "    \"description\": \"Analyze complexity and suggest improvements\",\n" +
                "    \"prompt\":      \"[Context: open file: {file}]\\n\\nPlease analyze this code for complexity, readability, and maintainability. Provide specific improvement suggestions with examples.\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"command\":     \"/todo\",\n" +
                "    \"description\": \"List all TODO / FIXME comments as a task list\",\n" +
                "    \"prompt\":      \"[Context: open file: {file}]\\n\\nPlease find all TODO, FIXME, HACK and similar comments in this code. Create a prioritized task list with context for each item.\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"command\":     \"/security\",\n" +
                "    \"description\": \"Security audit — find vulnerabilities\",\n" +
                "    \"prompt\":      \"[Context: open file: {file}]\\n\\nPlease perform a security audit of this code. Check for: SQL injection, XSS, insecure deserialization, hard-coded credentials, improper error handling, and any other vulnerabilities.\"\n" +
                "  }\n" +
                "]\n";
            Files.write(f.toPath(), sample.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Internal parsing (no external JSON library required)
    // ──────────────────────────────────────────────────────────────────────────

    private static void parseArray(String json, List<String[]> out) {
        Matcher objM = OBJ_PAT.matcher(json);
        while (objM.find()) {
            String body    = objM.group(1);
            String command = null, desc = null, prompt = null;

            Matcher kvM = KV_PAT.matcher(body);
            while (kvM.find()) {
                String key = kvM.group(1);
                String val = unescape(kvM.group(2));
                switch (key) {
                    case "command":     command = val.trim(); break;
                    case "description": desc    = val.trim(); break;
                    case "prompt":      prompt  = val;        break;
                }
            }

            if (command != null && !command.isEmpty()
                    && desc != null && !desc.isEmpty()
                    && prompt != null) {
                // Make prompt safe for String.format:
                // 1. escape existing % as %%
                // 2. replace {file} → %s
                String template = prompt
                        .replace("%", "%%")
                        .replace("{file}", "%s");
                out.add(new String[]{ command, desc, template });
            }
        }
    }

    /** Unescape JSON string escape sequences. */
    private static String unescape(String s) {
        return s.replace("\\n",  "\n")
                .replace("\\t",  "\t")
                .replace("\\r",  "\r")
                .replace("\\\"", "\"")
                .replace("\\\\/", "/")
                .replace("\\\\", "\\");
    }
}
