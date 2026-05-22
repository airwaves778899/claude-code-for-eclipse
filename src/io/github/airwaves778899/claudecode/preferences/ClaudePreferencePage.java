package io.github.airwaves778899.claudecode.preferences;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import io.github.airwaves778899.claudecode.Activator;
import io.github.airwaves778899.claudecode.api.ClaudeCliClient;
import io.github.airwaves778899.claudecode.terminal.UserSkillsLoader;

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
        "claude-sonnet-4-5 (recommended)",
        "claude-opus-4-5",
        "claude-haiku-4-5",
        "claude-sonnet-4-6",
        "claude-opus-4-6",
        "claude-opus-4-7 (latest)",
    };

    public ClaudePreferencePage() {
        super();
        setPreferenceStore(Activator.getDefault().getPreferenceStore());
        setDescription(
            "Uses Claude Code CLI as backend. No API key required.\n" +
            "First run claude in a terminal to log in, then confirm the CLI path here."
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
        section(root, "Claude CLI Path");

        Composite cliRow = row(root, 3);
        label(cliRow, "Path:");
        cliPathText = new Text(cliRow, SWT.SINGLE | SWT.BORDER);
        cliPathText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        cliPathText.setToolTipText(
            "Path to the Claude Code CLI executable.\n" +
            "Leave empty for auto-detection.\n" +
            "Windows example: C:\\Users\\you\\.local\\bin\\claude.exe");
        cliPathText.setText(getPreferenceStore().getString(Activator.PREF_CLI_PATH));

        // Buttons row
        Composite btnRow = new Composite(root, SWT.NONE);
        RowLayout rl = new RowLayout(SWT.HORIZONTAL);
        rl.spacing = 8; rl.marginHeight = 0; rl.marginWidth = 0; rl.wrap = false;
        btnRow.setLayout(rl);

        Button autoDetectBtn = new Button(btnRow, SWT.PUSH);
        autoDetectBtn.setText("Auto-detect");
        autoDetectBtn.setToolTipText("Try to find the claude executable in common locations");
        autoDetectBtn.addListener(SWT.Selection, e -> {
            String found = detectClaude();
            if (found != null) {
                cliPathText.setText(found);
                setMessage("✔ Found: " + found, INFORMATION);
            } else {
                setMessage("✘ claude executable not found, please enter the path manually", ERROR);
            }
        });

        Button testBtn = new Button(btnRow, SWT.PUSH);
        testBtn.setText("Test Connection");
        testBtn.setToolTipText("Run claude --version to verify availability");
        testBtn.addListener(SWT.Selection, e -> {
            String path = cliPathText.getText().trim();
            if (path.isEmpty()) path = detectClaude();
            if (path == null) path = "claude";
            String version = ClaudeCliClient.getVersion(path);
            boolean ok = !version.equals("Not found") && !version.startsWith("Error");
            setMessage(
                ok ? "✔ Connected  Version: " + version : "✘ Cannot run: " + version,
                ok ? INFORMATION : ERROR);
        });

        note(root, "Not installed? Run: npm install -g @anthropic-ai/claude-code  then run claude to log in");

        separator(root);

        // ── Default working directory ─────────────────────────────────────
        section(root, "Default Working Directory");

        Composite wdRow = row(root, 3);
        label(wdRow, "Directory:");
        workDirText = new Text(wdRow, SWT.SINGLE | SWT.BORDER);
        workDirText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        workDirText.setToolTipText("Leave empty to follow the currently open project.");
        workDirText.setText(getPreferenceStore().getString(Activator.PREF_WORK_DIR));

        Button browseBtn = new Button(wdRow, SWT.PUSH);
        browseBtn.setText("Browse…");
        browseBtn.addListener(SWT.Selection, e -> {
            DirectoryDialog dlg = new DirectoryDialog(getShell(), SWT.OPEN);
            dlg.setText("Select Default Working Directory");
            dlg.setFilterPath(workDirText.getText());
            String chosen = dlg.open();
            if (chosen != null) workDirText.setText(chosen);
        });

        note(root, "If empty, tries in order: current editor's project → first open project → workspace root");

        separator(root);

        // ── Model ─────────────────────────────────────────────────────────
        section(root, "Claude Model");

        Composite modelRow = row(root, 2);
        label(modelRow, "Default Model:");
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
        section(root, "Behavior");

        autoSwitchCheck = new Button(root, SWT.CHECK);
        autoSwitchCheck.setText("Auto-switch working directory when changing editor tabs");
        autoSwitchCheck.setSelection(
            getPreferenceStore().getBoolean(Activator.PREF_AUTO_SWITCH_WORKDIR));

        includeFileCheck = new Button(root, SWT.CHECK);
        includeFileCheck.setText("Automatically include the current file path in context");
        includeFileCheck.setSelection(
            getPreferenceStore().getBoolean(Activator.PREF_INCLUDE_ACTIVE_FILE));

        autoPermCheck = new Button(root, SWT.CHECK);
        autoPermCheck.setText("Auto-allow all file operations (skip confirmation)");
        autoPermCheck.setSelection(
            getPreferenceStore().getBoolean(Activator.PREF_AUTO_PERMISSIONS));

        note(root, "⚠ Auto-allow lets Claude read/write files directly. Enable only in test environments.");

        separator(root);

        // ── Custom Slash Commands ─────────────────────────────────────────
        section(root, "Custom Slash Commands (My Skills)");

        String skillsPath = UserSkillsLoader.getDefaultPath();

        // Read-only path display
        Text skillsPathText = new Text(root, SWT.SINGLE | SWT.READ_ONLY | SWT.BORDER);
        skillsPathText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        skillsPathText.setText(skillsPath);
        skillsPathText.setToolTipText("Path to the custom slash-commands definition file");

        // Buttons: Open / Create Sample
        Composite skillsBtnRow = new Composite(root, SWT.NONE);
        RowLayout sbrl = new RowLayout(SWT.HORIZONTAL);
        sbrl.spacing = 8; sbrl.marginHeight = 0; sbrl.marginWidth = 0;
        skillsBtnRow.setLayout(sbrl);

        Button openSkillsBtn = new Button(skillsBtnRow, SWT.PUSH);
        openSkillsBtn.setText("Open File");
        openSkillsBtn.setToolTipText("Open the skills JSON file in the default editor");
        openSkillsBtn.addListener(SWT.Selection, e -> {
            java.io.File f = new java.io.File(skillsPath);
            if (!f.exists()) UserSkillsLoader.createSampleIfAbsent();
            boolean launched = org.eclipse.swt.program.Program.launch(
                    new java.io.File(skillsPath).getAbsolutePath());
            if (!launched) setMessage("Cannot open: " + skillsPath, ERROR);
        });

        Button createSampleBtn = new Button(skillsBtnRow, SWT.PUSH);
        createSampleBtn.setText("Create Sample");
        createSampleBtn.setToolTipText("Create a sample file with 3 example slash commands");
        createSampleBtn.addListener(SWT.Selection, e -> {
            java.io.File f = new java.io.File(skillsPath);
            if (f.exists()) {
                setMessage("File already exists: " + skillsPath, INFORMATION);
            } else {
                UserSkillsLoader.createSampleIfAbsent();
                setMessage("✔ Created: " + skillsPath, INFORMATION);
            }
        });

        note(root,
            "Define custom /commands for the slash popup. JSON format:\n" +
            "[ { \"command\": \"/myslash\",  \"description\": \"What it does\",\n" +
            "    \"prompt\": \"Analyse {file} and ...\" } ]\n" +
            "Use {file} as placeholder for the open file path. Changes take effect immediately — no restart needed.");

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
