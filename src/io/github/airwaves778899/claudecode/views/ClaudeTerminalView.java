package io.github.airwaves778899.claudecode.views;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.*;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import io.github.airwaves778899.claudecode.Activator;
import io.github.airwaves778899.claudecode.mcp.McpTools;
import io.github.airwaves778899.claudecode.terminal.*;

/**
 * Claude Chat Terminal — VS Code Chat style, docked Eclipse View.
 *
 * Features (matching VS Code Claude Code):
 *  - Stop button to cancel in-progress response
 *  - Working dir display in header (clickable to change)
 *  - Slash commands  /explain /fix /doc /test /review /refactor /optimize
 *  - @ mention file picker (type @ to reference workspace files)
 *  - Code block action bar: Copy / Apply to File / View Diff
 *  - Inline tool-call activity display
 *  - Permission toggle 🔒 Ask / ⚡ Auto
 *  - Session history (save/load conversations)
 *  - Multi-line auto-growing input (Enter=send, Shift+Enter=newline)
 *  - Active file context badge (auto-fetched via MCP)
 */
public class ClaudeTerminalView extends ViewPart {

    public static final String ID = "io.github.airwaves778899.claudecode.views.ClaudeTerminalView";

    // ── Slash-command skill definitions ───────────────────────────────────────
    private static final String[][] SKILLS = {
        { "/explain",  "解釋這個 class 的作用與設計",
          "[Context: open file: %s]\n\n請詳細解釋這個 Java class 的功能：主要職責、類別設計、重要方法與邏輯流程。使用繁體中文。" },
        { "/fix",      "找出並修正錯誤",
          "[Context: open file: %s]\n\n請分析這個 Java class，找出所有錯誤、Bug、潛在問題並修正。說明每個問題的原因。" },
        { "/doc",      "為所有 public 方法加上 Javadoc",
          "[Context: open file: %s]\n\n請為這個 Java class 的所有 public 方法加上完整的 Javadoc（@param, @return, @throws），輸出整個修改後的檔案。" },
        { "/test",     "產生 JUnit 5 測試",
          "[Context: open file: %s]\n\n請為這個 Java class 產生完整的 JUnit 5 單元測試，涵蓋正常情況與邊界條件。" },
        { "/review",   "Code Review — 找出潛在問題",
          "[Context: open file: %s]\n\n請進行 Code Review，重點檢查：設計模式、效能問題、安全性、可讀性、重複程式碼，給出具體改善建議。" },
        { "/refactor", "重構以提升可讀性",
          "[Context: open file: %s]\n\n請重構這個 Java class：改善命名、拆分過長方法、消除重複碼，並說明每項改動原因，輸出完整修改後的檔案。" },
        { "/optimize", "效能優化建議",
          "[Context: open file: %s]\n\n請分析這個 Java class 的效能瓶頸，提供具體優化建議與改寫範例。" },
        { "/fields",   "列出所有欄位與其用途",
          "[Context: open file: %s]\n\n請列出這個 Java class 的所有欄位（fields），說明每個欄位的資料型別、用途與初始值。" },
        { "/new",      "開始新對話",      "__new__" },
        { "/history",  "查看歷史對話",    "__history__" },
        { "/help",     "顯示所有可用指令","__help__" },
    };

    // ── Widgets ────────────────────────────────────────────────────────────────
    private StyledText output;
    private StyledText inputArea;
    private Button     sendBtn;
    private Button     stopBtn;         // ■ Stop (visible when busy, in bottom row)
    private Button     headerStopBtn;   // ■ Stop duplicate in header (always visible)
    private Button     newBtn;
    private Button     permToggle;      // 🔒 Ask / ⚡ Auto
    private Combo      modelCombo;      // model selector (sonnet / opus / haiku)
    private Label      workDirLbl;      // clickable working dir
    private Composite  contextBar;
    private Label      activeFileLbl;
    private Composite  actionBar;       // code block actions
    private Button     copyCodeBtn;
    private Button     applyFileBtn;
    private Button     viewDiffBtn;
    private Composite  inputPanel;
    private Shell      completionPopup; // slash / @ popup
    private Table      completionTable;

    // ── State ──────────────────────────────────────────────────────────────────
    private boolean          firstMessage    = true;
    private volatile boolean busy            = false;
    private volatile boolean thinkingActive  = false;
    private Label            thinkingLbl;          // animated thinking indicator (Label widget)
    private final java.util.ArrayDeque<String> messageQueue = new java.util.ArrayDeque<>();
    private volatile int     claudeStart     = -1;
    private volatile Process currentProcess  = null;
    private boolean          autoPermissions = false;
    private String           activeFilePath  = null;
    private String           sessionId       = null;
    private List<String>     lastCodeBlocks  = new ArrayList<>();
    private String           currentWorkDir  = null;
    private Composite        launchPanel;         // VS Code-style landing screen
    private Label            statusBannerLbl;     // "Claude 已結束" notice
    private boolean          continueOnFirst = false; // add --continue on 1st send
    private volatile String  selectedModel   = "";    // "" = default (claude-sonnet-4-5)
    private IPartListener2          partListener    = null;  // listens for editor tab switches
    private IPropertyChangeListener prefListener    = null;  // listens for preference changes

    // ── Tool-call folding state (per response) ────────────────────────────────
    private int              toolCallCount   = 0;     // number of tool calls in current response
    private int              toolSectionStart= -1;    // char offset where tool section begins
    private List<String>     toolCallNames   = new ArrayList<>(); // names of tools used
    private boolean          toolSectionVisible = true;

    // ── Resources ──────────────────────────────────────────────────────────────
    private Color colBg, colInputBg, colHeaderBg, colContextBg, colActionBg;
    private Color colText, colYou, colClaude, colMeta, colTime;
    private Color colCode, colCodeBg, colHeading, colItalic;
    private Color colToolRead, colToolWrite, colToolOther;
    private Color colUserBg;    // background tint for user message lines (Cowork bubble style)
    private Font  monoFont, boldFont, chatFont;  // chatFont = proportional, for output area
    private MarkdownRenderer.Colors mdColors;

    // ── Regex patterns ─────────────────────────────────────────────────────────
    private static final Pattern TEXT_PATTERN  =
            Pattern.compile("\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern ANSI_PATTERN  =
            Pattern.compile("(?:\\[[0-9;]*[A-Za-z]|[^\\[])");
    private static final Pattern TOOL_NAME_PAT =
            Pattern.compile("\"name\":\"([^\"]+)\"");
    private static final Pattern PATH_PAT      =
            Pattern.compile("\"(?:path|command|file_path)\":\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern CODE_BLOCK_PAT=
            Pattern.compile("```(?:[a-zA-Z]*\n)?(.*?)```", Pattern.DOTALL);

    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm");

    // ══════════════════════════════════════════════════════════════════════════
    // createPartControl
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void createPartControl(Composite parent) {
        initResources(parent.getDisplay());
        currentWorkDir = ClaudeTerminalContext.getWorkDir();

        // ── Load initial preference values ──────────────────────────────────────
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        autoPermissions = store.getBoolean(Activator.PREF_AUTO_PERMISSIONS);
        String prefWorkDir = store.getString(Activator.PREF_WORK_DIR);
        if (prefWorkDir != null && !prefWorkDir.trim().isEmpty()) {
            currentWorkDir = prefWorkDir;
        }

        GridLayout gl = new GridLayout(1, false);
        gl.marginWidth = 0; gl.marginHeight = 0; gl.verticalSpacing = 0;
        parent.setLayout(gl);
        excl(parent);
        parent.setBackground(colBg);

        buildHeader(parent);

        // Apply model + perm prefs to widgets just created by buildHeader
        String modelPref = store.getString(Activator.PREF_MODEL);
        if (modelPref != null && !modelPref.isEmpty()) {
            String comboText = modelPref.startsWith("claude-") ? modelPref.substring(7) : modelPref;
            if (comboText.startsWith("haiku-4-5")) comboText = "haiku-4-5";  // strip version suffix
            for (String item : modelCombo.getItems()) {
                if (item.equals(comboText)) {
                    modelCombo.setText(comboText);
                    selectedModel = comboText;
                    break;
                }
            }
        }
        if (autoPermissions && permToggle != null && !permToggle.isDisposed()) {
            permToggle.setSelection(true);
            permToggle.setText("⚡");
        }

        // ── Register live preference change listener ─────────────────────────────
        prefListener = event -> {
            String key = event.getProperty();
            Display.getDefault().asyncExec(() -> {
                if (Activator.PREF_AUTO_PERMISSIONS.equals(key)) {
                    autoPermissions = store.getBoolean(Activator.PREF_AUTO_PERMISSIONS);
                    if (permToggle != null && !permToggle.isDisposed()) {
                        permToggle.setSelection(autoPermissions);
                        permToggle.setText(autoPermissions ? "⚡" : "🔒");
                    }
                } else if (Activator.PREF_WORK_DIR.equals(key)) {
                    String wd = store.getString(Activator.PREF_WORK_DIR);
                    if (wd != null && !wd.trim().isEmpty()) {
                        currentWorkDir = wd;
                        updateWorkDirLabel();
                    }
                } else if (Activator.PREF_MODEL.equals(key)) {
                    String mp = store.getString(Activator.PREF_MODEL);
                    if (mp != null && !mp.isEmpty() && modelCombo != null && !modelCombo.isDisposed()) {
                        String ct = mp.startsWith("claude-") ? mp.substring(7) : mp;
                        if (ct.startsWith("haiku-4-5")) ct = "haiku-4-5";
                        for (String item : modelCombo.getItems()) {
                            if (item.equals(ct)) {
                                modelCombo.setText(ct);
                                selectedModel = ct;
                                break;
                            }
                        }
                    }
                }
            });
        };
        store.addPropertyChangeListener(prefListener);

        buildLaunchPanel(parent);
        buildOutput(parent);
        buildActionBar(parent);
        buildContextBar(parent);
        buildInputArea(parent);

        sessionId = SessionHistory.newSession();
        inputArea.setFocus();
        fetchActiveFile();
        registerPartListener();   // auto-track editor tab changes

        parent.getDisplay().asyncExec(() -> {
            if (!parent.isDisposed()) parent.setBackground(colBg);
            if (!output.isDisposed()) { output.setBackground(colBg); output.setForeground(colText); }
        });
    }

    /** Register an IPartListener2 so that switching editor tabs auto-updates activeFilePath. */
    private void registerPartListener() {
        try {
            IWorkbenchWindow win = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (win == null) return;
            IWorkbenchPage page = win.getActivePage();
            if (page == null) return;

            partListener = new IPartListener2() {
                @Override public void partActivated(IWorkbenchPartReference ref) {
                    // Any part activated — check if it's an editor
                    if (ref.getPart(false) instanceof IEditorPart) fetchActiveFile();
                }
                @Override public void partBroughtToTop(IWorkbenchPartReference ref) {
                    if (ref.getPart(false) instanceof IEditorPart) fetchActiveFile();
                }
                // unused callbacks
                @Override public void partClosed(IWorkbenchPartReference ref) {}
                @Override public void partDeactivated(IWorkbenchPartReference ref) {}
                @Override public void partHidden(IWorkbenchPartReference ref) {}
                @Override public void partInputChanged(IWorkbenchPartReference ref) {
                    if (ref.getPart(false) instanceof IEditorPart) fetchActiveFile();
                }
                @Override public void partOpened(IWorkbenchPartReference ref) {}
                @Override public void partVisible(IWorkbenchPartReference ref) {}
            };
            page.addPartListener(partListener);
        } catch (Exception ignored) {}
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Header
    // ══════════════════════════════════════════════════════════════════════════

    private void buildHeader(Composite parent) {
        Composite hdr = new Composite(parent, SWT.NONE);
        GridLayout hl = new GridLayout(3, false);
        hl.marginWidth = 10; hl.marginHeight = 6;
        hdr.setLayout(hl);
        hdr.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        hdr.setBackground(colHeaderBg);
        excl(hdr);

        // Title
        Label title = new Label(hdr, SWT.NONE);
        title.setText("✦ Claude");
        title.setForeground(colClaude);
        title.setBackground(colHeaderBg);
        title.setFont(boldFont);

        // Working dir (center, clickable)
        workDirLbl = new Label(hdr, SWT.NONE);
        workDirLbl.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        workDirLbl.setForeground(colMeta);
        workDirLbl.setBackground(colHeaderBg);
        workDirLbl.setFont(monoFont);
        workDirLbl.setToolTipText("點擊更改工作目錄");
        updateWorkDirLabel();
        workDirLbl.setCursor(workDirLbl.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
        workDirLbl.addMouseListener(new MouseAdapter() {
            @Override public void mouseUp(MouseEvent e) { changeWorkDir(); }
        });

        // Right buttons
        Composite btns = new Composite(hdr, SWT.NONE);
        RowLayout rl = new RowLayout(SWT.HORIZONTAL);
        rl.spacing = 3; rl.marginHeight = 0; rl.marginWidth = 0;
        btns.setLayout(rl);
        btns.setBackground(colHeaderBg);
        excl(btns);

        // Model selector
        modelCombo = new Combo(btns, SWT.READ_ONLY | SWT.DROP_DOWN);
        modelCombo.setItems(new String[]{
            "sonnet-4-5", "opus-4-5", "haiku-4-5",
            "sonnet-4-6", "opus-4-6", "opus-4-7"
        });
        modelCombo.setText("sonnet-4-5");
        modelCombo.setToolTipText("選擇 Claude 模型");
        modelCombo.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                selectedModel = modelCombo.getText();
            }
        });

        Button histBtn = new Button(btns, SWT.PUSH);
        histBtn.setText("⏱");
        histBtn.setToolTipText("歷史對話");
        histBtn.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) { showHistory(histBtn); }
        });

        permToggle = new Button(btns, SWT.TOGGLE);
        permToggle.setText("🔒");
        permToggle.setToolTipText("🔒 Ask — 寫入前詢問確認\n⚡ Auto — 全自動（跳過所有確認）");
        permToggle.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                autoPermissions = permToggle.getSelection();
                permToggle.setText(autoPermissions ? "⚡" : "🔒");
            }
        });

        newBtn = new Button(btns, SWT.PUSH);
        newBtn.setText("⊕");
        newBtn.setToolTipText("開始新對話");
        newBtn.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) { newConversation(); }
        });

        // Header-level stop button — always visible even if bottom row is covered
        headerStopBtn = new Button(btns, SWT.PUSH);
        headerStopBtn.setText("■");
        headerStopBtn.setToolTipText("停止目前的回應 (Stop)");
        headerStopBtn.setEnabled(false);
        headerStopBtn.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) { stopQuery(); }
        });
    }

    private void updateWorkDirLabel() {
        if (workDirLbl == null || workDirLbl.isDisposed()) return;
        String dir = currentWorkDir != null ? currentWorkDir : "";
        // Show only last 2 path segments
        String[] parts = dir.replace('\\', '/').split("/");
        String display = parts.length >= 2
            ? "📁 " + parts[parts.length - 2] + "/" + parts[parts.length - 1]
            : "📁 " + dir;
        workDirLbl.setText(display);
        workDirLbl.setToolTipText(dir + "\n(點擊更改)");
    }

    private void changeWorkDir() {
        DirectoryDialog dlg = new DirectoryDialog(output.getShell(), SWT.OPEN);
        dlg.setText("選擇 Claude Terminal 工作目錄");
        dlg.setFilterPath(currentWorkDir);
        String chosen = dlg.open();
        if (chosen != null) {
            currentWorkDir = chosen;
            ClaudeTerminalContext.set(chosen,
                    ClaudeTerminalContext.getAddDirs(),
                    ClaudeTerminalContext.getClaudeExe());
            updateWorkDirLabel();
            appendMeta("  Working dir changed → " + chosen + "\n");
        }
    }

    /**
     * Called by handlers to pre-fill the input box with selected code from the editor.
     * Opens the chat view (shows main content) and populates the input area.
     * Safe to call from any thread.
     */
    public void setSelectedCode(String code, String filePath) {
        Display.getDefault().asyncExec(() -> {
            // Make sure the main content is visible (not the launch panel)
            showMainContent();
            if (filePath != null) activeFilePath = filePath;
            if (inputArea == null || inputArea.isDisposed()) return;
            String prompt = "[Selected code from " + shortName(filePath != null ? filePath : "editor") + "]\n"
                          + "```\n" + code + "\n```\n\n";
            inputArea.setText(prompt);
            inputArea.setForeground(colText);
            inputArea.setCaretOffset(prompt.length());
            inputArea.setFocus();
            autoGrow();
        });
    }

    /**
     * Called by handlers to programmatically switch the working directory
     * (e.g. when opened via Package Explorer right-click → Open Claude Chat).
     * Safe to call from any thread.
     */
    public void setWorkDir(String path) {
        if (path == null || path.isBlank()) return;
        currentWorkDir = path;
        ClaudeTerminalContext.set(path,
                ClaudeTerminalContext.getAddDirs(),
                ClaudeTerminalContext.getClaudeExe());
        Display.getDefault().asyncExec(() -> {
            updateWorkDirLabel();
            appendMeta("  Working dir → " + path + "\n");
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Output area
    // ══════════════════════════════════════════════════════════════════════════

    // ══════════════════════════════════════════════════════════════════════════
    // Launch panel (VS Code-style landing screen)
    // ══════════════════════════════════════════════════════════════════════════

    private void buildLaunchPanel(Composite parent) {
        launchPanel = new Composite(parent, SWT.NONE);
        GridLayout ll = new GridLayout(1, false);
        ll.marginWidth = 0; ll.marginHeight = 0; ll.verticalSpacing = 0;
        launchPanel.setLayout(ll);
        GridData lgd = new GridData(SWT.FILL, SWT.FILL, true, true);
        launchPanel.setLayoutData(lgd);
        launchPanel.setBackground(colBg);
        excl(launchPanel);

        // ScrolledComposite so buttons are reachable when the panel is short
        ScrolledComposite sc = new ScrolledComposite(launchPanel,
                SWT.V_SCROLL | SWT.H_SCROLL);
        sc.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        sc.setBackground(colBg);
        sc.setExpandHorizontal(true);
        sc.setExpandVertical(true);
        excl(sc);

        // Content wrapper — centred inside the scroll area
        Composite center = new Composite(sc, SWT.NONE);
        GridLayout cl = new GridLayout(1, false);
        cl.marginWidth = 50; cl.marginHeight = 40; cl.verticalSpacing = 12;
        center.setLayout(cl);
        center.setBackground(colBg);
        excl(center);
        sc.setContent(center);

        // ✦ Claude Code title
        Label titleLbl = new Label(center, SWT.NONE);
        titleLbl.setText("✦  Claude Code");
        titleLbl.setFont(boldFont);
        titleLbl.setForeground(colClaude);
        titleLbl.setBackground(colBg);
        titleLbl.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        Label subLbl = new Label(center, SWT.NONE);
        subLbl.setText("Eclipse IDE Integration");
        subLbl.setForeground(colMeta);
        subLbl.setBackground(colBg);
        subLbl.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        // Spacer
        Label gap = new Label(center, SWT.NONE);
        gap.setBackground(colBg);

        // New session button
        Button newSessionBtn = new Button(center, SWT.PUSH);
        newSessionBtn.setText("  ▶   新對話  ");
        newSessionBtn.setToolTipText("開始全新的 Claude 對話");
        GridData ngd = new GridData(SWT.CENTER, SWT.CENTER, true, false);
        ngd.minimumWidth = 220; ngd.heightHint = 36;
        newSessionBtn.setLayoutData(ngd);
        newSessionBtn.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                continueOnFirst = false;
                showMainContent();
            }
        });

        // Continue last session button
        Button contBtn = new Button(center, SWT.PUSH);
        contBtn.setText("  ⏭   繼續上次對話  ");
        contBtn.setToolTipText("繼續上一個 Claude CLI 對話");
        GridData cgd = new GridData(SWT.CENTER, SWT.CENTER, true, false);
        cgd.minimumWidth = 220; cgd.heightHint = 36;
        contBtn.setLayoutData(cgd);
        contBtn.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                continueOnFirst = true;
                showMainContent();
            }
        });

        // View history button
        Button histLaunchBtn = new Button(center, SWT.PUSH);
        histLaunchBtn.setText("  📚   選擇歷史對話  ");
        histLaunchBtn.setToolTipText("從歷史記錄中選擇並恢復對話");
        GridData hgd = new GridData(SWT.CENTER, SWT.CENTER, true, false);
        hgd.minimumWidth = 220; hgd.heightHint = 36;
        histLaunchBtn.setLayoutData(hgd);
        histLaunchBtn.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                showMainContent();
                showHistory(null);
            }
        });

        // Compute minimum size so the scroll kicks in when the panel is too short
        org.eclipse.swt.graphics.Point minSz = center.computeSize(
                SWT.DEFAULT, SWT.DEFAULT);
        sc.setMinSize(minSz);
    }

    /** Transition from launch screen to main chat UI. */
    private void showMainContent() {
        if (launchPanel == null || launchPanel.isDisposed()) return;
        GridData lgd = (GridData) launchPanel.getLayoutData();
        lgd.exclude = true;
        launchPanel.setVisible(false);

        GridData ogd = (GridData) output.getLayoutData();
        ogd.exclude = false;
        output.setVisible(true);

        launchPanel.getParent().layout(true, true);
        showWelcome();
        if (!inputArea.isDisposed()) inputArea.setFocus();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Output area
    // ══════════════════════════════════════════════════════════════════════════

    private void buildOutput(Composite parent) {
        output = new StyledText(parent, SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
        GridData ogd = new GridData(SWT.FILL, SWT.FILL, true, true);
        ogd.exclude = true;   // hidden until launch option selected
        output.setLayoutData(ogd);
        output.setVisible(false);
        output.setBackground(colBg);
        output.setForeground(colText);
        output.setFont(chatFont);          // proportional font for readable chat text
        output.setEditable(false);
        output.setMargins(20, 14, 20, 12); // more breathing room
        output.setLineSpacing(4);          // looser line height — closer to Cowork feel
        excl(output);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Action bar (code block actions — appears after each response)
    // ══════════════════════════════════════════════════════════════════════════

    private void buildActionBar(Composite parent) {
        Label sep = new Label(parent, SWT.HORIZONTAL | SWT.SEPARATOR);
        sep.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        actionBar = new Composite(parent, SWT.NONE);
        RowLayout al = new RowLayout(SWT.HORIZONTAL);
        al.marginWidth = 10; al.marginHeight = 5; al.spacing = 6;
        actionBar.setLayout(al);
        GridData agd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        actionBar.setLayoutData(agd);
        actionBar.setBackground(colActionBg);
        excl(actionBar);

        copyCodeBtn = new Button(actionBar, SWT.PUSH);
        copyCodeBtn.setText("📋 Copy");
        copyCodeBtn.setToolTipText("複製最後一個程式碼區塊到剪貼簿");
        copyCodeBtn.setEnabled(false);
        copyCodeBtn.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) { copyLastCodeBlock(); }
        });

        applyFileBtn = new Button(actionBar, SWT.PUSH);
        applyFileBtn.setText("✏️ Apply to File");
        applyFileBtn.setToolTipText("將程式碼套用到目前開啟的檔案（顯示 Diff 確認）");
        applyFileBtn.setEnabled(false);
        applyFileBtn.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) { applyCodeToFile(); }
        });

        viewDiffBtn = new Button(actionBar, SWT.PUSH);
        viewDiffBtn.setText("🔍 View Diff");
        viewDiffBtn.setToolTipText("預覽修改前後的差異");
        viewDiffBtn.setEnabled(false);
        viewDiffBtn.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) { viewDiff(); }
        });

        // Initially hidden — shown after response with code blocks
        agd.exclude = true;
        actionBar.setVisible(false);
    }

    private void showActionBar(boolean show) {
        if (actionBar == null || actionBar.isDisposed()) return;
        Display d = actionBar.getDisplay();
        d.asyncExec(() -> {
            if (actionBar.isDisposed()) return;
            GridData gd = (GridData) actionBar.getLayoutData();
            gd.exclude = !show;
            actionBar.setVisible(show);
            actionBar.getParent().layout(true, true);
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Context badge bar
    // ══════════════════════════════════════════════════════════════════════════

    private void buildContextBar(Composite parent) {
        Label sep2 = new Label(parent, SWT.HORIZONTAL | SWT.SEPARATOR);
        sep2.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        contextBar = new Composite(parent, SWT.NONE);
        GridLayout cl = new GridLayout(3, false);
        cl.marginWidth = 10; cl.marginHeight = 4; cl.horizontalSpacing = 5;
        contextBar.setLayout(cl);
        contextBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        contextBar.setBackground(colContextBg);
        excl(contextBar);

        Label icon = new Label(contextBar, SWT.NONE);
        icon.setText("📄");
        icon.setBackground(colContextBg);

        activeFileLbl = new Label(contextBar, SWT.NONE);
        activeFileLbl.setText("(no file open)");
        activeFileLbl.setForeground(colMeta);
        activeFileLbl.setBackground(colContextBg);
        activeFileLbl.setFont(monoFont);
        activeFileLbl.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Button refresh = new Button(contextBar, SWT.PUSH | SWT.FLAT);
        refresh.setText("↻");
        refresh.setToolTipText("重新抓取目前開啟的檔案");
        refresh.setBackground(colContextBg);
        refresh.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) { fetchActiveFile(); }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Input area (multi-line, auto-grow)
    // ══════════════════════════════════════════════════════════════════════════

    private void buildInputArea(Composite parent) {
        // Thinking spinner — 放在 parent（非 inputPanel 內），避免顯示時把輸入框推出畫面
        // output StyledText 有 grabExcessVerticalSpace=true，會自動吸收高度變化
        thinkingLbl = new Label(parent, SWT.NONE);
        thinkingLbl.setText("  ⠋ 思考中…");
        thinkingLbl.setForeground(colClaude);
        thinkingLbl.setBackground(colBg);
        GridData tgd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        tgd.exclude = true;
        thinkingLbl.setLayoutData(tgd);
        thinkingLbl.setVisible(false);

        Label sep3 = new Label(parent, SWT.HORIZONTAL | SWT.SEPARATOR);
        sep3.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        inputPanel = new Composite(parent, SWT.NONE);
        GridLayout igl = new GridLayout(1, false);
        igl.marginWidth = 10; igl.marginHeight = 8; igl.verticalSpacing = 5;
        inputPanel.setLayout(igl);
        inputPanel.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
        inputPanel.setBackground(colBg);
        excl(inputPanel);

        // Status banner ("Claude 已結束") — hidden until process exits
        statusBannerLbl = new Label(inputPanel, SWT.NONE);
        statusBannerLbl.setText("  ●  Claude 已結束 — 輸入訊息重新開始，或點擊 ⊕ 新對話");
        statusBannerLbl.setForeground(colToolWrite);
        statusBannerLbl.setBackground(colBg);
        GridData sbgd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        sbgd.exclude = true;
        statusBannerLbl.setLayoutData(sbgd);
        statusBannerLbl.setVisible(false);
        excl(statusBannerLbl);

        inputArea = new StyledText(inputPanel, SWT.MULTI | SWT.WRAP | SWT.BORDER | SWT.V_SCROLL);
        GridData igd = new GridData(SWT.FILL, SWT.FILL, true, false);
        igd.heightHint = 66;
        inputArea.setLayoutData(igd);
        inputArea.setBackground(colInputBg);
        inputArea.setForeground(colText);
        inputArea.setFont(monoFont);
        inputArea.setMargins(8, 6, 8, 6);
        inputArea.setLineSpacing(1);
        excl(inputArea);

        // Placeholder text
        setPlaceholder();
        inputArea.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (inputArea.getText().equals(PLACEHOLDER)) {
                    inputArea.setText(""); inputArea.setForeground(colText);
                }
            }
            @Override public void focusLost(FocusEvent e) { setPlaceholder(); }
        });

        inputArea.addModifyListener(e -> {
            autoGrow();
            String text = inputArea.getText();
            if (text.startsWith("/")) showCompletionPopup(text, false);
            else if (text.contains("@") && !text.contains(" @")) {
                int at = text.lastIndexOf('@');
                showCompletionPopup(text.substring(at), true);
            } else {
                hideCompletionPopup();
            }
        });

        inputArea.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (completionPopup != null && !completionPopup.isDisposed()) {
                    if (e.keyCode == SWT.ARROW_UP)   { e.doit=false; moveCompletion(-1); return; }
                    if (e.keyCode == SWT.ARROW_DOWN) { e.doit=false; moveCompletion(+1); return; }
                    if (e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR) { e.doit=false; applyCompletion(); return; }
                    if (e.keyCode == SWT.ESC) { hideCompletionPopup(); return; }
                }
                if ((e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR) && (e.stateMask & SWT.SHIFT) == 0) {
                    e.doit = false; sendMessage();
                }
            }
        });

        // Bottom row: hint + stop + send
        Composite btmRow = new Composite(inputPanel, SWT.NONE);
        GridLayout brl = new GridLayout(3, false);
        brl.marginWidth = 0; brl.marginHeight = 0;
        btmRow.setLayout(brl);
        btmRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        btmRow.setBackground(colBg);
        excl(btmRow);

        Label hint = new Label(btmRow, SWT.NONE);
        hint.setText("/ commands  •  @ files  •  Shift+Enter new line");
        hint.setForeground(colTime);
        hint.setBackground(colBg);
        hint.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        stopBtn = new Button(btmRow, SWT.PUSH);
        stopBtn.setText("■ Stop");
        stopBtn.setToolTipText("停止目前的回應");
        stopBtn.setEnabled(false);
        stopBtn.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        stopBtn.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) { stopQuery(); }
        });

        sendBtn = new Button(btmRow, SWT.PUSH);
        sendBtn.setText("Send ▷");
        sendBtn.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        sendBtn.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) { sendMessage(); }
        });
    }

    private static final String PLACEHOLDER = "Ask Claude...  (/ for commands,  @ for files)";
    private void setPlaceholder() {
        if (inputArea.isDisposed()) return;
        if (inputArea.getText().isEmpty()) {
            inputArea.setText(PLACEHOLDER);
            inputArea.setForeground(colTime);
        }
    }
    private String getInput() {
        String t = inputArea.getText().trim();
        return t.equals(PLACEHOLDER) ? "" : t;
    }

    private void autoGrow() {
        if (inputArea.isDisposed() || inputPanel.isDisposed()) return;
        int lines = Math.max(2, Math.min(8, inputArea.getLineCount()));
        int newH   = inputArea.getLineHeight() * lines
                   + inputArea.getTopMargin() + inputArea.getBottomMargin() + 4;
        GridData gd = (GridData) inputArea.getLayoutData();
        if (Math.abs(gd.heightHint - newH) > 2) {
            gd.heightHint = newH;
            inputPanel.layout(true, true);
            inputPanel.getParent().layout(true);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Completion popup (/ commands and @ files)
    // ══════════════════════════════════════════════════════════════════════════

    private void showCompletionPopup(String filter, boolean isAtMention) {
        List<String[]> matches = isAtMention
            ? findWorkspaceFiles(filter.substring(1))  // strip @
            : findSkillMatches(filter);

        if (matches.isEmpty()) { hideCompletionPopup(); return; }

        if (completionPopup == null || completionPopup.isDisposed()) {
            completionPopup = new Shell(inputArea.getShell(), SWT.NO_TRIM | SWT.ON_TOP);
            completionPopup.setLayout(new FillLayout());
            completionTable = new Table(completionPopup, SWT.SINGLE | SWT.FULL_SELECTION);
            completionTable.setBackground(colHeaderBg);
            completionTable.setForeground(colText);
            completionTable.setFont(monoFont);
            new TableColumn(completionTable, SWT.NONE);
            new TableColumn(completionTable, SWT.NONE);
            completionTable.addMouseListener(new MouseAdapter() {
                @Override public void mouseDoubleClick(MouseEvent e) { applyCompletion(); }
            });
        }

        completionTable.removeAll();
        for (String[] item : matches) {
            TableItem ti = new TableItem(completionTable, SWT.NONE);
            ti.setText(0, item[0]);
            ti.setText(1, "  " + item[1]);
            ti.setData(item);
        }
        if (completionTable.getItemCount() > 0) completionTable.setSelection(0);
        completionTable.getColumn(0).pack();
        completionTable.getColumn(1).pack();

        Point loc = inputArea.toDisplay(0, 0);
        int h = Math.min(matches.size(), 9) * 22 + 6;
        int w = Math.max(420, inputArea.getSize().x);
        completionPopup.setBounds(loc.x, loc.y - h, w, h);
        completionPopup.setVisible(true);
        completionPopup.moveAbove(null);
    }

    private void hideCompletionPopup() {
        if (completionPopup != null && !completionPopup.isDisposed())
            completionPopup.setVisible(false);
    }

    private void moveCompletion(int delta) {
        int sel = completionTable.getSelectionIndex() + delta;
        sel = Math.max(0, Math.min(completionTable.getItemCount() - 1, sel));
        completionTable.setSelection(sel);
    }

    private void applyCompletion() {
        if (completionTable == null || completionTable.isDisposed()) return;
        int idx = completionTable.getSelectionIndex();
        if (idx < 0) return;
        String[] item = (String[]) completionTable.getItem(idx).getData();
        hideCompletionPopup();

        boolean isSkill = item[0].startsWith("/");
        if (isSkill) {
            String template = item[2];
            if ("__new__".equals(template))     { inputArea.setText(""); newConversation(); return; }
            if ("__history__".equals(template)) { inputArea.setText(""); showHistory(null); return; }
            if ("__help__".equals(template))    { inputArea.setText(""); showHelp(); return; }
            String fp = activeFilePath != null ? activeFilePath : "(no file open)";
            String expanded = String.format(template, fp);
            inputArea.setText(expanded);
            inputArea.setForeground(colText);
            inputArea.setCaretOffset(expanded.length());
        } else {
            // @ file — append reference + queue file read
            String text = inputArea.getText();
            int at = text.lastIndexOf('@');
            String newText = (at >= 0 ? text.substring(0, at) : text) + item[0];
            inputArea.setText(newText);
            inputArea.setForeground(colText);
            inputArea.setCaretOffset(newText.length());
        }
        autoGrow();
    }

    private List<String[]> findSkillMatches(String filter) {
        String f = filter.toLowerCase();
        List<String[]> r = new ArrayList<>();
        for (String[] s : SKILLS) { if (s[0].startsWith(f)) r.add(s); }
        return r;
    }

    private List<String[]> findWorkspaceFiles(String filter) {
        List<String[]> r = new ArrayList<>();
        try {
            IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
            String fl = filter.toLowerCase();
            for (IProject p : projects) {
                if (!p.isOpen()) continue;
                scanForJava(p, fl, r, 0);
                if (r.size() >= 20) break;
            }
        } catch (Exception ignored) {}
        return r;
    }

    private void scanForJava(IContainer c, String filter, List<String[]> out, int depth) {
        if (depth > 8 || out.size() >= 20) return;
        try {
            for (IResource r : c.members()) {
                if (r instanceof IFile) {
                    IFile f = (IFile) r;
                    if (f.getName().toLowerCase().contains(filter)
                            && (f.getName().endsWith(".java") || f.getName().endsWith(".xml"))) {
                        String path = f.getLocation().toOSString();
                        out.add(new String[]{ f.getName(), path, path });
                    }
                } else if (r instanceof IContainer) {
                    scanForJava((IContainer) r, filter, out, depth + 1);
                }
                if (out.size() >= 20) return;
            }
        } catch (Exception ignored) {}
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Active file fetching
    // ══════════════════════════════════════════════════════════════════════════

    private void fetchActiveFile() {
        // Use Eclipse Platform API directly — avoids JSON regex path truncation on Windows
        Display.getDefault().asyncExec(() -> {
            try {
                IWorkbenchWindow win = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                if (win == null) return;
                IEditorPart editor = win.getActivePage() != null
                        ? win.getActivePage().getActiveEditor() : null;
                if (editor == null) return;
                IEditorInput input = editor.getEditorInput();
                IFile iFile = input.getAdapter(IFile.class);
                if (iFile == null || iFile.getLocation() == null) return;
                String path = iFile.getLocation().toOSString();
                activeFilePath = path;

                // ── 自動切換 workdir 到該檔案所屬的專案根目錄 ──────────────────
                IProject proj = iFile.getProject();
                boolean autoSwitch = Activator.getDefault().getPreferenceStore()
                        .getBoolean(Activator.PREF_AUTO_SWITCH_WORKDIR);
                if (proj != null && proj.getLocation() != null && !busy && autoSwitch) {
                    String projRoot = proj.getLocation().toOSString();
                    if (!projRoot.equals(currentWorkDir)) {
                        currentWorkDir = projRoot;
                        updateWorkDirLabel();
                        // 在 output 裡顯示一行提示，讓使用者知道 workdir 切換了
                        appendMeta("  📁 已切換專案：" + proj.getName()
                                   + "  →  " + projRoot + "\n");
                    }
                }

                String name = shortName(path);
                if (!activeFileLbl.isDisposed()) {
                    activeFileLbl.setText(name + "  —  " + path);
                    activeFileLbl.setToolTipText(path);
                    contextBar.layout(true);
                }
            } catch (Exception ignored) {}
        });
    }

    private static String shortName(String path) {
        int i = Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/'));
        return i >= 0 ? path.substring(i + 1) : path;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Welcome / Help / History
    // ══════════════════════════════════════════════════════════════════════════

    private void showWelcome() {
        appendMeta("╔══════════════════════════════════════════════════╗\n");
        appendMeta("║  ✦ Claude Code  ·  Eclipse Chat Terminal         ║\n");
        appendMeta("╚══════════════════════════════════════════════════╝\n");
        appendTime("  Working dir: " + currentWorkDir + "\n\n");
    }

    private void showHelp() {
        appendMeta("\n━━━━━━━━━  Available Commands  ━━━━━━━━━\n\n");
        for (String[] s : SKILLS) {
            if (!s[2].startsWith("__")) appendMeta(String.format("  %-12s  %s\n", s[0], s[1]));
            else appendMeta(String.format("  %-12s  %s\n", s[0], s[1]));
        }
        appendMeta("\n  @ filename   引用 workspace 中的檔案作為 context\n\n");
    }

    private void showHistory(Button anchor) {
        List<Map<String,String>> sessions = SessionHistory.recentSessions(15);
        if (sessions.isEmpty()) {
            appendMeta("\n  (No conversation history yet)\n\n");
            return;
        }
        Menu menu = new Menu(output.getShell(), SWT.POP_UP);
        MenuItem hdr = new MenuItem(menu, SWT.NONE);
        hdr.setText("Recent Conversations");
        hdr.setEnabled(false);
        new MenuItem(menu, SWT.SEPARATOR);
        for (Map<String,String> s : sessions) {
            MenuItem mi = new MenuItem(menu, SWT.NONE);
            mi.setText(s.getOrDefault("date","") + "  " + s.getOrDefault("preview",""));
            String sid = s.get("id");
            mi.addSelectionListener(new SelectionAdapter() {
                @Override public void widgetSelected(SelectionEvent e) {
                    loadHistorySession(sid);
                }
            });
        }
        new MenuItem(menu, SWT.SEPARATOR);
        MenuItem clear = new MenuItem(menu, SWT.NONE);
        clear.setText("Clear History");
        clear.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                if (MessageDialog.openConfirm(output.getShell(), "Clear History", "刪除所有歷史對話記錄？"))
                    SessionHistory.clearAll();
            }
        });
        menu.setVisible(true);
    }

    private void loadHistorySession(String sid) {
        List<Map<String,String>> turns = SessionHistory.loadSession(sid);
        if (turns.isEmpty()) return;
        appendMeta("\n═══════════════  History: " + sid + "  ═══════════════\n\n");
        for (Map<String,String> t : turns) {
            String role = t.getOrDefault("role","?");
            String text = t.getOrDefault("text","");
            String ts   = t.getOrDefault("ts","");
            if ("user".equals(role)) {
                appendHeader("You", ts, colYou);
                appendYou(text);
            } else {
                appendHeader("Claude", ts, colClaude);
                int start = output.getCharCount();
                appendToken(text);
                Display.getDefault().asyncExec(() -> {
                    if (!output.isDisposed()) applyMarkdown(start, text);
                });
            }
            appendMeta("\n");
        }
        appendMeta("═══════════════════════════════════════════════════════\n\n");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Send / Stop / New
    // ══════════════════════════════════════════════════════════════════════════

    private void sendMessage() {
        String msg = getInput();
        if (msg.isEmpty()) return;
        inputArea.setText("");
        inputArea.setForeground(colText);
        autoGrow();

        if (busy) {
            // Queue — will auto-send when Claude finishes
            messageQueue.addLast(msg);
            appendMeta("  ⏳ 已排隊 [" + messageQueue.size() + "]：" +
                       (msg.length() > 40 ? msg.substring(0, 40) + "…" : msg) + "\n");
            output.setTopIndex(output.getLineCount() - 1);
            return;
        }
        dispatchMessage(msg);
    }

    /** Dispatch a message immediately (used by sendMessage and queue auto-drain). */
    private void dispatchMessage(String msg) {
        busy = true; claudeStart = -1;

        if (statusBannerLbl != null && !statusBannerLbl.isDisposed()) {
            GridData sbgd = (GridData) statusBannerLbl.getLayoutData();
            sbgd.exclude = true;
            statusBannerLbl.setVisible(false);
            statusBannerLbl.getParent().layout(true, true);
        }
        lastCodeBlocks.clear();
        showActionBar(false);

        String ts = TIME_FMT.format(new Date());
        appendHeader("You", ts, colYou);
        appendYou(msg);
        appendMeta("\n");
        SessionHistory.append(sessionId, "user", msg, ts);

        String fullPrompt = expandAtMentions(buildPromptWithContext(msg));

        if (!autoPermissions && looksLikeWriteOp(msg)) {
            boolean[] proceed = {true};
            output.getDisplay().syncExec(() -> {
                proceed[0] = MessageDialog.openQuestion(output.getShell(), "Claude Permission",
                    "此操作可能會修改檔案。\n目前開啟：" + (activeFilePath != null ? activeFilePath : "(none)") +
                    "\n\n允許 Claude 讀寫檔案？");
            });
            if (!proceed[0]) {
                busy = false;
                appendMeta("(Cancelled)\n\n"); return;
            }
        }

        boolean isFirst = firstMessage; firstMessage = false;
        setInputEnabled(false);   // enable Stop button, disable Send while busy
        new Thread(() -> runQuery(fullPrompt, isFirst), "claude-view-query").start();
    }

    private void stopQuery() {
        Process p = currentProcess;
        if (p != null) {
            p.destroyForcibly();
            appendMeta("\n(Stopped)\n\n");
        }
    }

    private void newConversation() {
        if (busy) return;
        firstMessage = true; claudeStart = -1; continueOnFirst = false;
        sessionId = SessionHistory.newSession();
        lastCodeBlocks.clear();
        showActionBar(false);
        appendMeta("\n─────────────────────  New Conversation  ─────────────────────\n\n");
        fetchActiveFile();
    }

    private String buildPromptWithContext(String msg) {
        if (activeFilePath == null) return msg;
        boolean includeFile = Activator.getDefault().getPreferenceStore()
                .getBoolean(Activator.PREF_INCLUDE_ACTIVE_FILE);
        if (!includeFile) return msg;
        return "[Eclipse context: currently open file: " + activeFilePath + "]\n\n" + msg;
    }

    private String expandAtMentions(String prompt) {
        Pattern atPat = Pattern.compile("@(\\S+)");
        Matcher m = atPat.matcher(prompt);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String filename = m.group(1);
            String content = tryReadFile(filename);
            if (content != null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(
                    "@" + filename + "\n```\n" + content.substring(0,
                    Math.min(content.length(), 8000)) + "\n```"));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String tryReadFile(String nameOrPath) {
        // Try as absolute path first
        File f = new File(nameOrPath);
        if (f.isFile()) {
            try { return Files.readString(f.toPath(), StandardCharsets.UTF_8); } catch (Exception ignore) {}
        }
        // Try searching workspace
        try {
            IProject[] projs = ResourcesPlugin.getWorkspace().getRoot().getProjects();
            for (IProject p : projs) {
                if (!p.isOpen()) continue;
                String found = findInProject(p, nameOrPath, 0);
                if (found != null) return found;
            }
        } catch (Exception ignore) {}
        return null;
    }

    private String findInProject(IContainer c, String name, int depth) {
        if (depth > 8) return null;
        try {
            for (IResource r : c.members()) {
                if (r instanceof IFile && r.getName().equalsIgnoreCase(name)) {
                    try { return Files.readString(Paths.get(((IFile)r).getLocation().toOSString()), StandardCharsets.UTF_8); }
                    catch (Exception ignore) {}
                } else if (r instanceof IContainer) {
                    String found = findInProject((IContainer) r, name, depth + 1);
                    if (found != null) return found;
                }
            }
        } catch (Exception ignore) {}
        return null;
    }

    private static boolean looksLikeWriteOp(String m) {
        String lo = m.toLowerCase();
        return lo.contains("修改")||lo.contains("刪除")||lo.contains("新增")
            ||lo.contains("edit")||lo.contains("write")||lo.contains("creat")
            ||lo.contains("delete")||lo.contains("refactor")
            ||lo.contains("加")||lo.contains("改")||lo.contains("重構");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Query execution
    // ══════════════════════════════════════════════════════════════════════════

    private void runQuery(String prompt, boolean isFirstMessage) {
        // Resolve working directory (keep native OS separators for ProcessBuilder)
        String workDir = currentWorkDir != null ? currentWorkDir : ClaudeTerminalContext.getWorkDir();

        try {
            // ── Run claude.exe directly (native Windows PE, no shell wrapper needed) ──
            // Java's ProcessBuilder handles argument quoting at the OS level, so we can
            // pass the prompt string directly without any shell-escaping gymnastics.
            String claudeExe = findClaudeExe();

            List<String> cmd = new ArrayList<>();
            cmd.add(claudeExe);
            cmd.add("--dangerously-skip-permissions");
            cmd.add("--verbose");
            cmd.add("--output-format"); cmd.add("stream-json");
            if (!isFirstMessage || continueOnFirst) cmd.add("--continue");
            // Model selection (read from UI on background thread — safe: selectedModel is a volatile String)
            final String model = selectedModel;
            if (model != null && !model.isEmpty()) {
                cmd.add("--model"); cmd.add("claude-" + model);
            }
            cmd.add("-p"); cmd.add(prompt);
            for (String d : ClaudeTerminalContext.getAddDirs()) { cmd.add("--add-dir"); cmd.add(d); }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(workDir));
            pb.environment().put("NO_COLOR","1"); pb.environment().put("FORCE_COLOR","0");
            pb.environment().put("TERM","dumb");
            pb.redirectErrorStream(false);
            // Redirect stdin from the OS null device — suppresses "no stdin data" warnings
            boolean isWinOs = System.getProperty("os.name","").toLowerCase().contains("win");
            pb.redirectInput(ProcessBuilder.Redirect.from(new java.io.File(isWinOs ? "NUL" : "/dev/null")));

            currentProcess = pb.start();
            OutputStream stdin = currentProcess.getOutputStream();

            // Claude header
            String ts = TIME_FMT.format(new Date());
            output.getDisplay().asyncExec(() -> {
                if (!output.isDisposed()) appendHeader("Claude", ts, colClaude);
            });

            startThinking();  // show animated label indicator
            // Reset tool-call tracking for this new response
            toolCallCount = 0; toolCallNames = new ArrayList<>(); toolSectionStart = -1;





























            // Stderr thread (permission prompts)
            Process proc = currentProcess;
            Thread errThread = new Thread(() -> {
                try (BufferedReader err = new BufferedReader(
                        new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = err.readLine()) != null) {
                        String l = line.trim();
                        if (l.isEmpty() || l.startsWith("{")) continue;
                        // Suppress harmless CLI noise
                        if (l.contains("EEXIST") || l.contains("file already exists")
                                || l.contains("no stdin data")) continue;
                        if ((l.contains("Do you want")||l.contains("Allow?")||l.contains("[y/n]"))
                                && l.contains("?")) {
                            handlePermissionPrompt(l, stdin);
                        } else {
                            appendMeta("  ℹ " + l + "\n");
                        }
                    }
                } catch (IOException ignored) {}
            }, "claude-stderr");
            errThread.setDaemon(true); errThread.start();

            // Stdout reader
            StringBuilder lastText = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(currentProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || !line.startsWith("{")) continue;

                    if (line.contains("\"type\":\"assistant\"")) {
                        Matcher m = TEXT_PATTERN.matcher(line);
                        String last = null;
                        while (m.find()) last = m.group(1);
                        if (last != null) {
                            String text = unescapeJson(last);
                            if (text.length() > lastText.length()) {
                                stopThinking();
                                String delta = text.substring(lastText.length());
                                lastText = new StringBuilder(text);
                                appendToken(stripAnsi(delta));
                            }
                        }
                    } else if (line.contains("\"type\":\"tool_use\"")) {
                        stopThinking();
                        handleToolUse(line, stdin);
                    } else if (line.contains("\"type\":\"result\"")) {
                        if (lastText.length() == 0) {
                            Pattern rp = Pattern.compile("\"result\":\"((?:[^\"\\\\]|\\\\.)*)\"");
                            Matcher rm = rp.matcher(line);
                            if (rm.find()) {
                                String t = stripAnsi(unescapeJson(rm.group(1)));
                                appendToken(t); lastText = new StringBuilder(t);
                            }
                        }
                        break;
                    } else if (line.contains("\"is_error\":true")) {
                        Pattern ep = Pattern.compile("\"content\":\"((?:[^\"\\\\]|\\\\.)*)\"");
                        Matcher em = ep.matcher(line);
                        if (em.find()) {
                            String msg = stripAnsi(unescapeJson(em.group(1)));
                            // 清除 XML tags（如 <tool_use_error>…</tool_use_error>）
                            msg = msg.replaceAll("<[^>]+>", "").trim();
                            if (!msg.isBlank()) {
                                // "Answer questions?" / "AskFollowup" = Claude CLI 互動問答機制，
                                // plugin 環境無法回應，靜默忽略即可（Claude 會自行繼續）
                                boolean isInteractivePrompt = msg.toLowerCase().contains("answer question")
                                        || msg.toLowerCase().contains("askfollowup")
                                        || msg.equalsIgnoreCase("Answer questions?");
                                // Claude CLI 在 bash 裡執行 ls/cd 等指令時，Windows 路徑反斜線
                                // 會被吃掉（D:\EX12\Foo → D:EX12Foo），導致 "cannot access" 假錯誤，
                                // 靜默忽略即可（Claude 仍會透過其他工具繼續分析）
                                boolean isBashPathError = msg.contains("cannot access")
                                        || (msg.contains("Exit code") && msg.contains("ls:"))
                                        || (msg.contains("Exit code") && msg.contains("No such file or directory"));
                                if (isBashPathError) {
                                    appendMeta("  ℹ bash 路徑轉換中，Claude 將改用其他工具繼續…\n");
                                } else if (!isInteractivePrompt) {
                                    appendError("Tool error: " + msg);
                                }
                            }
                        }
                        startThinking();  // Claude continues after error
                    }
                }
            }

            try { stdin.close(); } catch (IOException ignored) {}
            currentProcess.waitFor();
            errThread.join(500);

            // Post-process response
            final String fullResponse = lastText.toString();
            final int startPos = claudeStart;
            final String respTs = TIME_FMT.format(new Date());

            if (fullResponse.length() > 0) SessionHistory.append(sessionId, "claude", fullResponse, respTs);

            // Extract code blocks
            List<String> blocks = new ArrayList<>();
            Matcher cm = CODE_BLOCK_PAT.matcher(fullResponse);
            while (cm.find()) { String b = cm.group(1).trim(); if (!b.isEmpty()) blocks.add(b); }
            lastCodeBlocks = blocks;

            output.getDisplay().asyncExec(() -> {
                if (output.isDisposed()) return;
                if (startPos >= 0 && !fullResponse.isEmpty()) applyMarkdown(startPos, fullResponse);
                // Update action bar
                boolean hasCode = !blocks.isEmpty();
                copyCodeBtn.setEnabled(hasCode);
                applyFileBtn.setEnabled(hasCode && activeFilePath != null);
                viewDiffBtn.setEnabled(hasCode && activeFilePath != null);
                showActionBar(hasCode);
            });

        } catch (Exception e) {
            if (!(e instanceof InterruptedException)) appendError(e.getMessage());
        } finally {
            currentProcess = null;
            stopThinking();   // always clear spinner on finish
            Display d = output.getDisplay();
            if (!d.isDisposed()) d.asyncExec(() -> {
                if (!output.isDisposed()) output.append("\n\n");
                busy = false;
                setInputEnabled(true);   // restore Send button, disable Stop
                fetchActiveFile();
                // Auto-drain message queue
                String next = messageQueue.poll();
                if (next != null) {
                    appendMeta("  ▶ 自動送出排隊訊息…\n");
                    dispatchMessage(next);
                } else {
                    // Show "Claude 已結束" banner
                    if (statusBannerLbl != null && !statusBannerLbl.isDisposed()) {
                        GridData sbgd = (GridData) statusBannerLbl.getLayoutData();
                        sbgd.exclude = false;
                        statusBannerLbl.setVisible(true);
                        statusBannerLbl.getParent().layout(true, true);
                    }
                }
            });
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Tool use display & permission handling
    // ══════════════════════════════════════════════════════════════════════════

    private static final String[] THINK_FRAMES = {
        "  ⠋ 思考中…", "  ⠙ 思考中…", "  ⠹ 思考中…", "  ⠸ 思考中…",
        "  ⠼ 思考中…", "  ⠴ 思考中…", "  ⠦ 思考中…", "  ⠧ 思考中…",
        "  ⠇ 思考中…", "  ⠏ 思考中…"
    };

    /** Show the thinking spinner Label and start animating (safe to call from any thread). */
    private void startThinking() {
        if (thinkingActive) return;
        thinkingActive = true;
        Display.getDefault().asyncExec(() -> {
            if (thinkingLbl == null || thinkingLbl.isDisposed()) return;
            GridData gd = (GridData) thinkingLbl.getLayoutData();
            gd.exclude = false;
            thinkingLbl.setVisible(true);
            thinkingLbl.getParent().layout(true, true);
            animateThinking(0);
        });
    }

    private void animateThinking(int idx) {
        if (!thinkingActive || thinkingLbl == null || thinkingLbl.isDisposed()) return;
        thinkingLbl.setText(THINK_FRAMES[idx % THINK_FRAMES.length]);
        thinkingLbl.getDisplay().timerExec(100, () -> animateThinking(idx + 1));
    }

    /** Hide the thinking spinner Label (safe to call multiple times or from any thread). */
    private void stopThinking() {
        thinkingActive = false;
        if (thinkingLbl == null || thinkingLbl.isDisposed()) return;
        Display d = thinkingLbl.getDisplay();
        if (d == null || d.isDisposed()) return;
        d.asyncExec(() -> {
            if (thinkingLbl.isDisposed()) return;
            GridData gd = (GridData) thinkingLbl.getLayoutData();
            gd.exclude = true;
            thinkingLbl.setVisible(false);
            thinkingLbl.getParent().layout(true, true);
        });
    }

    private void handleToolUse(String line, OutputStream stdin) {
        Matcher nm = TOOL_NAME_PAT.matcher(line);
        String toolName = nm.find() ? nm.group(1) : "unknown";

        // 過濾 Claude CLI 內部的互動問答 tool — plugin 環境無法回應，靜默忽略
        String toolLower = toolName.toLowerCase();
        if (toolLower.contains("answer question") || toolLower.contains("askfollowup")
                || toolLower.contains("ask_followup") || toolName.equalsIgnoreCase("Answer questions?")) {
            return;
        }

        Matcher pm = PATH_PAT.matcher(line);
        String arg = pm.find() ? unescapeJson(pm.group(1)) : "";
        if (arg.length() > 55) { int i = Math.max(arg.lastIndexOf('\\'), arg.lastIndexOf('/')); arg = i>0 ? "…"+arg.substring(i) : arg; }

        boolean isWrite = toolName.toLowerCase().matches(".*(write|edit|build|delete|move).*");
        String icon = isWrite ? "✏️" : toolName.toLowerCase().contains("read") ? "📖"
                    : toolName.toLowerCase().contains("bash") ? "⚙️" : "🔧";

        // ── 第一個 tool call：印出「思考過程」Cowork 風格區塊標題 ──
        toolCallCount++;
        toolCallNames.add(toolName);
        if (toolCallCount == 1) {
            appendToolSectionHeader();
        }
        appendToolLine("  " + icon + " " + toolName + (arg.isEmpty() ? "" : ": " + arg), isWrite);

        if (!autoPermissions && isWrite) {
            final String fToolName = toolName;
            final String fArg = arg;
            boolean[] allow = {true};
            Display.getDefault().syncExec(() -> {
                allow[0] = MessageDialog.openQuestion(output.getShell(), "Claude 想要執行",
                    "  " + fToolName + (fArg.isEmpty()?"":"\n  檔案："+fArg) + "\n\n允許？");
            });
            try {
                stdin.write((allow[0] ? "y" : "n").getBytes(StandardCharsets.UTF_8));
                stdin.write("\n".getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            } catch (IOException ignored) {}
            if (!allow[0]) appendToolLine("  ⛔ Denied", false);
        }
    }

    /** Print the "▼ 思考過程" header before the first tool call in a response. */
    private void appendToolSectionHeader() {
        Display d = output.getDisplay();
        if (d.isDisposed()) return;
        d.asyncExec(() -> {
            if (output.isDisposed()) return;
            String hdr = "\n  ▼ 思考過程\n";
            int s = output.getCharCount();
            toolSectionStart = s;
            output.append(hdr);
            output.setStyleRange(new StyleRange(s, hdr.length(), colMeta, null));
            scrollToEnd();
        });
    }

    private void handlePermissionPrompt(String prompt, OutputStream stdin) {
        boolean[] allow = {true};
        Display.getDefault().syncExec(() -> {
            allow[0] = MessageDialog.openQuestion(output.getShell(), "Claude Permission", prompt + "\n\n允許？");
        });
        try {
            stdin.write((allow[0] ? "y" : "n").getBytes(StandardCharsets.UTF_8));
            stdin.write("\n".getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (IOException ignored) {}
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Code block actions
    // ══════════════════════════════════════════════════════════════════════════

    private void copyLastCodeBlock() {
        if (lastCodeBlocks.isEmpty()) return;
        String code = lastCodeBlocks.get(lastCodeBlocks.size() - 1);
        Clipboard cb = new Clipboard(output.getDisplay());
        cb.setContents(new Object[]{code}, new Transfer[]{TextTransfer.getInstance()});
        cb.dispose();
        appendMeta("  📋 Copied to clipboard\n");
    }

    private void applyCodeToFile() {
        if (lastCodeBlocks.isEmpty() || activeFilePath == null) return;
        String proposed = lastCodeBlocks.get(lastCodeBlocks.size() - 1);
        showDiffDialog(proposed, true);
    }

    private void viewDiff() {
        if (lastCodeBlocks.isEmpty() || activeFilePath == null) return;
        String proposed = lastCodeBlocks.get(lastCodeBlocks.size() - 1);
        showDiffDialog(proposed, false);
    }

    private void showDiffDialog(String proposed, boolean applyOnOk) {
        String current = "";
        try { current = Files.readString(Paths.get(activeFilePath), StandardCharsets.UTF_8); }
        catch (Exception e) { appendError("Cannot read: " + activeFilePath); return; }

        DiffApplyDialog dlg = new DiffApplyDialog(output.getShell(), activeFilePath, current, proposed);
        int result = dlg.open();
        if (result == org.eclipse.jface.window.Window.OK) {
            appendMeta("  ✅ Applied to: " + shortName(activeFilePath) + "\n");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Markdown rendering
    // ══════════════════════════════════════════════════════════════════════════

    private void applyMarkdown(int startPos, String text) {
        if (output.isDisposed()) return;
        List<StyleRange> ranges = MarkdownRenderer.render(text, startPos, mdColors);
        for (StyleRange sr : ranges) {
            try { output.setStyleRange(sr); } catch (Exception ignored) {}
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Append helpers
    // ══════════════════════════════════════════════════════════════════════════

    private void appendHeader(String label, String ts, Color col) {
        if (output.isDisposed()) return;
        boolean isUser = "You".equals(label);
        // Blank separator line before each message block (breathing room)
        int sepLine = output.getLineCount() - 1;
        output.append("\n");
        // Icon + label + timestamp line
        String icon   = isUser ? "◈ " : "✦ ";
        String header = icon + label + "   " + ts + "\n";
        int headerLine = output.getLineCount() - 1;
        int s = output.getCharCount();
        output.append(header);
        // Icon + label in bold colour
        StyleRange sr = new StyleRange(s, icon.length() + label.length(), col, isUser ? colUserBg : null);
        sr.fontStyle = SWT.BOLD;
        output.setStyleRange(sr);
        // Timestamp in muted colour
        int tsStart = s + icon.length() + label.length();
        output.setStyleRange(new StyleRange(tsStart, header.length() - icon.length() - label.length(),
                                            colTime, isUser ? colUserBg : null));
        // Background tint on header line for user messages
        if (isUser) {
            try {
                output.setLineBackground(sepLine,   1, colUserBg);
                output.setLineBackground(headerLine, 1, colUserBg);
            } catch (Exception ignored) {}
        }
        scrollToEnd();
    }

    private void appendYou(String text) {
        if (output.isDisposed()) return;
        int firstLine = output.getLineCount() - 1;
        output.append(text + "\n");
        int lastLine = output.getLineCount() - 1;
        int s = output.getCharCount() - text.length() - 1;
        output.setStyleRange(new StyleRange(s, text.length()+1, colYou, colUserBg));
        // Apply background tint to all lines of the user message (bubble effect)
        try {
            for (int ln = firstLine; ln <= lastLine; ln++) {
                output.setLineBackground(ln, 1, colUserBg);
            }
        } catch (Exception ignored) {}
        scrollToEnd();
    }

    private void appendMeta(String text) { asyncAppend(text, colMeta); }
    private void appendTime(String text) { asyncAppend(text, colTime); }

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

    private void appendToolLine(String msg, boolean isWrite) {
        Display d = output.getDisplay();
        if (d.isDisposed()) return;
        d.asyncExec(() -> {
            if (output.isDisposed()) return;
            String line = "  " + msg + "\n";
            int s = output.getCharCount();
            output.append(line);
            output.setStyleRange(new StyleRange(s, line.length(), isWrite ? colToolWrite : colToolRead, null));
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
            output.setStyleRange(new StyleRange(s, err.length(), d.getSystemColor(SWT.COLOR_RED), null));
            scrollToEnd();
        });
    }

    private void asyncAppend(String text, Color col) {
        Display d = output.getDisplay();
        if (d.isDisposed()) return;
        d.asyncExec(() -> {
            if (output.isDisposed()) return;
            int s = output.getCharCount();
            output.append(text);
            output.setStyleRange(new StyleRange(s, text.length(), col, null));
            scrollToEnd();
        });
    }

    private void scrollToEnd() {
        output.setCaretOffset(output.getCharCount());
        output.showSelection();
    }

    private void setInputEnabled(boolean en) {
        if (!inputArea.isDisposed())     inputArea.setEnabled(en);
        if (!sendBtn.isDisposed())       sendBtn.setEnabled(en);
        if (!stopBtn.isDisposed())       stopBtn.setEnabled(!en);
        if (!newBtn.isDisposed())        newBtn.setEnabled(en);
        if (headerStopBtn != null && !headerStopBtn.isDisposed()) headerStopBtn.setEnabled(!en);
        if (en && !inputArea.isDisposed()) { inputArea.setFocus(); setPlaceholder(); }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CLI helpers
    // ══════════════════════════════════════════════════════════════════════════

    private static List<String> buildCmd(String claude) {
        List<String> cmd = new ArrayList<>();
        if (System.getProperty("os.name","").toLowerCase().contains("win") && claude.endsWith(".cmd")) {
            cmd.add("cmd.exe"); cmd.add("/c");
        }
        cmd.add(claude);
        return cmd;
    }

    /**
     * Locate the claude.exe native Windows executable.
     * Search order:
     *   1. ~\.local\bin\claude.exe  (Bun/npm global install default)
     *   2. "where claude" on Windows / "which claude" on *nix
     *   3. ClaudeTerminalContext.getClaudeExe() (user-configured value)
     */
    private static String findClaudeExe() {
        // 0. Preference store — user-configured path wins
        try {
            String prefPath = Activator.getPref(Activator.PREF_CLI_PATH);
            if (prefPath != null && !prefPath.trim().isEmpty()) {
                File f = new File(prefPath.trim());
                if (f.isFile()) return f.getAbsolutePath();
            }
        } catch (Exception ignored) {}
        // 1. Known Bun install path
        String home = System.getProperty("user.home");
        if (home != null) {
            File f = new File(home, ".local" + File.separator + "bin" + File.separator + "claude.exe");
            if (f.isFile()) return f.getAbsolutePath();
        }
        // 2. "where" / "which" lookup
        try {
            boolean isWin = System.getProperty("os.name","").toLowerCase().contains("win");
            ProcessBuilder wb = isWin
                ? new ProcessBuilder("where", "claude")
                : new ProcessBuilder("which", "claude");
            wb.redirectErrorStream(true);
            Process wp = wb.start();
            String out = new String(wp.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            wp.waitFor();
            if (!out.isEmpty()) {
                String first = out.split("[\r\n]+")[0].trim();
                if (!first.isEmpty() && new File(first).isFile()) return first;
            }
        } catch (Exception ignored) {}
        // 3. Fall back to user-configured value
        return ClaudeTerminalContext.getClaudeExe();
    }

    private static String stripAnsi(String s) { return ANSI_PATTERN.matcher(s).replaceAll(""); }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i+1 < s.length()) {
                char n = s.charAt(i+1);
                switch (n) {
                    case '"': sb.append('"');  i+=2; continue;
                    case'\\': sb.append('\\'); i+=2; continue;
                    case 'n': sb.append('\n'); i+=2; continue;
                    case 'r': sb.append('\r'); i+=2; continue;
                    case 't': sb.append('\t'); i+=2; continue;
                    case 'u':
                        if (i+5 < s.length()) {
                            try { sb.append((char)Integer.parseInt(s.substring(i+2,i+6),16)); i+=6; continue; }
                            catch (NumberFormatException ignore) {}
                        }
                        break;
                    default: break;
                }
            }
            sb.append(c); i++;
        }
        return sb.toString();
    }

    private static void excl(Control c) { c.setData("org.eclipse.e4.ui.css.swt.theme.exclude", Boolean.TRUE); }

    // ══════════════════════════════════════════════════════════════════════════
    // Resources
    // ══════════════════════════════════════════════════════════════════════════

    private void initResources(Display d) {
        Color sysBg = d.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
        boolean dark = (sysBg.getRed() + sysBg.getGreen() + sysBg.getBlue()) < 380;
        if (dark) {
            colBg         = new Color(d,  22,  22,  22);
            colInputBg    = new Color(d,  36,  36,  40);
            colHeaderBg   = new Color(d,  30,  30,  34);
            colContextBg  = new Color(d,  26,  26,  30);
            colActionBg   = new Color(d,  28,  28,  36);
            colText       = new Color(d, 240, 240, 240);
            colYou        = new Color(d,  86, 156, 214);
            colClaude     = new Color(d, 167, 139, 250);  // Claude purple — readable on dark
            colMeta       = new Color(d, 106, 153,  85);
            colTime       = new Color(d, 110, 110, 110);
            colCode       = new Color(d, 206, 145, 120);
            colCodeBg     = new Color(d,  44,  44,  44);
            colHeading    = new Color(d,  86, 156, 214);
            colItalic     = new Color(d, 190, 190, 190);
            colToolRead   = new Color(d,  90, 160, 110);
            colToolWrite  = new Color(d, 220, 160,  60);
            colToolOther  = new Color(d, 150, 150, 200);
            colUserBg     = new Color(d,  30,  38,  52);  // dark blue tint for user message
        } else {
            colBg         = new Color(d, 250, 250, 250);
            colInputBg    = new Color(d, 238, 238, 244);
            colHeaderBg   = new Color(d, 242, 242, 248);
            colContextBg  = new Color(d, 246, 246, 250);
            colActionBg   = new Color(d, 240, 240, 248);
            colText       = new Color(d,  28,  28,  28);
            colYou        = new Color(d,   0,  80, 180);
            colClaude     = new Color(d,  90,  50, 251);  // Claude brand purple #5A32FB
            colMeta       = new Color(d,   0, 110,  50);
            colTime       = new Color(d, 130, 130, 130);
            colCode       = new Color(d, 160,  60,  20);
            colCodeBg     = new Color(d, 238, 238, 238);
            colHeading    = new Color(d,   0,  80, 180);
            colItalic     = new Color(d,  80,  80,  80);
            colToolRead   = new Color(d,   0, 120,  60);
            colToolWrite  = new Color(d, 180,  80,   0);
            colToolOther  = new Color(d,  80,  80, 180);
            colUserBg     = new Color(d, 235, 242, 255);  // light blue tint for user message
        }
        monoFont = new Font(d, "Consolas",  12, SWT.NORMAL);  // code / input
        chatFont = new Font(d, "Segoe UI",  13, SWT.NORMAL);  // chat output (proportional)
        boldFont = new Font(d, "Segoe UI",  13, SWT.BOLD);    // headers / buttons
        mdColors = new MarkdownRenderer.Colors(colText, colHeading, colText, colItalic,
                                               colCode, colCodeBg, colMeta, monoFont);
    }

    @Override
    public void setFocus() { if (inputArea != null && !inputArea.isDisposed()) inputArea.setFocus(); }

    @Override
    public void dispose() {
        // Unregister preference change listener
        if (prefListener != null) {
            try { Activator.getDefault().getPreferenceStore().removePropertyChangeListener(prefListener); }
            catch (Exception ignored) {}
            prefListener = null;
        }
        // Unregister editor-tab listener
        if (partListener != null) {
            try {
                IWorkbenchWindow win = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                if (win != null && win.getActivePage() != null)
                    win.getActivePage().removePartListener(partListener);
            } catch (Exception ignored) {}
            partListener = null;
        }
        if (completionPopup != null && !completionPopup.isDisposed()) completionPopup.dispose();
        Color[] cs = { colBg, colInputBg, colHeaderBg, colContextBg, colActionBg,
                       colText, colYou, colClaude, colMeta, colTime, colCode, colCodeBg,
                       colHeading, colItalic, colToolRead, colToolWrite, colToolOther, colUserBg };
        for (Color c : cs) if (c != null && !c.isDisposed()) c.dispose();
        if (monoFont != null && !monoFont.isDisposed()) monoFont.dispose();
        if (chatFont != null && !chatFont.isDisposed()) chatFont.dispose();
        if (boldFont != null && !boldFont.isDisposed()) boldFont.dispose();
        super.dispose();
    }
}
