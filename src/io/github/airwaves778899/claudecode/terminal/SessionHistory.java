package io.github.airwaves778899.claudecode.terminal;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Manages conversation history for the Claude Terminal.
 * Sessions are persisted in JSON Lines format at ~/.claude-eclipse/history.jsonl.
 *
 * Each line contains one conversation turn:
 *   {"role":"user"|"claude","text":"...","ts":"HH:mm","session":"<sessionId>"}
 *
 * Session IDs are timestamp strings in yyyyMMdd_HHmmss format.
 */
public class SessionHistory {

    private static final String HISTORY_FILE =
            System.getProperty("user.home") + "/.claude-eclipse/history.jsonl";

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Start a new session and return its ID (yyyyMMdd_HHmmss).
     */
    public static synchronized String newSession() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        return sdf.format(new Date());
    }

    /**
     * Append one conversation turn to persistent storage.
     *
     * @param sessionId  session identifier from {@link #newSession()}
     * @param role       "user" or "claude"
     * @param text       message content
     * @param timestamp  display timestamp, e.g. "14:30"
     */
    public static synchronized void append(String sessionId, String role, String text, String timestamp) {
        try {
            Path path = Paths.get(HISTORY_FILE);
            Files.createDirectories(path.getParent());

            String line = buildJson(sessionId, role, text, timestamp);
            Files.write(path, (line + "\n").getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Best-effort; do not crash the UI on history write failure
            e.printStackTrace();
        }
    }

    /**
     * Return summaries for the most-recent {@code maxCount} unique sessions.
     *
     * Each map contains:
     *   "id"      – session ID, e.g. "20260521_143000"
     *   "preview" – first user message in that session (truncated to 80 chars)
     *   "date"    – human-readable date/time, e.g. "2026-05-21 14:30"
     *
     * @param maxCount maximum number of session summaries to return
     */
    public static synchronized List<Map<String, String>> recentSessions(int maxCount) {
        List<Map<String, String>> result = new ArrayList<>();
        Path path = Paths.get(HISTORY_FILE);
        if (!Files.exists(path)) {
            return result;
        }

        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);

            // Maintain insertion-order map: sessionId -> first user message line
            LinkedHashMap<String, String> firstUserMsg = new LinkedHashMap<>();

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                Map<String, String> turn = parseLine(line);
                if (turn == null) continue;

                String sid = turn.get("session");
                if (sid == null || sid.isEmpty()) continue;

                // Record first user message for this session (for preview)
                if ("user".equals(turn.get("role")) && !firstUserMsg.containsKey(sid)) {
                    firstUserMsg.put(sid, turn.get("text"));
                } else if (!firstUserMsg.containsKey(sid)) {
                    // ensure the session key exists even without a user message
                    firstUserMsg.put(sid, "");
                }
            }

            // Collect unique session IDs in reverse order (most recent last = end of file)
            List<String> sessionIds = new ArrayList<>(firstUserMsg.keySet());
            Collections.reverse(sessionIds);

            for (String sid : sessionIds) {
                if (result.size() >= maxCount) break;

                String preview = firstUserMsg.getOrDefault(sid, "");
                if (preview.length() > 80) {
                    preview = preview.substring(0, 77) + "...";
                }

                String dateStr = formatSessionDate(sid);

                Map<String, String> summary = new LinkedHashMap<>();
                summary.put("id", sid);
                summary.put("preview", preview);
                summary.put("date", dateStr);
                result.add(summary);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Load all turns for a specific session.
     *
     * Each map contains "role", "text", and "ts".
     *
     * @param sessionId session ID to load
     */
    public static synchronized List<Map<String, String>> loadSession(String sessionId) {
        List<Map<String, String>> turns = new ArrayList<>();
        Path path = Paths.get(HISTORY_FILE);
        if (!Files.exists(path) || sessionId == null || sessionId.isEmpty()) {
            return turns;
        }

        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                Map<String, String> turn = parseLine(line);
                if (turn == null) continue;

                if (sessionId.equals(turn.get("session"))) {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("role", turn.getOrDefault("role", ""));
                    entry.put("text", turn.getOrDefault("text", ""));
                    entry.put("ts",   turn.getOrDefault("ts",   ""));
                    turns.add(entry);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return turns;
    }

    /**
     * Delete all stored history.
     */
    public static synchronized void clearAll() {
        try {
            Path path = Paths.get(HISTORY_FILE);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Build a JSON object line for one conversation turn.
     * All special characters in string values are escaped.
     */
    private static String buildJson(String sessionId, String role, String text, String timestamp) {
        return "{"
                + "\"role\":"    + jsonString(role)      + ","
                + "\"text\":"    + jsonString(text)      + ","
                + "\"ts\":"      + jsonString(timestamp) + ","
                + "\"session\":" + jsonString(sessionId)
                + "}";
    }

    /**
     * Wrap a value as a JSON string literal, escaping backslash, quote, and
     * common control characters.
     */
    private static String jsonString(String value) {
        if (value == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    /**
     * Minimal hand-written JSON object parser for the fixed four-key structure
     * produced by {@link #buildJson}. Returns {@code null} on any parse error.
     */
    private static Map<String, String> parseLine(String line) {
        try {
            line = line.trim();
            if (!line.startsWith("{") || !line.endsWith("}")) return null;
            // Strip outer braces
            line = line.substring(1, line.length() - 1);

            Map<String, String> map = new LinkedHashMap<>();
            int idx = 0;
            int len = line.length();

            while (idx < len) {
                // Skip whitespace
                while (idx < len && Character.isWhitespace(line.charAt(idx))) idx++;
                if (idx >= len) break;

                // Expect a quoted key
                if (line.charAt(idx) != '"') return null;
                int[] keyEnd = new int[1];
                String key = parseJsonString(line, idx, keyEnd);
                if (key == null) return null;
                idx = keyEnd[0];

                // Skip whitespace and colon
                while (idx < len && Character.isWhitespace(line.charAt(idx))) idx++;
                if (idx >= len || line.charAt(idx) != ':') return null;
                idx++;

                // Skip whitespace
                while (idx < len && Character.isWhitespace(line.charAt(idx))) idx++;
                if (idx >= len) return null;

                // Expect a quoted value
                if (line.charAt(idx) != '"') return null;
                int[] valEnd = new int[1];
                String value = parseJsonString(line, idx, valEnd);
                if (value == null) return null;
                idx = valEnd[0];

                map.put(key, value);

                // Skip whitespace and optional comma
                while (idx < len && Character.isWhitespace(line.charAt(idx))) idx++;
                if (idx < len && line.charAt(idx) == ',') idx++;
            }

            return map;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse a JSON string starting at {@code start} (which must point at the
     * opening {@code "}). Writes the index after the closing {@code "} into
     * {@code endIdx[0]}. Returns the decoded string, or {@code null} on error.
     */
    private static String parseJsonString(String s, int start, int[] endIdx) {
        if (s.charAt(start) != '"') return null;
        StringBuilder sb = new StringBuilder();
        int i = start + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '"') {
                endIdx[0] = i + 1;
                return sb.toString();
            } else if (c == '\\') {
                i++;
                if (i >= s.length()) return null;
                char esc = s.charAt(i);
                switch (esc) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'u':
                        if (i + 4 >= s.length()) return null;
                        String hex = s.substring(i + 1, i + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                        break;
                    default:
                        sb.append(esc);
                }
            } else {
                sb.append(c);
            }
            i++;
        }
        return null; // unterminated string
    }

    /**
     * Convert a session ID (yyyyMMdd_HHmmss) to a human-readable date string
     * (yyyy-MM-dd HH:mm). Returns the raw ID if it cannot be parsed.
     */
    private static String formatSessionDate(String sessionId) {
        if (sessionId == null || sessionId.length() < 15) return sessionId;
        try {
            // yyyyMMdd_HHmmss  ->  2026-05-21 14:30
            String datePart = sessionId.substring(0, 8);   // yyyyMMdd
            String timePart = sessionId.substring(9, 15);  // HHmmss
            String year  = datePart.substring(0, 4);
            String month = datePart.substring(4, 6);
            String day   = datePart.substring(6, 8);
            String hour  = timePart.substring(0, 2);
            String min   = timePart.substring(2, 4);
            return year + "-" + month + "-" + day + " " + hour + ":" + min;
        } catch (Exception e) {
            return sessionId;
        }
    }
}
