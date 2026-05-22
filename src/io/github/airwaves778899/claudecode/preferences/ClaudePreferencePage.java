package io.github.airwaves778899.claudecode.preferences;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import io.github.airwaves778899.claudecode.Activator;
import io.github.airwaves778899.claudecode.api.ClaudeCliClient;

/**
 * Window → Preferences → Claude Code
 */
public class ClaudePreferencePage extends PreferencePage
        implements IWorkbenchPreferencePage {

    private Text    cliPathText;
    private Text    workDirText;
    private Combo   modelCombo;
    private Button  autoSwitchCheck;
    private Button  includeFileCheck;
    private Button  autoPermCheck;

    // Keep combo values in sync with the display strings
    private static final String[] MODEL_IDS = {
        "claude-sonnet-4-5",
        "claude-opus-4-5",
        "claude-haiku-4-5-20251001",
        "claude-sonnet-4-6",
        "claude-opus-4-6",
        "claude-opus-4-7",
    };
    private static final String[] MODEL_LABELS = {
        "claude-sonnet-4-5（建議）",
        "claude-opus-4-5",
        "claude-haiku-4-5",
        "claude-sonnet-4-6",
        "claude-opus-4-6",
        "claude-opus-4-7（最新）",
    };

    public ClaudePreferencePage() {
        super();
        setPreferenceStore(Activator.getDefault().getPreferenceStore());
        setDescription(
            "使用 Claude Code CLI 作為後端，無需 API Key。\n" +
            "請先在終端執行 claude 完成登入，再於此確認 CLI 路徑。"
        );
    }

    @Override
    protected Control createContents(Composite parent) {
        Composite root = new Composite(parent, SWT.NONE);
        GridLayout gl = new GridLayout(1, false);
        gl.marginWidth = 0; gl.marginHeight = 0; gl.verticalSpacing = 8;
        root.setLayout(gl);
        root.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // ── CLI Path ──────────────────────────────────────────────────────
        section(root, "Claude CLI 路徑");

        Composite cliRow = row(root, 3);
        label(cliRow, "路徑：");
        cliPathText = new Text(cliRow, SWT.SINGLE | SWT.BORDER);
        cliPathText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        cliPathText.setToolTipText(
            "Claude Code CLI 執行檔路徑。\n留空 = 自動偵測。\n" +
            "Windows 範例：C:\\Users\\you\\.local\\bin\\claude.exe");
        cliPathText.setText(getPreferenceStore().getString(Activator.PREF_CLI_PATH));

        // Buttons row
        Composite btnRow = new Composite(root, SWT.NONE);
        RowLayout rl = new RowLayout(SWT.HORIZONTAL);
        rl.spacing = 8; rl.marginHeight = 0; rl.marginWidth = 0; rl.wrap = false;
        btnRow.setLayout(rl);

        Button autoDetectBtn = new Button(btnRow, SWT.PUSH);
        autoDetectBtn.setText("自動偵測");
        autoDetectBtn.setToolTipText("嘗試在常見位置找到 claude 執行檔");
        autoDetectBtn.addListener(SWT.Selection, e -> {
            String found = detectClaude();
            if (found != null) {
                cliPathText.setText(found);
                setMessage("✔ 找到：" + found, INFORMATION);
            } else {
                setMessage("✘ 找不到 claude 執行檔，請手動填入路徑", ERROR);
            }
        });

        Button testBtn = new Button(btnRow, SWT.PUSH);
        testBtn.setText("測試連線");
        testBtn.setToolTipText("執行 claude --version 確認是否可用");
        testBtn.addListener(SWT.Selection, e -> {
            String path = cliPathText.getText().trim();
            if (path.isEmpty()) path = detectClaude();
            if (path == null) path = "claude";
            String version = ClaudeCliClient.getVersion(path);
            boolean ok = !version.equals("未找到") && !version.startsWith("錯誤");
            setMessage(
                ok ? "✔ 連線成功　版本：" + version : "✘ 無法執行：" + version,
                ok ? INFORMATION : ERROR);
        });

        note(root, "尚未安裝？請執行：npm install -g @anthropic-ai/claude-code  然後執行 claude 登入");

        separator(root);

        // ── Default working directory ─────────────────────────────────────
        section(root, "預設工作目錄");

        Composite wdRow = row(root, 3);
        label(wdRow, "目錄：");
        workDirText = new Text(wdRow, SWT.SINGLE | SWT.BORDER);
        workDirText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        workDirText.setToolTipText("留空 = 自動跟隨目前開啟的專案。");
        workDirText.setText(getPreferenceStore().getString(Activator.PREF_WORK_DIR));

        Button browseBtn = new Button(wdRow, SWT.PUSH);
        browseBtn.setText("瀏覽…");
        browseBtn.addListener(SWT.Selection, e -> {
            DirectoryDialog dlg = new DirectoryDialog(getShell(), SWT.OPEN);
            dlg.setText("選擇預設工作目錄");
            dlg.setFilterPath(workDirText.getText());
            String chosen = dlg.open();
            if (chosen != null) workDirText.setText(chosen);
        });

        note(root, "留空時依序嘗試：目前 Editor 的專案 → 第一個開啟的專案 → workspace 根");

        separator(root);

        // ── Model ─────────────────────────────────────────────────────────
        section(root, "Claude 模型");

        Composite modelRow = row(root, 2);
        label(modelRow, "預設模型：");
        modelCombo = new Combo(modelRow, SWT.READ_ONLY | SWT.DROP_DOWN);
        modelCombo.setItems(MODEL_LABELS);
        modelCombo.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        // Select current model
        String curModel = getPreferenceStore().getString(Activator.PREF_MODEL);
        int sel = 0;
        for (int i = 0; i < MODEL_IDS.length; i++) {
            if (MODEL_IDS[i].equals(curModel)) { sel = i; break; }
        }
        modelCombo.select(sel);

        separator(root);

        // ── Behaviour toggles ─────────────────────────────────────────────
        section(root, "行為設定");

        autoSwitchCheck = new Button(root, SWT.CHECK);
        autoSwitchCheck.setText("切換 Tab 時自動切換工作目錄到對應專案");
        autoSwitchCheck.setSelection(
            getPreferenceStore().getBoolean(Activator.PREF_AUTO_SWITCH_WORKDIR));

        includeFileCheck = new Button(root, SWT.CHECK);
        includeFileCheck.setText("自動將目前開啟的檔案路徑加入 context");
        includeFileCheck.setSelection(
            getPreferenceStore().getBoolean(Activator.PREF_INCLUDE_ACTIVE_FILE));

        autoPermCheck = new Button(root, SWT.CHECK);
        autoPermCheck.setText("自動允許所有檔案操作（不詢問確認）");
        autoPermCheck.setSelection(
            getPreferenceStore().getBoolean(Activator.PREF_AUTO_PERMISSIONS));

        note(root, "⚠ 自動允許會讓 Claude 直接讀寫檔案，建議僅在測試環境啟用。");

        return root;
    }

    // ── Save ──────────────────────────────────────────────────────────────

    @Override
    public boolean performOk() {
        getPreferenceStore().setValue(Activator.PREF_CLI_PATH,
            cliPathText.getText().trim());
        getPreferenceStore().setValue(Activator.PREF_WORK_DIR,
            workDirText.getText().trim());
        getPreferenceStore().setValue(Activator.PREF_MODEL,
            MODEL_IDS[Math.max(0, modelCombo.getSelectionIndex())]);
        getPreferenceStore().setValue(Activator.PREF_AUTO_SWITCH_WORKDIR,
            autoSwitchCheck.getSelection());
        getPreferenceStore().setValue(Activator.PREF_INCLUDE_ACTIVE_FILE,
            includeFileCheck.getSelection());
        getPreferenceStore().setValue(Activator.PREF_AUTO_PERMISSIONS,
            autoPermCheck.getSelection());
        return true;
    }

    @Override
    protected void performDefaults() {
        cliPathText.setText("");
        workDirText.setText("");
        modelCombo.select(0);
        autoSwitchCheck.setSelection(true);
        includeFileCheck.setSelection(true);
        autoPermCheck.setSelection(false);
        super.performDefaults();
    }

    // ── Layout helpers ────────────────────────────────────────────────────

    private static void section(Composite parent, String title) {
        Label lbl = new Label(parent, SWT.NONE);
        lbl.setText(title);
        lbl.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        GridData gd = (GridData) lbl.getLayoutData();
        gd.verticalIndent = 4;
    }

    private static Composite row(Composite parent, int cols) {
        Composite c = new Composite(parent, SWT.NONE);
        GridLayout gl = new GridLayout(cols, false);
        gl.marginWidth = 0; gl.marginHeight = 0; gl.horizontalSpacing = 6;
        c.setLayout(gl);
        c.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return c;
    }

    private static void label(Composite parent, String text) {
        Label l = new Label(parent, SWT.NONE);
        l.setText(text);
        l.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
    }

    private static void note(Composite parent, String text) {
        Label l = new Label(parent, SWT.WRAP);
        l.setText(text);
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.widthHint = 400;
        l.setLayoutData(gd);
    }

    private static void separator(Composite parent) {
        Label sep = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL);
        sep.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    // ── CLI auto-detection ────────────────────────────────────────────────

    private static String detectClaude() {
        String home = System.getProperty("user.home", "");
        boolean isWin = System.getProperty("os.name", "").toLowerCase().contains("win");
        String[] candidates = isWin
            ? new String[]{
                home + "\\.local\\bin\\claude.exe",
                System.getenv("LOCALAPPDATA") + "\\Programs\\claude\\claude.exe",
                "C:\\Program Files\\claude\\claude.exe",
              }
            : new String[]{
                home + "/.local/bin/claude",
                "/usr/local/bin/claude",
                "/opt/homebrew/bin/claude",
                "/usr/bin/claude",
              };
        for (String c : candidates) {
            if (c != null && new java.io.File(c).isFile()) return c;
        }
        try {
            String[] cmd = isWin ? new String[]{"where","claude"} : new String[]{"which","claude"};
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            if (!out.isEmpty()) {
                String first = out.split("[\r\n]+")[0].trim();
                if (new java.io.File(first).isFile()) return first;
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public void init(IWorkbench workbench) {}
}
