package io.github.airwaves778899.claudecode.mcp;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/**
 * Minimal HTTP server that exposes an MCP (Model Context Protocol) endpoint
 * at  http://localhost:PORT/mcp
 *
 * Claude Code connects via ~/.claude/settings.json:
 *   mcp-remote http://localhost:8124/mcp/eclipse-ide --allow-http
 *             --header "Authorization: Bearer <token>"
 *
 * Protocol: MCP Streamable-HTTP (stateless JSON-RPC 2.0 over HTTP POST).
 * Routes: any path starting with /mcp is handled by McpHandler.
 */
public class McpServer {

    /** Must match the port in ~/.claude/settings.json */
    public static final int    DEFAULT_PORT  = 8124;
    public static final String MCP_PATH      = "/mcp";
    /** Bearer token set in settings.json — used to reject unauthorised callers */
    public static final String BEARER_TOKEN  = "617135f5-377e-4b0a-aebc-194ce9acd762";

    private final int port;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private volatile boolean running = false;
    private Thread listenThread;

    public McpServer() { this(DEFAULT_PORT); }
    public McpServer(int port) { this.port = port; }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    public synchronized void start() throws IOException {
        if (running) return;
        serverSocket = new ServerSocket(port);
        serverSocket.setReuseAddress(true);
        executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "mcp-conn");
            t.setDaemon(true);
            return t;
        });
        running = true;
        listenThread = new Thread(this::acceptLoop, "mcp-listen");
        listenThread.setDaemon(true);
        listenThread.start();
        System.out.println("[MCP] Server started on port " + port);
    }

    public synchronized void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        if (executor != null) executor.shutdownNow();
        System.out.println("[MCP] Server stopped");
    }

    public boolean isRunning() { return running; }
    public int     getPort()   { return port; }

    // ── Accept loop ────────────────────────────────────────────────────────────

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                client.setSoTimeout(10_000);
                executor.submit(() -> handleConnection(client));
            } catch (IOException e) {
                if (running) System.err.println("[MCP] Accept error: " + e.getMessage());
            }
        }
    }

    // ── Connection handler ─────────────────────────────────────────────────────

    private void handleConnection(Socket socket) {
        try (socket) {
            InputStream  in  = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // ── Read request line ─────────────────────────────────────────────
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String httpMethod = parts[0];
            String path       = parts[1];

            // ── Read headers ──────────────────────────────────────────────────
            int    contentLength = 0;
            String authHeader    = null;
            String line;
            while (!(line = readLine(in)).isEmpty()) {
                String lower = line.toLowerCase();
                if (lower.startsWith("content-length:"))
                    contentLength = Integer.parseInt(lower.substring(15).trim());
                if (lower.startsWith("authorization:"))
                    authHeader = line.substring("authorization:".length()).trim();
            }

            // ── CORS pre-flight ───────────────────────────────────────────────
            if ("OPTIONS".equals(httpMethod)) {
                writeResponse(out, 200, "application/json", "{}");
                return;
            }

            // ── Route check ───────────────────────────────────────────────────
            if (!"POST".equals(httpMethod) || !path.startsWith(MCP_PATH)) {
                writeResponse(out, 404, "application/json",
                        "{\"error\":\"Not found: " + path + "\"}");
                return;
            }

            // ── Bearer token auth ─────────────────────────────────────────────
            String expectedAuth = "Bearer " + BEARER_TOKEN;
            if (authHeader == null || !expectedAuth.equalsIgnoreCase(authHeader)) {
                writeResponse(out, 401, "application/json",
                        "{\"error\":\"Unauthorized\"}");
                return;
            }

            // ── Read body ─────────────────────────────────────────────────────
            byte[] bodyBytes = new byte[contentLength];
            int read = 0;
            while (read < contentLength) {
                int n = in.read(bodyBytes, read, contentLength - read);
                if (n < 0) break;
                read += n;
            }
            String body = new String(bodyBytes, 0, read, StandardCharsets.UTF_8);

            // ── Handle MCP message ────────────────────────────────────────────
            String response = McpHandler.handle(body);
            if (response == null || response.isEmpty()) {
                // Notification — 202 Accepted, no body
                writeStatus(out, 202);
            } else {
                writeResponse(out, 200, "application/json", response);
            }

        } catch (Exception e) {
            // Connection reset / timeout — ignore
        }
    }

    // ── HTTP helpers ───────────────────────────────────────────────────────────

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\r') { in.read(); break; } // skip \n
            if (c == '\n') break;
            sb.append((char) c);
        }
        return sb.toString();
    }

    private static void writeResponse(OutputStream out, int status,
                                      String contentType, String body)
            throws IOException {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + status + " OK\r\n"
                + "Content-Type: " + contentType + "; charset=utf-8\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Access-Control-Allow-Headers: Content-Type, Mcp-Session-Id, Authorization\r\n"
                + "Connection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(bodyBytes);
        out.flush();
    }

    private static void writeStatus(OutputStream out, int status) throws IOException {
        String resp = "HTTP/1.1 " + status + "\r\n"
                + "Content-Length: 0\r\n"
                + "Connection: close\r\n\r\n";
        out.write(resp.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
