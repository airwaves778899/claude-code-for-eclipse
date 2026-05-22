package io.github.airwaves778899.claudecode.views;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import io.github.airwaves778899.claudecode.Activator;
import io.github.airwaves778899.claudecode.api.ClaudeCliClient;
import io.github.airwaves778899.claudecode.api.ChatMessage;
import io.github.airwaves778899.claudecode.api.StreamCallback;
import io.github.airwaves778899.claudecode.apply.CodeBlock;
import io.github.airwaves778899.claudecode.apply.CodeBlockParser;
import io.github.airwaves778899.claudecode.context.ProjectContextProvider;

/**
 * Claude Code for Eclipse - Main Chat View (Phase 2: Live API)
 *
 * Layout:
 * ┌─────────────────────────────────────────┐
 * │  [Header: Claude Code]   [model name]   │
 * ├─────────────────────────────────────────┤
 * │                                         │
 * │  [Chat history - StyledText]            │
 * │      ─── 您 ───────────                 │
 * │      your message                       │
 * │      ─── Claude ────────                │
 * │      streamed response...               │
 * │                                         │
 * ├─────────────────────────────────────────┤
 * │  status: 準備就緒 / 思考中...           │
 * ├─────────────────────────────────────────┤
 * │  [input text field]        [送出] [清除] │
 * └─────────────────────────────────────────┘
 */
public class ClaudeView extends ViewPart {

    public static final String ID = "io.github.airwaves778899.claudecode.views.ClaudeView";

    // ── Appearance settings (easy to adjust) ─────────────────────────────────
    private static final String FONT_NAME   = "Consolas";  // font family
    private static final int    FONT_SIZE   = 13;          // chat text size (pt)
    private static final int    HEADER_SIZE = 12;          // header bold size (pt)

    // ── Conversation history ──────────────────────────────────────────────────
    private final List<ChatMessage> conversationHistory = new ArrayList<>();

    // ── UI widgets ────────────────────────────────────────────────────────────
    private StyledText chatHistory;
    private Text       inputField;
    private Button     sendButton;
    private Button     clearButton;
    private Label      statusLabel;
    private Label      modelLabel;

    // ── Colours (disposed in dispose()) ──────────────────────────────────────
    private Color colorBackground;
    private Color colorHeaderBg;
    private Color colorUserBubble;
    private Color colorAssistantBubble;
    private Color colorSystemBubble;
    private Color colorTextDark;
    private Color colorTextLight;
    private Color colorAccent;
    private Color colorError;

    // ── Font ──────────────────────────────────────────────────────────────────
    private Font monoFont;
    private Font boldFont;

    // ── Phase 5: Project Context toggle ──────────────────────────────────────
    private Button  projectContextButton;
    private boolean includeProjectContext = false;

    // ── Code Action Bar (Phase 4) ─────────────────────────────────────────────
    private Composite codeActionBar;
    private Label     codeActionLabel;
    private Button    applyCodeButton;
    private Button    copyCodeButton;

    // ── Last response code blocks (Phase 4) ──────────────────────────────────
    private List<CodeBlock> lastCodeBlocks = Collections.emptyList();

    // ── State ─────────────────────────────────────────────────────────────────
    private volatile boolean isBusy = false;

    // ─────────────────────────────────────────────────────────────────────────
    //  View creation
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void createPartControl(Composite parent) {
        Display display = parent.getDisplay();
        initColours(display);
        initFonts(display);

        parent.setLayout(new FillLayout());

        Composite root = new Composite(parent, SWT.NONE);
        root.setBackground(colorBackground);
        GridLayout rootLayout = new GridLayout(1, false);
        rootLayout.marginWidth  = 0;
        rootLayout.marginHeight = 0;
        rootLayout.verticalSpacing = 0;
        root.setLayout(rootLayout);

        buildHeader(root, display);
        buildChatHistory(root);
        buildStatusBar(root);
        buildCodeActionBar(root);   // Phase 4: appears when Claude returns code
        buildInputArea(root);

        // Show welcome message
        showWelcome();

        // Re-apply colours after Eclipse CSS theming engine runs (it fires after this method returns)
        display.asyncExec(this::reapplyColours);
    }

    /** Force colours back after Eclipse CSS theme overrides them. */
    private void reapplyColours() {
        if (chatHistory == null || chatHistory.isDisposed()) return;
        chatHistory.setBackground(colorBackground);
        chatHistory.setForeground(colorTextDark);
        chatHistory.redraw();
        if (statusLabel != null && !statusLabel.isDisposed()) {
            statusLabel.setBackground(colorBackground);
            statusLabel.setForeground(colorAccent);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UI builders
    // ─────────────────────────────────────────────────────────────────────────

    private void buildHeader(Composite parent, Display display) {
        Composite header = new Composite(parent, SWT.NONE);
        header.setBackground(colorHeaderBg);
        GridLayout hl = new GridLayout(3, false);
        hl.marginWidth  = 12;
        hl.marginHeight = 6;
        header.setLayout(hl);
        header.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Label title = new Label(header, SWT.NONE);
        title.setText("Claude Code");
        title.setBackground(colorHeaderBg);
        title.setForeground(colorTextLight);
        title.setFont(boldFont);
        title.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Phase 5: Project Context toggle button
        projectContextButton = new Button(header, SWT.TOGGLE);
        projectContextButton.setText("📁 Project");
        projectContextButton.setToolTipText(
            "開啟後，每則訊息自動附上專案結構摘要（pom.xml、套件結構等）");
        projectContextButton.setLayoutData(
            new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        projectContextButton.addListener(SWT.Selection, e -> {
            includeProjectContext = projectContextButton.getSelection();
            setStatus(includeProjectContext
                ? "✓  已開啟 Project Context — 每則訊息將附帶專案摘要"
                : "準備就緒");
        });

        modelLabel = new Label(header, SWT.NONE);
        modelLabel.setBackground(colorHeaderBg);
        modelLabel.setForeground(colorAccent);
        modelLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        refreshModelLabel();
    }

    private void buildChatHistory(Composite parent) {
        chatHistory = new StyledText(parent,
                SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL | SWT.BORDER);
        // Exclude from Eclipse CSS theming so our colours are not overridden
        chatHistory.setData("org.eclipse.e4.ui.css.swt.theme.exclude", Boolean.TRUE);
        chatHistory.setBackground(colorBackground);
        chatHistory.setForeground(colorTextDark);
        chatHistory.setFont(monoFont);
        chatHistory.setEditable(false);
        chatHistory.setWordWrap(true);
        chatHistory.setLeftMargin(10);
        chatHistory.setRightMargin(10);
        chatHistory.setTopMargin(8);
        chatHistory.setBottomMargin(8);
        chatHistory.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    }

    /**
     * Phase 4: Code Action Bar — hidden by default, appears after a response with code.
     *
     * ┌──────────────────────────────────────────────────────────────────────┐
     * │ 💡 Claude 建議了 2 段程式碼 (java)   [Apply to Editor]  [Copy Code]  │
     * └──────────────────────────────────────────────────────────────────────┘
     */
    private void buildCodeActionBar(Composite parent) {
        codeActionBar = new Composite(parent, SWT.NONE);
        codeActionBar.setBackground(new Color(parent.getDisplay(), 20, 45, 20));
        GridLayout gl = new GridLayout(3, false);
        gl.marginWidth = 10; gl.marginHeight = 5;
        gl.horizontalSpacing = 8;
        codeActionBar.setLayout(gl);
        GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false);
        gd.exclude = true;   // hidden until code arrives
        codeActionBar.setLayoutData(gd);
        codeActionBar.setVisible(false);

        codeActionLabel = new Label(codeActionBar, SWT.NONE);
        codeActionLabel.setBackground(new Color(parent.getDisplay(), 20, 45, 20));
        codeActionLabel.setForeground(new Color(parent.getDisplay(), 144, 238, 144));
        codeActionLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        applyCodeButton = new Button(codeActionBar, SWT.PUSH);
        applyCodeButton.setText("Apply to Editor");
        applyCodeButton.setToolTipText("在編輯器中套用 Claude 建議的程式碼 (Alt+Shift+P)");
        applyCodeButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        applyCodeButton.addListener(SWT.Selection, e -> triggerApplyCode());

        copyCodeButton = new Button(codeActionBar, SWT.PUSH);
        copyCodeButton.setText("Copy Code");
        copyCodeButton.setToolTipText("複製最佳程式碼區塊到剪貼簿");
        copyCodeButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        copyCodeButton.addListener(SWT.Selection, e -> copyBestCodeBlock());
    }

    private void buildStatusBar(Composite parent) {
        statusLabel = new Label(parent, SWT.NONE);
        statusLabel.setText("準備就緒");
        statusLabel.setBackground(colorBackground);
        statusLabel.setForeground(colorAccent);
        GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false);
        gd.horizontalIndent = 10;
        gd.verticalIndent   = 2;
        statusLabel.setLayoutData(gd);
    }

    private void buildInputArea(Composite parent) {
        Composite inputArea = new Composite(parent, SWT.NONE);
        inputArea.setBackground(colorBackground);
        GridLayout il = new GridLayout(2, false);
        il.marginWidth  = 8;
        il.marginHeight = 6;
        il.horizontalSpacing = 6;
        inputArea.setLayout(il);
        inputArea.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

        // Multi-line input
        inputField = new Text(inputArea,
                SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
        inputField.setMessage("輸入問題... (Ctrl+Enter 送出)");
        inputField.setFont(monoFont);
        inputField.setForeground(colorTextDark);
        inputField.setBackground(new Color(inputArea.getDisplay(), 255, 255, 255));
        GridData tgd = new GridData(SWT.FILL, SWT.FILL, true, false);
        tgd.heightHint = 72;
        inputField.setLayoutData(tgd);
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.keyCode == SWT.CR && (e.stateMask & SWT.CTRL) != 0) {
                    sendMessage();
                }
            }
        });

        // Button column
        Composite btnCol = new Composite(inputArea, SWT.NONE);
        btnCol.setBackground(colorBackground);
        GridLayout bl = new GridLayout(1, false);
        bl.marginWidth = 0; bl.marginHeight = 0; bl.verticalSpacing = 4;
        btnCol.setLayout(bl);
        btnCol.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, false, false));

        sendButton = new Button(btnCol, SWT.PUSH);
        sendButton.setText("  送出  ");
        sendButton.setToolTipText("送出訊息 (Ctrl+Enter)");
        sendButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        sendButton.addListener(SWT.Selection, e -> sendMessage());

        clearButton = new Button(btnCol, SWT.PUSH);
        clearButton.setText("  清除  ");
        clearButton.setToolTipText("清除對話記錄與歷史");
        clearButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        clearButton.addListener(SWT.Selection, e -> clearChat());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Core actions
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Send a message programmatically from an editor action (Phase 3).
     *
     * Called by editor handlers (ExplainCodeHandler, FixCodeHandler, etc.).
     * Must be called from any thread — switches to UI thread automatically.
     *
     * @param text      the full prompt to send (instruction + code block)
     * @param autoSend  true = send immediately; false = populate input field for user to review
     */
    public void sendWithContext(String text, boolean autoSend) {
        Display display = Display.getDefault();
        safeUiExec(display, () -> {
            if (isBusy) {
                appendSystemMessage("⚠  Claude 目前忙碌中，請稍後再試。");
                return;
            }
            if (autoSend) {
                inputField.setText(text);
                sendMessage();
            } else {
                inputField.setText(text);
                inputField.setFocus();
                // Move caret to start so user can prepend a question
                inputField.setSelection(0);
            }
        });
    }

    /**
     * Send the current user input to the Claude CLI and stream the response.
     */
    private void sendMessage() {
        if (isBusy) return;

        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        // Check CLI availability before proceeding
        String cliPath = Activator.getPref(Activator.PREF_CLI_PATH);
        if (cliPath == null || cliPath.isBlank()) cliPath = "claude";
        if (!ClaudeCliClient.isCliAvailable(cliPath)) {
            appendSystemMessage(
                "⚠  找不到 Claude Code CLI\n" +
                "請先安裝：npm install -g @anthropic-ai/claude-code\n" +
                "然後執行 claude 完成登入，再於\n" +
                "Window > Preferences > Claude Code 確認 CLI 路徑。");
            return;
        }

        // Phase 5: Prepend project context if toggle is on
        if (includeProjectContext) {
            try {
                var project = ProjectContextProvider.getActiveProject();
                if (project != null) {
                    String summary = ProjectContextProvider.buildProjectSummary(project);
                    if (!summary.isBlank()) {
                        text = summary + "\n---\n\n" + text;
                    }
                }
            } catch (Exception ignored) { /* context is optional */ }
        }

        // Update UI
        inputField.setText("");
        inputField.setFocus();
        setBusy(true);

        // Append user bubble (show original text without project context prefix)
        conversationHistory.add(new ChatMessage("user", text));
        appendBubble("您", inputField.getText().isBlank()
                ? text.contains("---") ? text.substring(text.indexOf("---") + 4).trim() : text
                : inputField.getText(), BubbleType.USER);

        // Pre-render Claude bubble header, then stream content into it
        String header = "\n─── Claude ────────────────────────────\n";
        int headerStart = chatHistory.getCharCount();
        chatHistory.append(header);
        applyHeaderStyle(headerStart, header.length(), BubbleType.ASSISTANT);
        scrollToBottom();

        // Accumulate full response for history
        final StringBuilder fullResponse = new StringBuilder();
        final String finalCliPath = cliPath;
        final String model  = Activator.getPref(Activator.PREF_MODEL);
        final Display display = chatHistory.getDisplay();

        ClaudeCliClient client = new ClaudeCliClient(finalCliPath, model);
        client.sendStream(conversationHistory, new StreamCallback() {

            @Override
            public void onToken(String token) {
                fullResponse.append(token);
                safeUiExec(display, () -> {
                    int pos = chatHistory.getCharCount();
                    chatHistory.append(token);
                    applyTokenStyle(pos, token.length());
                    scrollToBottom();
                });
            }

            @Override
            public void onComplete(String fullText) {
                conversationHistory.add(new ChatMessage("assistant", fullText));
                // Phase 4: parse code blocks from the response
                List<CodeBlock> blocks = CodeBlockParser.getSourceBlocks(fullText);
                safeUiExec(display, () -> {
                    chatHistory.append("\n");
                    scrollToBottom();
                    setStatus("準備就緒  ·  " + tokenEstimate(fullText));
                    setBusy(false);
                    // Show/update code action bar
                    updateCodeActionBar(blocks);
                });
            }

            @Override
            public void onError(Exception e) {
                safeUiExec(display, () -> {
                    chatHistory.append("\n");
                    appendSystemMessage("✕  " + e.getMessage());
                    setStatus("發生錯誤");
                    setBusy(false);
                });
            }
        });
    }

    /** Clear chat display and conversation history. */
    private void clearChat() {
        conversationHistory.clear();
        lastCodeBlocks = Collections.emptyList();
        chatHistory.setText("");
        setStatus("對話已清除");
        updateCodeActionBar(Collections.emptyList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Phase 4: Code Action Bar helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Show or hide the code action bar depending on whether blocks exist. */
    private void updateCodeActionBar(List<CodeBlock> blocks) {
        lastCodeBlocks = blocks != null ? blocks : Collections.emptyList();
        if (codeActionBar == null || codeActionBar.isDisposed()) return;

        boolean hasCode = !lastCodeBlocks.isEmpty();
        ((GridData) codeActionBar.getLayoutData()).exclude = !hasCode;
        codeActionBar.setVisible(hasCode);

        if (hasCode) {
            int count = lastCodeBlocks.size();
            String lang = lastCodeBlocks.get(0).getLanguage();
            codeActionLabel.setText(
                "💡  Claude 建議了 " + count + " 段程式碼 (" + lang + ")");
        }
        // Re-layout the parent to expand/collapse the bar
        codeActionBar.getParent().layout(true, true);
    }

    /** Trigger the Apply Code dialog (also called by Alt+Shift+P keyboard shortcut). */
    private void triggerApplyCode() {
        try {
            org.eclipse.ui.PlatformUI.getWorkbench()
                .getService(org.eclipse.ui.handlers.IHandlerService.class)
                .executeCommand("io.github.airwaves778899.claudecode.commands.applyCode", null);
        } catch (Exception e) {
            appendSystemMessage("⚠  無法開啟 Apply 對話框：" + e.getMessage());
        }
    }

    /** Copy the best code block directly to the system clipboard. */
    private void copyBestCodeBlock() {
        if (lastCodeBlocks.isEmpty()) return;
        CodeBlock best = CodeBlockParser.getBestBlock(
            conversationHistory.isEmpty() ? ""
                : conversationHistory.get(conversationHistory.size() - 1).getContent());
        if (best == null) best = lastCodeBlocks.get(0);

        org.eclipse.swt.dnd.Clipboard cb =
            new org.eclipse.swt.dnd.Clipboard(Display.getCurrent());
        cb.setContents(
            new Object[]  { best.getContent() },
            new org.eclipse.swt.dnd.Transfer[]{
                org.eclipse.swt.dnd.TextTransfer.getInstance() });
        cb.dispose();
        setStatus("✓  已複製程式碼到剪貼簿");
    }

    /**
     * Returns the code blocks from the last Claude response.
     * Called by ApplyCodeHandler (Alt+Shift+P).
     */
    public List<CodeBlock> getLastCodeBlocks() {
        return Collections.unmodifiableList(lastCodeBlocks);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Display helpers
    // ─────────────────────────────────────────────────────────────────────────

    private enum BubbleType { USER, ASSISTANT, SYSTEM }

    /**
     * Append a full message bubble (header + body) to the chat history.
     */
    private void appendBubble(String sender, String message, BubbleType type) {
        String header = "\n─── " + sender + " ────────────────────────────\n";
        int headerStart = chatHistory.getCharCount();
        chatHistory.append(header);
        applyHeaderStyle(headerStart, header.length(), type);
        // Force body text colour via StyleRange (immune to Eclipse CSS theme override)
        int bodyStart = chatHistory.getCharCount();
        chatHistory.append(message);
        applyBodyStyle(bodyStart, message.length());
        chatHistory.append("\n");
        scrollToBottom();
    }

    private void appendSystemMessage(String message) {
        appendBubble("系統", message, BubbleType.SYSTEM);
    }

    private void applyHeaderStyle(int start, int length, BubbleType type) {
        StyleRange sr = new StyleRange();
        sr.start     = start;
        sr.length    = length;
        sr.fontStyle = SWT.BOLD;
        sr.foreground = switch (type) {
            case USER      -> colorUserBubble;
            case ASSISTANT -> colorAssistantBubble;
            case SYSTEM    -> colorSystemBubble;
        };
        chatHistory.setStyleRange(sr);
    }

    /** Force body text colour — overrides Eclipse CSS theme. */
    private void applyBodyStyle(int start, int length) {
        if (length <= 0) return;
        StyleRange sr = new StyleRange();
        sr.start     = start;
        sr.length    = length;
        sr.foreground = colorTextDark;
        chatHistory.setStyleRange(sr);
    }

    /** Apply body style to a streaming token at the current end of document. */
    private void applyTokenStyle(int start, int length) {
        applyBodyStyle(start, length);
    }

    private void scrollToBottom() {
        chatHistory.setTopIndex(chatHistory.getLineCount() - 1);
    }

    /** Set busy state: disable/enable input controls. */
    private void setBusy(boolean busy) {
        isBusy = busy;
        if (!chatHistory.isDisposed()) {
            sendButton.setEnabled(!busy);
            inputField.setEnabled(!busy);
            setStatus(busy ? "Claude 思考中..." : "準備就緒");
            if (!busy) inputField.setFocus();
        }
    }

    public void setStatus(String text) {
        if (statusLabel != null && !statusLabel.isDisposed()) {
            statusLabel.setText(text);
            statusLabel.getParent().layout(true);
        }
    }

    private void refreshModelLabel() {
        if (modelLabel != null && !modelLabel.isDisposed()) {
            String m = Activator.getPref(Activator.PREF_MODEL);
            modelLabel.setText(m != null && !m.isEmpty() ? m : "claude-sonnet-4-6");
        }
    }

    private void showWelcome() {
        String cliPath = Activator.getPref(Activator.PREF_CLI_PATH);
        if (cliPath == null || cliPath.isBlank()) cliPath = "claude";
        boolean cliOk = ClaudeCliClient.isCliAvailable(cliPath);
        String version = cliOk ? ClaudeCliClient.getVersion(cliPath) : "";

        appendSystemMessage(
            "Claude Code for Eclipse  v1.0\n\n" +
            (cliOk
                ? "✓  Claude CLI 已就緒  " + (version.isEmpty() ? "" : "(" + version + ")") + "\n" +
                  "  請輸入您的問題開始對話。\n\n" +
                  "  • Ctrl+Enter    送出訊息\n" +
                  "  • Alt+Shift+C   重新開啟此面板\n" +
                  "  • Alt+Shift+E   解釋選取程式碼\n" +
                  "  • Alt+Shift+F   修正/改善程式碼"
                : "⚠  找不到 Claude Code CLI。\n\n" +
                  "  安裝步驟：\n" +
                  "  1. npm install -g @anthropic-ai/claude-code\n" +
                  "  2. 在終端執行 claude（開啟瀏覽器登入 Team 帳號）\n" +
                  "  3. Window > Preferences > Claude Code 確認路徑")
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Utilities
    // ─────────────────────────────────────────────────────────────────────────

    /** Run a Runnable on the SWT UI thread, safely checking disposal. */
    private static void safeUiExec(Display display, Runnable r) {
        if (display != null && !display.isDisposed()) {
            display.asyncExec(() -> {
                try { r.run(); } catch (Exception ignored) {}
            });
        }
    }

    /** Rough token count estimate for status display. */
    private static String tokenEstimate(String text) {
        int words = text.split("\\s+").length;
        return "~" + (words * 4 / 3) + " tokens";
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Colours & Fonts
    // ─────────────────────────────────────────────────────────────────────────

    private void initColours(Display d) {
        colorBackground      = new Color(d, 250, 250, 252);   // near-white bg
        colorHeaderBg        = new Color(d,  60,  90, 140);   // dark blue header
        colorUserBubble      = new Color(d,  30,  90, 180);   // blue
        colorAssistantBubble = new Color(d,  30, 130,  70);   // green
        colorSystemBubble    = new Color(d, 160, 100,  20);   // amber
        colorTextDark        = new Color(d,  40,  40,  40);   // dark text on light bg
        colorTextLight       = new Color(d, 255, 255, 255);   // white text on header
        colorAccent          = new Color(d,  30,  90, 180);   // accent blue
        colorError           = new Color(d, 180,  30,  30);   // error red
    }

    private void initFonts(Display d) {
        monoFont = new Font(d, FONT_NAME, FONT_SIZE, SWT.NORMAL);
        FontData[] fd = new FontData[]{ new FontData(FONT_NAME, HEADER_SIZE, SWT.BOLD) };
        boldFont = new Font(d, fd);
    }

    @Override
    public void setFocus() {
        if (inputField != null && !inputField.isDisposed()) {
            inputField.setFocus();
        }
    }

    @Override
    public void dispose() {
        Color[] colours = {
            colorBackground, colorHeaderBg, colorUserBubble,
            colorAssistantBubble, colorSystemBubble,
            colorTextDark, colorTextLight, colorAccent, colorError
        };
        for (Color c : colours) { if (c != null) c.dispose(); }
        if (monoFont != null) monoFont.dispose();
        if (boldFont != null) boldFont.dispose();
        super.dispose();
    }
}
