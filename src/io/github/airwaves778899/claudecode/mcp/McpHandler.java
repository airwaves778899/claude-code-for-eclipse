package io.github.airwaves778899.claudecode.mcp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON-RPC 2.0 routing for MCP protocol.
 *
 * Handles:  initialize, notifications/initialized, tools/list, tools/call, ping
 */
public final class McpHandler {

    private McpHandler() {}

    private static final String PROTO_VERSION = "2024-11-05";
    private static final String SERVER_NAME   = "eclipse-mcp";
    private static final String SERVER_VER    = "1.0.0";

    // ── Dispatch ───────────────────────────────────────────────────────────────

    /**
     * @return JSON-RPC response string, or null/empty for notifications
     */
    public static String handle(String body) {
        if (body == null || body.isBlank()) return null;

        String method = extractStr(body, "method");
        if (method == null) return error("null", -32600, "Invalid request");

        // Notifications have no "id" — no response needed
        if (!hasId(body)) return null;

        String id = extractId(body);

        switch (method) {

            case "initialize":
                return response(id, buildInitializeResult());

            case "tools/list":
                return response(id, McpTools.toolsList());

            case "tools/call": {
                String toolName = extractNestedStr(body, "params", "name");
                String argsJson = extractBlock(body, "arguments");
                if (toolName == null) return error(id, -32602, "Missing tool name");
                String result = McpTools.call(toolName, argsJson != null ? argsJson : "{}");
                return response(id, result);
            }

            case "ping":
                return response(id, "{}");

            default:
                return error(id, -32601, "Method not found: " + method);
        }
    }

    // ── MCP initialize result ──────────────────────────────────────────────────

    private static String buildInitializeResult() {
        return "{"
             + "\"protocolVersion\":\"" + PROTO_VERSION + "\","
             + "\"capabilities\":{\"tools\":{}},"
             + "\"serverInfo\":{\"name\":\"" + SERVER_NAME + "\","
             +                 "\"version\":\"" + SERVER_VER + "\"}"
             + "}";
    }

    // ── JSON-RPC builders ──────────────────────────────────────────────────────

    static String response(String id, String result) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + jsonId(id) + ",\"result\":" + result + "}";
    }

    static String error(String id, int code, String msg) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + jsonId(id)
             + ",\"error\":{\"code\":" + code + ",\"message\":\"" + esc(msg) + "\"}}";
    }

    /** Wrap text in a standard MCP content result */
    static String textResult(String text) {
        return "{\"content\":[{\"type\":\"text\",\"text\":\"" + esc(text) + "\"}]}";
    }

    /** Wrap an error in a MCP isError result */
    static String errorResult(String msg) {
        return "{\"content\":[{\"type\":\"text\",\"text\":\"" + esc("Error: " + msg) + "\"}],\"isError\":true}";
    }

    // ── Minimal JSON parsing ───────────────────────────────────────────────────

    /** Extract top-level string or numeric value for key */
    static String extractStr(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|(\\d+))");
        Matcher m = p.matcher(json);
        if (!m.find()) return null;
        return m.group(1) != null ? unesc(m.group(1)) : m.group(2);
    }

    /** Extract id field — may be number or string */
    static String extractId(String json) {
        // numeric id
        Matcher nm = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(json);
        if (nm.find()) return nm.group(1);
        // string id
        Matcher sm = Pattern.compile("\"id\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        if (sm.find()) return sm.group(1);
        return "null";
    }

    static boolean hasId(String json) {
        return Pattern.compile("\"id\"\\s*:").matcher(json).find();
    }

    /** Extract a string value nested one level: params.name → extractNestedStr(json,"params","name") */
    static String extractNestedStr(String json, String outerKey, String innerKey) {
        String block = extractBlock(json, outerKey);
        if (block == null) return null;
        return extractStr(block, innerKey);
    }

    /**
     * Extract a JSON object or array value for key.
     * Returns the raw JSON block including braces/brackets, or null.
     */
    static String extractBlock(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int pos = idx + search.length();
        // skip whitespace and colon
        while (pos < json.length() && (json.charAt(pos) == ' ' || json.charAt(pos) == '\t' || json.charAt(pos) == '\n' || json.charAt(pos) == ':')) pos++;
        if (pos >= json.length()) return null;
        char open = json.charAt(pos);
        char close = open == '{' ? '}' : (open == '[' ? ']' : 0);
        if (close == 0) return null;
        int depth = 0;
        boolean inStr = false;
        int start = pos;
        for (int i = pos; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inStr) {
                if (c == '\\') { i++; continue; }
                if (c == '"')  inStr = false;
                continue;
            }
            if (c == '"')   { inStr = true; continue; }
            if (c == open)  depth++;
            if (c == close) { depth--; if (depth == 0) return json.substring(start, i + 1); }
        }
        return null;
    }

    // ── String utilities ───────────────────────────────────────────────────────

    private static String jsonId(String id) {
        if (id == null || "null".equals(id)) return "null";
        return id.matches("\\d+") ? id : "\"" + esc(id) + "\"";
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unesc(String s) {
        if (s == null) return null;
        return s.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }
}
