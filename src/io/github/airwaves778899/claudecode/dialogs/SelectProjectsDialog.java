package io.github.airwaves778899.claudecode.dialogs;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;

/**
 * Project selection dialog with:
 *  - Two groups: Git Repositories / Other Projects
 *  - Working directory: current project or workspace root
 *  - Open mode: Eclipse View (embedded) / Floating Window / Windows Terminal
 */
public class SelectProjectsDialog extends Dialog {

    // ── Open mode constants ────────────────────────────────────────────────────
    public static final int MODE_ECLIPSE_VIEW = 0;
    public static final int MODE_FLOATING     = 1;
    public static final int MODE_TERMINAL     = 2;

    // ── Input ──────────────────────────────────────────────────────────────────
    private final String currentProjectPath;
    private final String currentProjectName;
    private final String workspacePath;
    private final String claudeExe;

    // ── Widgets ────────────────────────────────────────────────────────────────
    private Button  radioCurrentProject;
    private Button  radioWorkspaceRoot;
    private Table   gitTable;
    private Table   otherTable;
    private Text    previewText;
    private Button  modeEclipseView;
    private Button  modeFloating;
    private Button  modeTerminal;
    private Font    boldFont;

    // ── Result ─────────────────────────────────────────────────────────────────
    private String       resultWorkDir;
    private List<String> resultAddDirs = new ArrayList<>();
    private int          resultMode    = MODE_TERMINAL;

    public SelectProjectsDialog(Shell parentShell,
                                String currentProjectPath,
                                String currentProjectName,
                                String workspacePath,
                                String claudeExe) {
        super(parentShell);
        this.currentProjectPath = currentProjectPath;
        this.currentProjectName = currentProjectName != null ? currentProjectName : "";
        this.workspacePath      = workspacePath;
        this.claudeExe          = claudeExe;
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Open Claude Terminal");
    }

    @Override
    protected Point getInitialSize() { return new Point(700, 600); }

    // ── Dialog area ────────────────────────────────────────────────────────────

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        area.setLayout(new GridLayout(1, false));

        boldFont = new Font(parent.getDisplay(),
                parent.getFont().getFontData()[0].getName(), 9, SWT.BOLD);
        parent.addDisposeListener(e -> boldFont.dispose());

        createWorkDirSection(area);
        createProjectTables(area);
        createModeSection(area);
        createPreviewSection(area);

        updatePreview();
        return area;
    }

    // ── Working directory ──────────────────────────────────────────────────────

    private void createWorkDirSection(Composite parent) {
        Group g = new Group(parent, SWT.NONE);
        g.setText("Working directory");
        g.setLayout(new GridLayout(1, false));
        g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        radioCurrentProject = new Button(g, SWT.RADIO);
        radioCurrentProject.setText(
            currentProjectName.isEmpty()
                ? "Current project  (none detected)"
                : "Current project:  " + currentProjectName +
                  "  [" + shortenPath(currentProjectPath) + "]");
        radioCurrentProject.setSelection(currentProjectPath != null);
        radioCurrentProject.setEnabled(currentProjectPath != null);
        radioCurrentProject.addSelectionListener(updateListener());

        radioWorkspaceRoot = new Button(g, SWT.RADIO);
        radioWorkspaceRoot.setText(
            "Workspace root:  " + workspacePath + "  (all projects visible)");
        radioWorkspaceRoot.setSelection(currentProjectPath == null);
        radioWorkspaceRoot.addSelectionListener(updateListener());
    }

    // ── Project tables (two groups) ────────────────────────────────────────────

    private void createProjectTables(Composite parent) {
        // Collect projects split by git / non-git
        List<IProject> gitProjects   = new ArrayList<>();
        List<IProject> otherProjects = new ArrayList<>();
        splitProjects(gitProjects, otherProjects);

        Composite tablesRow = new Composite(parent, SWT.NONE);
        tablesRow.setLayout(new GridLayout(2, true));
        tablesRow.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        gitTable   = buildProjectTable(tablesRow,
                "Git Repositories  (" + gitProjects.size() + ")",
                gitProjects);
        otherTable = buildProjectTable(tablesRow,
                "Other Projects  (" + otherProjects.size() + ")",
                otherProjects);
    }

    private Table buildProjectTable(Composite parent, String title,
                                    List<IProject> projects) {
        Group g = new Group(parent, SWT.NONE);
        g.setText(title);
        g.setLayout(new GridLayout(1, false));
        g.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Table table = new Table(g, SWT.CHECK | SWT.BORDER | SWT.V_SCROLL
                                 | SWT.FULL_SELECTION);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        GridData td = new GridData(SWT.FILL, SWT.FILL, true, true);
        td.heightHint = 160;
        table.setLayoutData(td);

        TableColumn cName = new TableColumn(table, SWT.NONE);
        cName.setText("Project");
        cName.setWidth(160);

        TableColumn cPath = new TableColumn(table, SWT.NONE);
        cPath.setText("Path");
        cPath.setWidth(230);

        for (IProject p : projects) {
            String path = p.getLocation() != null ? p.getLocation().toOSString() : "";
            if (path.isEmpty()) continue;

            TableItem item = new TableItem(table, SWT.NONE);
            boolean isCurrent = path.equalsIgnoreCase(currentProjectPath);
            item.setText(0, isCurrent ? p.getName() + " ★" : p.getName());
            item.setText(1, path);
            item.setData(path);
            // Pre-check everything except the "main" current project
            item.setChecked(!isCurrent);
            if (isCurrent) item.setGrayed(true);
        }

        table.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                if (e.detail == SWT.CHECK) updatePreview();
            }
        });

        // Buttons
        Composite btns = new Composite(g, SWT.NONE);
        btns.setLayout(new RowLayout());
        btns.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        Button all  = new Button(btns, SWT.PUSH); all.setText("All");
        Button none = new Button(btns, SWT.PUSH); none.setText("None");
        all.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                for (TableItem i : table.getItems()) i.setChecked(true);
                updatePreview();
            }
        });
        none.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                for (TableItem i : table.getItems()) i.setChecked(false);
                updatePreview();
            }
        });

        return table;
    }

    // ── Open mode ──────────────────────────────────────────────────────────────

    private void createModeSection(Composite parent) {
        Group g = new Group(parent, SWT.NONE);
        g.setText("Open mode");
        g.setLayout(new GridLayout(3, false));
        g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        modeEclipseView = new Button(g, SWT.RADIO);
        modeEclipseView.setText("Embed in Eclipse  (View panel)");
        modeEclipseView.setToolTipText("Opens a terminal panel docked inside Eclipse");
        modeEclipseView.addSelectionListener(updateListener());

        modeFloating = new Button(g, SWT.RADIO);
        modeFloating.setText("Floating Window  (Eclipse-controlled)");
        modeFloating.setToolTipText(
            "Opens a standalone window linked to Eclipse — stays in the same taskbar group");
        modeFloating.addSelectionListener(updateListener());

        modeTerminal = new Button(g, SWT.RADIO);
        modeTerminal.setText("Windows Terminal  (full Claude TUI)");
        modeTerminal.setSelection(true);
        modeTerminal.setToolTipText(
            "Opens the full interactive Claude Code experience in Windows Terminal");
        modeTerminal.addSelectionListener(updateListener());
    }

    // ── Preview ────────────────────────────────────────────────────────────────

    private void createPreviewSection(Composite parent) {
        Group g = new Group(parent, SWT.NONE);
        g.setText("Command preview");
        g.setLayout(new GridLayout(1, false));
        g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        previewText = new Text(g,
                SWT.READ_ONLY | SWT.MULTI | SWT.WRAP | SWT.BORDER);
        GridData pgd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        pgd.heightHint = 48;
        previewText.setLayoutData(pgd);
    }

    private void updatePreview() {
        if (previewText == null || previewText.isDisposed()) return;
        String wd    = getSelectedWorkDir();
        List<String> ad = getCheckedPaths();

        String mode = modeEclipseView != null && modeEclipseView.getSelection()
                ? "[Eclipse View]"
                : (modeFloating != null && modeFloating.getSelection()
                   ? "[Floating Window]" : "[Windows Terminal]");

        StringBuilder sb = new StringBuilder(mode).append("  ");
        sb.append("claude");
        for (String d : ad) sb.append(" --add-dir \"").append(d).append("\"");
        sb.append("\n").append("Working dir: ").append(wd);
        previewText.setText(sb.toString());
    }

    // ── OK / result ────────────────────────────────────────────────────────────

    @Override
    protected void okPressed() {
        resultWorkDir = getSelectedWorkDir();
        resultAddDirs = getCheckedPaths();
        if (modeEclipseView != null && modeEclipseView.getSelection())
            resultMode = MODE_ECLIPSE_VIEW;
        else if (modeFloating != null && modeFloating.getSelection())
            resultMode = MODE_FLOATING;
        else
            resultMode = MODE_TERMINAL;
        super.okPressed();
    }

    public String       getWorkDir()  { return resultWorkDir; }
    public List<String> getAddDirs()  { return resultAddDirs; }
    public int          getMode()     { return resultMode; }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String getSelectedWorkDir() {
        boolean useRoot = radioWorkspaceRoot != null && radioWorkspaceRoot.getSelection();
        return useRoot
                ? workspacePath
                : (currentProjectPath != null ? currentProjectPath : workspacePath);
    }

    private List<String> getCheckedPaths() {
        List<String> result = new ArrayList<>();
        addChecked(gitTable,   result);
        addChecked(otherTable, result);
        return result;
    }

    private static void addChecked(Table table, List<String> out) {
        if (table == null || table.isDisposed()) return;
        for (TableItem item : table.getItems()) {
            if (item.getChecked() && !item.getGrayed())
                out.add((String) item.getData());
        }
    }

    private void splitProjects(List<IProject> git, List<IProject> other) {
        IProject[] all = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        Arrays.sort(all, Comparator.comparing(IProject::getName,
                String.CASE_INSENSITIVE_ORDER));
        for (IProject p : all) {
            if (!p.isOpen()) continue;
            if (p.getLocation() == null) continue;
            File gitDir = new File(p.getLocation().toOSString(), ".git");
            if (gitDir.exists()) git.add(p);
            else                 other.add(p);
        }
    }

    private SelectionAdapter updateListener() {
        return new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) { updatePreview(); }
        };
    }

    private static String shortenPath(String path) {
        if (path == null) return "";
        return path.length() <= 45 ? path : "..." + path.substring(path.length() - 42);
    }
}
