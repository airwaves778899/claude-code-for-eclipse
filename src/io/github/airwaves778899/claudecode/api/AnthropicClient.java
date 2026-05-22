package io.github.airwaves778899.claudecode.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Claude Code for Eclipse - Anthropic API Client (Phase 2)
 *
 * Sends requests to https://api.anthropic.com/v1/messages with streaming enabled.
 * Uses Java 11 HttpClient — no external JSON library required.
 *
 * SSE event format (simplified):
 *   event: content_block_delta
 *   data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}
 */
public class AnthropicClient {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final String API_URL         = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION     = "2023-06-01";
    private static final int    MAX_TOKENS      = 8192;
    private static final int    CONNECT_TIMEOUT = 15; // seconds

    /** System prompt injected as the first message context. */
    private static final String SYSTEM_PROMPT =
        "You are Claude Code, an AI coding assistant embedded in Eclipse IDE. " +
        "Help the user with Java development, debugging, code review, and architecture. " +
        "When showing code, use proper formatting. " +
        "Be concise but thorough. Respond in the same language the user uses.";

    // ── SSE parsing patterns ──────────────────────────────────────────────────
    /** Matches the text value inside a text_delta SSE event. */
    private static final Pattern TEXT_DELTA_PATTERN =
            Pattern.compile("\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"");

    // ── Fields ────────────────────────────────────────────────────────────────
    private final String    apiKey;
    private final String    model;
    private final HttpClient httpClient;

    // ── Constructor ───────────────────────────────────────────────────────────

    public AnthropicClient(String apiKey, String model) {
        this.apiKey  = apiKey;
        this.model   = (model != null && !model.isEmpty()) ? model : "claude-sonnet-4-6";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT))
                .build();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Send a message list to the Anthropic API and stream the response.
     * Runs on a new background thread; all callback methods are invoked
     * from that thread — use Display.asyncExec() for SWT updates.
     *
     * @param messages  full conversation history (user/assistant alternating)
     * @param callback  receives onToken / onComplete / onError events
     */
    public void sendStream(List<ChatMessage> messages, StreamCallback callback) {
        new Thread(() -> {
            try {
                doSendStream(messages, callback);
            } catch (Exception e) {
                callback.onError(e);
            }
        }, "claude-api-stream").start();
    }

    // ── Private implementation ────────────────────────────────────────────────

    private void doSendStream(List<ChatMessage> messages, StreamCallback callback)
            throws Exception {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "API Key 未設定。\n請前往 Window > Preferences > Claude Code 輸入您的 Anthropic API Key。");
        }

        String requestBody = buildRequestJson(messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("x-api-key",          apiKey.trim())
                .header("anthropic-version",   API_VERSION)
                .header("content-type",        "application/json")
                .header("accept",              "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<Stream<String>> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofLines());

        int statusCode = response.statusCode();
        if (statusCode != 200) {
            // Collect error body
            StringBuilder errorBody = new StringBuilder();
            response.body().forEach(line -> errorBody.append(line).append("\n"));
            throw new RuntimeException(buildErrorMessage(statusCode, errorBody.toString()));
        }

        // ── Parse SSE stream ─────────────────────────────────────────────────
        StringBuilder fullResponse = new StringBuilder();

        response.body().forEach(line -> {
            if (!line.startsWith("data: ")) return;

            String data = line.substring(6).trim();
            if (data.isEmpty() || data.equals("[DONE]")) return;

            String token = extractTextDelta(data);
            if (token != null && !token.isEmpty()) {
                fullResponse.append(token);
                callback.onToken(token);
            }
        });

        callback.onComplete(fullResponse.toString());
    }

    // ── JSON builder ──────────────────────────────────────────────────────────

    /**
     * Build the Anthropic Messages API request body as a JSON string.
     * No external library required — constructs JSON manually.
     */
    private String buildRequestJson(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"model\":\"").append(escapeJson(model)).append("\",");
        sb.append("\"max_tokens\":").append(MAX_TOKENS).append(",");
        sb.append("\"stream\":true,");
        sb.append("\"system\":\"").append(escapeJson(SYSTEM_PROMPT)).append("\",");
        sb.append("\"messages\":[");

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (i > 0) sb.append(",");
            sb.append("{");
            sb.append("\"role\":\"").append(escapeJson(msg.getRole())).append("\",");
            sb.append("\"content\":\"").append(escapeJson(msg.getContent())).append("\"");
            sb.append("}");
        }

        sb.append("]}");
        return sb.toString();
    }

    // ── SSE / JSON parsers ────────────────────────────────────────────────────

    /**
     * Extract the text token from a content_block_delta SSE data line.
     * Returns null if the event is not a text_delta.
     */
    private static String extractTextDelta(String jsonData) {
        if (!jsonData.contains("content_block_delta") ||
            !jsonData.contains("text_delta")) {
            return null;
        }

        // Find all "text":"..." occurrences; the last one is the delta text
        Matcher m = TEXT_DELTA_PATTERN.matcher(jsonData);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        return last != null ? unescapeJson(last) : null;
    }

    // ── String utilities ──────────────────────────────────────────────────────

    /**
     * Escape a plain string for embedding in a JSON string value.
     */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Unescape a JSON string value back to a plain Java string.
     */
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
                            String hex = s.substring(i + 2, i + 6);
                            try {
                                out.append((char) Integer.parseInt(hex, 16));
                                i += 6;
                                continue;
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

    /**
     * Build a human-readable error message for non-200 HTTP responses.
     */
    private static String buildErrorMessage(int statusCode, String body) {
        String hint;
        switch (statusCode) {
            case 401: hint = "API Key 無效或未授權。請至 Preferences > Claude Code 重新輸入。"; break;
            case 403: hint = "存取被拒。請確認 API Key 權限。"; break;
            case 429: hint = "請求頻率過高（Rate Limit）。請稍後再試。"; break;
            case 500:
            case 529: hint = "Anthropic 伺服器暫時無法回應。請稍後再試。"; break;
            default:  hint = "HTTP " + statusCode; break;
        }
        // Try to extract "error.message" from response body
        String detail = extractJsonField(body, "message");
        if (detail != null && !detail.isEmpty()) {
            hint += "\n詳細：" + detail;
        }
        return hint;
    }

    /**
     * Very simple field extractor: find "key":"value" in a JSON string.
     */
    private static String extractJsonField(String json, String key) {
        if (json == null || json.isEmpty()) return null;
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        int end   = json.indexOf('"', start);
        if (end < 0) return null;
        return unescapeJson(json.substring(start, end));
    }
}
