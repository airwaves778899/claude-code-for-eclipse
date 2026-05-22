package com.holtek.claudecode.terminal;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.PlatformUI;

import com.holtek.claudecode.views.MarkdownRenderer;

/**
 * Floating Claude Chat Terminal — parented to Eclipse's workbench shell.
 *
 * Features:
 *  - Streaming Claude responses
 *  - Markdown rendering after each response (bold, italic, code, headings)
 *  - Timestamps on messages
 *  - Dark terminal theme
 *  - Singleton: re-focuses if already open
 */
public class ClaudeTerminalWindow {

    private static ClaudeTerminalWindow current;

    // ── Widgets ────────────────────────────────────────────────────────────────
    private Shell      shell;
    private StyledText output;
    private Text       inputText;
    private Button     sendBtn;
    private Button     newBtn;

    // ── State ──────────────────────────────────────────────────────────────────
    private boolean          firstMessage = true;
    private volatile boolean busy         = false;
    private volatile int     claudeStart  = -1;

    // ── Resources ──────────────────────────────────────────────────────────────
    private Color colBg, colInputBg, colText, colYou, colClaude, colMeta;
    private Color colCode, colCodeBg, colHeading, colItalic, colTime;
    private Font  monoFont;
    private MarkdownRenderer.Colors mdColors;

    // ── Patterns ───────────────────────────────────────────────────────────────
    private static final java.util.regex.Pattern TEXT_PATTERN =
            java.util.regex.Pattern.compile("\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final java.util.regex.Pattern ANSI_PATTERN =
            java.util.regex.Pattern.compile("(?:\\[[0-9;]*[A-Za-z]|[^\\[])");

    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm");

    // ── Static open ────────────────────────────────────────────────────────────

    public static void open() {
        Display d = Display.getDefault();
        if (d.isDisposed()) return;
        d.asyncExec(() -> {
            if (current != null && !current.shell.isDisposed()) {
                current.shell.forceActive();
                if (current.inputText != null) current.inputText.setFocus();
                return;
            }
            current = new ClaudeTerminalWindow();
            current.create();
        });
    }

    // ── Window creation ────────────────────────────────────────────────────────

    private void create() {
        Shell eclipseShell = null;
        try {
            eclipseShell = PlatformUI.getWorkbench()
                    .getActiveWorkbenchWindow().getShell();
        } catch (Exception ignored) {}

        if (eclipseShell != null) {
            shell = new Shell(eclipseShell, SWT.SHELL_TRIM | SWT.MODELESS);
        } else {
            shell = new Shell(Display.getDefault(), SWT.SHELL_TRIM | SWT.MODELESS);
        }
        shell.setText("Claude Terminal");
        shell.setSize(860, 620);

        if (eclipseShell != null) {
            Rectangle eb = eclipseShell.getBounds();
            shell.setLocation(
                eb.x + (eb.width  - 860) / 2,
                eb.y + (eb.height - 620) / 2);
        }

        initResources(shell.getDisplay());
        shell.setBackground(colBg);

        GridLayout gl = new GridLayout(1, false);
        gl.marginWidth = 0; gl.marginHeight = 0; gl.verticalSpacing = 0;
        shell.setLayout(gl);

        buildTitleBar();
        buildOutput();
        buildSeparator();
        buildInput();

        shell.addDisposeListener(e -> {
            disposeResources();
            current = null;
        });

        shell.open();
        showWelcome();
        if (inputText != null) inputText.setFocus();
    }

    private void buildTitleBar() {
        Composite bar = new Composite(shell, SWT.NONE);
        bar.setLayout(new GridLayout(1, false));
        bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Color barBg = new Color(shell.getDisplay(), 18, 18, 18);
        bar.setBackground(barBg);

        Label title = new Label(bar, SWT.NONE);
        title.setText("  ◆  Claude Terminal  —  " + ClaudeTerminalContext.getWorkDir());
        title.setForeground(colMeta);
        title.setBackground(barBg);
        title.setFont(monoFont);
    }

    private void buildOutput() {
        output = new StyledText(shell,
                SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
        output.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        output.setBackground(colBg);
        output.setForeground(colText);
        output.setFont(monoFont);
        output.setEditable(false);
        output.setMargins(14, 10, 14, 8);
        output.setLineSpacing(2);
        output.setData("org.eclipse.e4.ui.css.swt.theme.exclude", Boolean.TRUE);
    }

    private void buildSeparator() {
        Label sep = new Label(shell, SWT.HORIZONTAL | SWT.SEPARATOR);
        sep.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    private void buildInput() {
        Composite row = new Composite(shell, SWT.NONE);
        GridLayout rl = new GridLayout(3, false);
        rl.marginWidth = 10; rl.marginHeight = 6; rl.horizontalSpacing = 6;
        row.setLayout(rl);
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        row.setBackground(colBg);
        row.setData("org.eclipse.e4.ui.css.swt.theme.exclude", Boolean.TRUE);

        Label prompt = new Label(row, SWT.NONE);
        prompt.setText("▶");
        prompt.setForeground(colYou);
        prompt.setBackground(colBg);
        prompt.setFont(monoFont);

        inputText = new Text(row, SWT.SINGLE | SWT.BORDER);
        inputText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        inputText.setBackground(colInputBg);
        inputText.setForeground(colText);
        inputText.setFont(monoFont);
        inputText.setData("org.eclipse.e4.ui.css.swt.theme.exclude", Boolean.TRUE);

        Composite btnArea = new Composite(row, SWT.NONE);
        btnArea.setLayout(new RowLayout(SWT.HORIZONTAL));
        btnArea.setBackground(colBg);

        newBtn = new Button(btnArea, SWT.PUSH);
        newBtn.setText("⊕ New");
        newBtn.setToolTipText("Start a new conversation");

        sendBtn = new Button(btnArea, SWT.PUSH);
        sendBtn.setText("Send ▷");

        inputText.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR) sendMessage();
            }
        });
        sendBtn.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) { sendMessage(); }
        });
        newBtn.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) { newConversation(); }
        });
    }

    // ── Welcome ────────────────────────────────────────────────────────────────

    private void showWelcome() {
        String workDir   = ClaudeTerminalContext.getWorkDir();
        List<String> ads = ClaudeTerminalContext.getAddDirs();

        appendMeta("┌─────────────────────────────────────────────────────┐\n");
        appendMeta("│  Claude Code  ·  Chat Terminal (Floating)           │\n");
        appendMeta("└─────────────────────────────────────────────────────┘\n");
        appendTime("  Working dir: " + workDir + "\n");
        if (!ads.isEmpty()) {
            appendTime("  Extra dirs : " + ads.size() + " project(s)\n");
            for (String d : ads) appendTime("               + " + d + "\n");
        }
        appendMeta("\n");
    }

    // ── Send / New ─────────────────────────────────────────────────────────────

    private void sendMessage() {
        if (busy || shell.isDisposed()) return;
        String msg = inputText.getText().trim();
        if (msg.isEmpty()) return;

        inputText.setText("");
        setInputEnabled(false);
        busy        = true;
        claudeStart = -1;

        String ts = TIME_FMT.format(new Date());
        appendHeader("You", ts, colYou);
        appendYou(msg);
        appendMeta("\n");

        boolean isFirst = firstMessage;
        firstMessage = false;

        new Thread(() -> runQuery(msg, isFirst), "claude-float-query").start();
    }

    private void newConversation() {
        if (busy) return;
        firstMessage = true;
        claudeStart  = -1;
        appendMeta("\n─────────────────────  New Conversation  ─────────────────────\n\n");
    }

    // ── Query ──────────────────────────────────────────────────────────────────

    private void runQuery(String prompt, boolean isFirstMessage) {
        String claude  = ClaudeTerminalContext.getClaudeExe();
        String workDir = ClaudeTerminalContext.getWorkDir();
        List<String> addDirs = ClaudeTerminalContext.getAddDirs();

        try {
            List<String> cmd = buildCmd(claude);
            cmd.add("-p");
            cmd.add(prompt);
            cmd.add("--verbose");
            cmd.add("--output-format");
            cmd.add("stream-json");
            cmd.add("--dangerously-skip-permissions");
            if (!isFirstMessage) cmd.add("--continue");
            for (String d : addDirs) { cmd.add("--add-dir"); cmd.add(d); }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new java.io.File(workDir));
            pb.environment().put("NO_COLOR", "1");
            pb.environment().put("FORCE_COLOR", "0");
            pb.environment().put("TERM", "dumb");
            pb.redirectErrorStream(true);

            Process process = pb.start();
            try { process.getOutputStream().close(); } catch (Exception ignored) {}

            // Claude header
            String ts = TIME_FMT.format(new Date());
            if (!shell.isDisposed()) {
                shell.getDisplay().asyncExec(() -> appendHeader("Claude", ts, colClaude));
            }

            StringBuilder lastText = new StringBuilder();

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (line.startsWith("{")) {
                        if (line.contains("\"type\":\"assistant\"")) {
                            Matcher m = TEXT_PATTERN.matcher(line);
                            String last = null;
                            while (m.find()) last = m.group(1);
                            if (last != null) {
                                String text = unescapeJson(last);
                                if (text.length() > lastText.length()) {
                                    String delta = text.substring(lastText.length());
                                    lastText = new StringBuilder(text);
                                    appendToken(stripAnsi(delta));
                                }
                            }
                        } else if (line.contains("\"type\":\"result\"")) {
                            if (lastText.length() == 0) {
                                java.util.regex.Pattern rp = java.util.regex.Pattern.compile(
                                        "\"result\":\"((?:[^\"\\\\]|\\\\.)*)\"");
                                Matcher rm = rp.matcher(line);
                                if (rm.find()) {
                                    String t = stripAnsi(unescapeJson(rm.group(1)));
                                    appendToken(t);
                                    lastText = new StringBuilder(t);
                                }
                            }
                            break;
                        } else if (line.contains("\"is_error\":true")) {
                            // Tool call returned an error — extract message but keep reading.
                            // Claude will continue and provide a text response explaining the issue.
                            java.util.regex.Pattern ep = java.util.regex.Pattern.compile(
                                    "\"content\":\"((?:[^\"\\\\]|\\\\.)*)\"");
                            Matcher em = ep.matcher(line);
                            if (em.find()) {
                                String errMsg = stripAnsi(unescapeJson(em.group(1)));
                                if (!errMsg.isBlank()) appendError("Tool error: " + errMsg);
                            }
                            // Do NOT break — Claude will send its reply next
                        }
                    }
                    // Skip non-JSON lines (warnings, etc.)
                }
            }
            process.waitFor();

            // Apply markdown to completed response
            final String fullResponse = lastText.toString();
            final int startPos = claudeStart;
            if (!shell.isDisposed() && startPos >= 0 && !fullResponse.isEmpty()) {
                shell.getDisplay().asyncExec(() -> {
                    if (!output.isDisposed()) applyMarkdown(startPos, fullResponse);
                });
            }

        } catch (Exception e) {
            appendError(e.getMessage());
        } finally {
            if (!shell.isDisposed()) {
                shell.getDisplay().asyncExec(() -> {
                    if (!output.isDisposed()) output.append("\n\n");
                    setInputEnabled(true);
                    busy = false;
                });
            }
        }
    }

    // ── Markdown ───────────────────────────────────────────────────────────────

    private void applyMarkdown(int startPos, String text) {
        if (output.isDisposed()) return;
        List<StyleRange> ranges = MarkdownRenderer.render(text, startPos, mdColors);
        for (StyleRange sr : ranges) {
            try { output.setStyleRange(sr); } catch (Exception ignored) {}
        }
    }

    // ── Append helpers ─────────────────────────────────────────────────────────

    private void appendHeader(String label, String ts, Color labelColor) {
        if (output.isDisposed()) return;
        String line = label + "  " + ts + "\n";
        int s = output.getCharCount();
        output.append(line);
        StyleRange sr = new StyleRange(s, label.length(), labelColor, null);
        sr.fontStyle = SWT.BOLD;
        output.setStyleRange(sr);
        output.setStyleRange(new StyleRange(s + label.length(), line.length() - label.length(),
                colTime, null));
        scrollToEnd();
    }

    private void appendYou(String text) {
        if (output.isDisposed()) return;
        int s = output.getCharCount();
        String line = text + "\n";
        output.append(line);
        output.setStyleRange(new StyleRange(s, line.length(), colYou, null));
        scrollToEnd();
    }

    private void appendMeta(String text) { asyncAppend(text, colMeta, false, 0); }
    private void appendTime(String text) { asyncAppend(text, colTime, false, 0); }

    private void appendToken(String token) {
        if (token == null || token.isEmpty()) return;
        Display d = output.getDisplay();
        if (d.isDisposed()) return;
        d.asyncExec(() -> {
            if (output.isDisposed()) return;
            if (claudeStart < 0) claudeStart = output.getCharCount();
            int s = output.getCharCount();
            output.append(token);
            output.setStyleRange(new StyleRange(s, token.length(), colClaude, null));
            scrollToEnd();
        });
    }

    private void appendError(String msg) {
        Display d = output.getDisplay();
        if (d.isDisposed()) return;
        d.asyncExec(() -> {
            if (output.isDisposed()) return;
            String err = "\n⚠  " + msg + "\n";
            int s = output.getCharCount();
            output.append(err);
            output.setStyleRange(new StyleRange(s, err.length(),
                    d.getSystemColor(SWT.COLOR_RED), null));
            scrollToEnd();
        });
    }

    private void asyncAppend(String text, Color col, boolean bold, int labelLen) {
        Display d = output.getDisplay();
        if (d.isDisposed()) return;
        d.asyncExec(() -> {
            if (output.isDisposed()) return;
            int start = output.getCharCount();
            output.append(text);
            if (labelLen > 0) {
                StyleRange sr = new StyleRange(start, labelLen, col, null);
                sr.fontStyle = bold ? SWT.BOLD : SWT.NORMAL;
                output.setStyleRange(sr);
                if (text.length() > labelLen)
                    output.setStyleRange(new StyleRange(start + labelLen,
                            text.length() - labelLen, colText, null));
            } else {
                output.setStyleRange(new StyleRange(start, text.length(), col, null));
            }
            scrollToEnd();
        });
    }

    private void scrollToEnd() {
        output.setCaretOffset(output.getCharCount());
        output.showSelection();
    }

    private void setInputEnabled(boolean en) {
        if (!inputText.isDisposed()) inputText.setEnabled(en);
        if (!sendBtn.isDisposed())   sendBtn.setEnabled(en);
        if (!newBtn.isDisposed())    newBtn.setEnabled(en);
        if (en && !inputText.isDisposed()) inputText.setFocus();
    }

    // ── CLI helpers ────────────────────────────────────────────────────────────

    private static List<String> buildCmd(String claude) {
        List<String> cmd = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win") && claude.endsWith(".cmd")) {
            cmd.add("cmd.exe"); cmd.add("/c");
        }
        cmd.add(claude);
        return cmd;
    }

    private static String stripAnsi(String s) {
        return ANSI_PATTERN.matcher(s).replaceAll("");
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
                    case 'n':  sb.append('\n'); i += 2; continue;
                    case 'r':  sb.append('\r'); i += 2; continue;
                    case 't':  sb.append('\t'); i += 2; continue;
                    case 'u':
                        if (i + 5 < s.length()) {
                            try {
                                sb.append((char) Integer.parseInt(
                                        s.substring(i + 2, i + 6), 16));
                                i += 6; continue;
                            } catch (NumberFormatException ignored) {}
                        }
                        break;
                    default: break;
                }
            }
            sb.append(c); i++;
        }
        return sb.toString();
    }

    // ── Resources ──────────────────────────────────────────────────────────────

    private void initResources(Display d) {
        // Detect system theme
        Color sysBg = d.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
        boolean dark = (sysBg.getRed() + sysBg.getGreen() + sysBg.getBlue()) < 380;

        if (dark) {
            colBg      = new Color(d,  24,  24,  24);
            colInputBg = new Color(d,  40,  40,  44);
            colText    = new Color(d, 240, 240, 240);
            colYou     = new Color(d,  86, 156, 214);
            colClaude  = new Color(d, 255, 255, 255);
            colMeta    = new Color(d, 106, 153,  85);
            colTime    = new Color(d, 120, 120, 120);
            colCode    = new Color(d, 206, 145, 120);
            colCodeBg  = new Color(d,  48,  48,  48);
            colHeading = new Color(d,  86, 156, 214);
            colItalic  = new Color(d, 200, 200, 200);
        } else {
            colBg      = new Color(d, 250, 250, 250);
            colInputBg = new Color(d, 240, 240, 243);
            colText    = new Color(d,  30,  30,  30);
            colYou     = new Color(d,   0,  80, 180);
            colClaude  = new Color(d,  20,  20,  20);
            colMeta    = new Color(d,   0, 110,  50);
            colTime    = new Color(d, 130, 130, 130);
            colCode    = new Color(d, 160,  60,  20);
            colCodeBg  = new Color(d, 238, 238, 238);
            colHeading = new Color(d,   0,  80, 180);
            colItalic  = new Color(d,  80,  80,  80);
        }
        monoFont   = new Font(d, "Consolas", 11, SWT.NORMAL);
        mdColors   = new MarkdownRenderer.Colors(
                colText, colHeading, colText, colItalic,
                colCode, colCodeBg,  colMeta);
    }

    private void disposeResources() {
        Color[] cs = { colBg, colInputBg, colText, colYou, colClaude, colMeta,
                       colTime, colCode, colCodeBg, colHeading, colItalic };
        for (Color c : cs) if (c != null && !c.isDisposed()) c.dispose();
        if (monoFont != null && !monoFont.isDisposed()) monoFont.dispose();
    }
}
