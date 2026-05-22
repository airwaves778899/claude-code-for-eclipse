package io.github.airwaves778899.claudecode.apply;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/**
 * Claude Code for Eclipse - Apply Code Dialog (Phase 4)
 *
 * Shows a side-by-side diff preview:
 *   Left  — current code (from editor selection, read-only)
 *   Right — Claude's suggested code (editable before applying)
 *
 * Diff coloring (line-level):
 *   Green background  = lines added/changed by Claude
 *   Red background    = lines removed (only in current)
 *   White background  = unchanged lines
 *
 * Apply targets:
 *   • Replace selection  — replaces the selected text in the active editor
 *   • Replace whole file — replaces the entire document
 *   • Copy to clipboard  — just copy, no editor change
 */
public class ApplyCodeDialog extends Dialog {

    // ── Button IDs ────────────────────────────────────────────────────────────
    public static final int APPLY_SELECTION = 100;
    public static final int APPLY_FILE      = 101;
    public static final int COPY_CLIPBOARD  = 102;

    // ── Input ─────────────────────────────────────────────────────────────────
    private final CodeBlock codeBlock;
    private final String    currentCode;     // current selection / file content (may be empty)
    private final String    fileName;
    private final boolean   hasSelection;

    // ── Widgets ───────────────────────────────────────────────────────────────
    private StyledText currentPane;
    private StyledText suggestedPane;

    // ── Diff colours ──────────────────────────────────────────────────────────
    private Color colorAdded;
    private Color colorRemoved;
    private Color colorUnchanged;
    private Color colorPaneBg;
    private Color colorHeaderBg;
    private Color colorTextFg;
    private Font  monoFont;

    // ── Result ────────────────────────────────────────────────────────────────
    /** The (possibly edited) code to apply — set when user clicks Apply. */
    private String resultCode;

    // ─────────────────────────────────────────────────────────────────────────

    public ApplyCodeDialog(Shell parent, CodeBlock codeBlock,
                           String currentCode, String fileName, boolean hasSelection) {
        super(parent);
        this.codeBlock    = codeBlock;
        this.currentCode  = currentCode != null ? currentCode : "";
        this.fileName     = fileName != null ? fileName : "editor";
        this.hasSelection = hasSelection;
        setShellStyle(getShellStyle() | SWT.RESIZE | SWT.MAX);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Apply Code — " + codeBlock.getLabel() + "  →  " + fileName);
        shell.setSize(1100, 680);
        // Centre on screen
        org.eclipse.swt.graphics.Rectangle bounds = shell.getDisplay().getPrimaryMonitor().getBounds();
        shell.setLocation(
            bounds.x + (bounds.width  - 1100) / 2,
            bounds.y + (bounds.height - 680)  / 2);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Display display = parent.getDisplay();
        initResources(display);

        Composite area = (Composite) super.createDialogArea(parent);
        area.setBackground(colorPaneBg);
        GridLayout gl = new GridLayout(1, false);
        gl.marginWidth = 10; gl.marginHeight = 10;
        area.setLayout(gl);

        buildInfoBar(area);
        buildDiffPanels(area);
        buildLegend(area);

        return area;
    }

    // ── Info bar ──────────────────────────────────────────────────────────────

    private void buildInfoBar(Composite parent) {
        Composite bar = new Composite(parent, SWT.NONE);
        bar.setBackground(colorHeaderBg);
        GridLayout gl = new GridLayout(2, false);
        gl.marginWidth = 8; gl.marginHeight = 6;
        bar.setLayout(gl);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Label info = new Label(bar, SWT.NONE);
        info.setBackground(colorHeaderBg);
        info.setForeground(colorTextFg);
        info.setText("Language: " + codeBlock.getLanguage() +
                     "   ·   " + codeBlock.getContent().split("\n").length + " lines" +
                     "   ·   Code on the right can be edited before applying");
        info.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label hint = new Label(bar, SWT.NONE);
        hint.setBackground(colorHeaderBg);
        hint.setForeground(new Color(parent.getDisplay(), 130, 130, 130));
        hint.setText("Ctrl+Z to undo after applying");
        hint.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
    }

    // ── Diff panels ───────────────────────────────────────────────────────────

    private void buildDiffPanels(Composite parent) {
        Composite panels = new Composite(parent, SWT.NONE);
        panels.setBackground(colorPaneBg);
        panels.setLayout(new GridLayout(2, true));
        panels.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // Left panel header
        buildPanelHeader(panels, "Current code  (" + fileName + ")");
        buildPanelHeader(panels, "Claude's suggested code  (editable before applying)");

        // Left panel — current code, read-only
        currentPane = buildCodePane(panels, true);
        currentPane.setText(currentCode.isEmpty() ? "(no code selected)" : currentCode);

        // Right panel — suggested code, editable
        suggestedPane = buildCodePane(panels, false);
        suggestedPane.setText(codeBlock.getContent());

        // Apply diff coloring after both panels are populated
        applyDiffColors();
    }

    private void buildPanelHeader(Composite parent, String title) {
        Label lbl = new Label(parent, SWT.NONE);
        lbl.setText("  " + title);
        lbl.setBackground(colorHeaderBg);
        lbl.setForeground(colorTextFg);
        GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false);
        gd.heightHint = 24;
        lbl.setLayoutData(gd);
    }

    private StyledText buildCodePane(Composite parent, boolean readOnly) {
        int style = SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL;
        if (readOnly) style |= SWT.READ_ONLY;
        StyledText st = new StyledText(parent, style);
        st.setBackground(colorPaneBg);
        st.setForeground(colorTextFg);
        st.setFont(monoFont);
        st.setLeftMargin(6);
        st.setTopMargin(4);
        st.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        return st;
    }

    // ── Legend ────────────────────────────────────────────────────────────────

    private void buildLegend(Composite parent) {
        Composite legend = new Composite(parent, SWT.NONE);
        legend.setBackground(colorPaneBg);
        legend.setLayout(new GridLayout(6, false));
        legend.setLayoutData(new GridData(SWT.LEFT, SWT.BOTTOM, false, false));

        buildLegendItem(legend, colorAdded,   "  Added  ");
        buildLegendItem(legend, colorRemoved, "  Removed  ");
        buildLegendItem(legend, colorPaneBg,  "  Unchanged  ");
    }

    private void buildLegendItem(Composite parent, Color bg, String label) {
        Label swatch = new Label(parent, SWT.BORDER);
        swatch.setBackground(bg);
        GridData gd = new GridData(16, 14);
        swatch.setLayoutData(gd);

        Label lbl = new Label(parent, SWT.NONE);
        lbl.setText(label);
        lbl.setBackground(colorPaneBg);
        lbl.setForeground(colorTextFg);
    }

    // ── Diff coloring ─────────────────────────────────────────────────────────

    /**
     * Apply line-level diff colors to both panes.
     * Uses a simple set-based comparison — lines present only in one version
     * get colored; identical lines stay with the default background.
     */
    private void applyDiffColors() {
        if (currentCode.isEmpty()) {
            // All suggested lines are "added"
            colorAllLines(suggestedPane, colorAdded);
            return;
        }

        List<String> currentLines   = splitLines(currentCode);
        List<String> suggestedLines = splitLines(codeBlock.getContent());

        Set<String> currentSet   = new HashSet<>(currentLines);
        Set<String> suggestedSet = new HashSet<>(suggestedLines);

        // Color left pane: lines not present in suggested → removed (red)
        colorLines(currentPane,   currentLines,   line -> !suggestedSet.contains(line),
                   colorRemoved,  colorUnchanged);

        // Color right pane: lines not present in current → added (green)
        colorLines(suggestedPane, suggestedLines, line -> !currentSet.contains(line),
                   colorAdded,    colorUnchanged);
    }

    @FunctionalInterface
    private interface LinePredicate { boolean test(String line); }

    private void colorLines(StyledText pane, List<String> lines,
                            LinePredicate highlight,
                            Color highlightColor, Color normalColor) {
        List<StyleRange> ranges = new ArrayList<>();
        int offset = 0;
        for (String line : lines) {
            int len = line.length() + 1; // +1 for \n
            if (offset + len > pane.getCharCount()) len = pane.getCharCount() - offset;
            if (len <= 0) break;

            StyleRange sr = new StyleRange();
            sr.start      = offset;
            sr.length     = len;
            sr.background = highlight.test(line) ? highlightColor : normalColor;
            ranges.add(sr);
            offset += len;
        }
        pane.setStyleRanges(ranges.toArray(new StyleRange[0]));
    }

    private void colorAllLines(StyledText pane, Color color) {
        if (pane.getCharCount() == 0) return;
        StyleRange sr = new StyleRange();
        sr.start      = 0;
        sr.length     = pane.getCharCount();
        sr.background = color;
        pane.setStyleRange(sr);
    }

    private static List<String> splitLines(String text) {
        if (text == null || text.isEmpty()) return List.of();
        return Arrays.asList(text.split("\n", -1));
    }

    // ── Buttons ───────────────────────────────────────────────────────────────

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        if (hasSelection) {
            createButton(parent, APPLY_SELECTION, "Apply to Selection", true);
        }
        createButton(parent, APPLY_FILE,      "Apply to Entire File", !hasSelection);
        createButton(parent, COPY_CLIPBOARD,  "Copy to Clipboard",   false);
        createButton(parent, IDialogConstants.CANCEL_ID, "Cancel", false);
    }

    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == APPLY_SELECTION || buttonId == APPLY_FILE) {
            resultCode = suggestedPane.getText();
            setReturnCode(buttonId);
            close();
        } else if (buttonId == COPY_CLIPBOARD) {
            copyToClipboard();
            // Don't close — let user also apply if they want
        } else {
            super.buttonPressed(buttonId);
        }
    }

    private void copyToClipboard() {
        Clipboard cb = new Clipboard(Display.getCurrent());
        cb.setContents(
            new Object[]  { suggestedPane.getText() },
            new Transfer[]{ TextTransfer.getInstance() });
        cb.dispose();

        // Brief visual feedback
        Button btn = getButton(COPY_CLIPBOARD);
        if (btn != null) {
            String orig = btn.getText();
            btn.setText("✓ Copied");
            btn.setEnabled(false);
            Display.getCurrent().timerExec(1500, () -> {
                if (!btn.isDisposed()) {
                    btn.setText(orig);
                    btn.setEnabled(true);
                }
            });
        }
    }

    // ── Resources ─────────────────────────────────────────────────────────────

    private void initResources(Display d) {
        colorAdded     = new Color(d,  40,  80,  40);   // dark green
        colorRemoved   = new Color(d,  80,  30,  30);   // dark red
        colorUnchanged = new Color(d,  28,  28,  28);   // match main bg
        colorPaneBg    = new Color(d,  28,  28,  28);
        colorHeaderBg  = new Color(d,  18,  18,  18);
        colorTextFg    = new Color(d, 212, 212, 212);
        monoFont       = new Font(d, "Consolas", 10, SWT.NORMAL);
    }

    @Override
    public boolean close() {
        disposeResources();
        return super.close();
    }

    private void disposeResources() {
        Color[] colours = { colorAdded, colorRemoved, colorUnchanged,
                            colorPaneBg, colorHeaderBg, colorTextFg };
        for (Color c : colours) { if (c != null && !c.isDisposed()) c.dispose(); }
        if (monoFont != null && !monoFont.isDisposed()) monoFont.dispose();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** The final code to apply (may have been edited by user). Null if cancelled. */
    public String getResultCode() { return resultCode; }
}
