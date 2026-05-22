package io.github.airwaves778899.claudecode.terminal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import io.github.airwaves778899.claudecode.mcp.McpTools;

/**
 * Side-by-side diff dialog showing current file content vs Claude's proposed
 * content. Accept writes the proposed content to disk and refreshes Eclipse.
 */
public class DiffApplyDialog extends Dialog {

    // ── Input data ────────────────────────────────────────────────────────────
    private final String filePath;
    private final String currentContent;
    private final String proposedContent;

    // ── Widgets ───────────────────────────────────────────────────────────────
    private StyledText leftText;
    private StyledText rightText;

    // ── Resources (disposed on close) ─────────────────────────────────────────
    private Color colBg;
    private Color colRemovedBg;
    private Color colAddedBg;
    private Color colFg;
    private Font  monoFont;

    // ── Diff types ────────────────────────────────────────────────────────────
    private static final int UNCHANGED = 0;
    private static final int REMOVED   = 1;
    private static final int ADDED     = 2;

    /** One entry in the merged diff view. */
    private static class DiffLine {
        final int    type;
        final String leftText;   // null when ADDED
        final String rightText;  // null when REMOVED
        DiffLine(int type, String leftText, String rightText) {
            this.type      = type;
            this.leftText  = leftText;
            this.rightText = rightText;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    public DiffApplyDialog(Shell parent, String filePath,
                           String currentContent, String proposedContent) {
        super(parent);
        this.filePath        = filePath;
        this.currentContent  = currentContent;
        this.proposedContent = proposedContent;
        setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
    }

    // ── Shell configuration ───────────────────────────────────────────────────

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        String fileName = Paths.get(filePath).getFileName().toString();
        shell.setText("Apply Changes — " + fileName);
    }

    @Override
    protected Point getInitialSize() {
        return new Point(900, 600);
    }

    // ── Content area ──────────────────────────────────────────────────────────

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);

        allocateColors(area.getDisplay());
        monoFont = buildMonoFont(area.getDisplay());

        area.setBackground(colBg);
        area.setLayout(new GridLayout(1, false));

        // Title bar
        createTitleLabel(area);

        // Split pane
        createSplitPane(area);

        return area;
    }

    private void createTitleLabel(Composite parent) {
        Label lbl = new Label(parent, SWT.NONE);
        lbl.setText("Modifying: " + filePath);
        lbl.setBackground(colBg);
        lbl.setForeground(colFg);
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.horizontalSpan = 1;
        lbl.setLayoutData(gd);
    }

    private void createSplitPane(Composite parent) {
        Composite split = new Composite(parent, SWT.NONE);
        split.setBackground(colBg);
        split.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        split.setLayout(new GridLayout(2, true));

        // ── Left pane ──
        Composite leftPane = createPane(split, "Current");
        leftText = createStyledText(leftPane);

        // ── Right pane ──
        Composite rightPane = createPane(split, "Proposed");
        rightText = createStyledText(rightPane);

        // Synchronise scrolling
        linkScrolling(leftText, rightText);

        // Populate with diff content
        populateDiff();
    }

    private Composite createPane(Composite parent, String title) {
        Composite pane = new Composite(parent, SWT.NONE);
        pane.setBackground(colBg);
        pane.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout gl = new GridLayout(1, false);
        gl.marginWidth  = 4;
        gl.marginHeight = 4;
        pane.setLayout(gl);

        Label lbl = new Label(pane, SWT.NONE);
        lbl.setText(title);
        lbl.setBackground(colBg);
        lbl.setForeground(colFg);
        FontData[] fds = lbl.getFont().getFontData();
        for (FontData fd : fds) { fd.setStyle(SWT.BOLD); fd.setHeight(fd.getHeight() + 1); }
        lbl.setFont(new Font(parent.getDisplay(), fds));
        lbl.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        return pane;
    }

    private StyledText createStyledText(Composite parent) {
        StyledText st = new StyledText(parent,
                SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
        st.setFont(monoFont);
        st.setBackground(colBg);
        st.setForeground(colFg);
        st.setEditable(false);
        st.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        return st;
    }

    private void linkScrolling(StyledText left, StyledText right) {
        left.getVerticalBar().addListener(SWT.Selection, e ->
                right.setTopIndex(left.getTopIndex()));
        right.getVerticalBar().addListener(SWT.Selection, e ->
                left.setTopIndex(right.getTopIndex()));
    }

    // ── Diff computation & rendering ──────────────────────────────────────────

    private void populateDiff() {
        String[] leftLines  = splitLines(currentContent);
        String[] rightLines = splitLines(proposedContent);

        List<DiffLine> diff = computeDiff(leftLines, rightLines);

        StringBuilder leftSb  = new StringBuilder();
        StringBuilder rightSb = new StringBuilder();
        List<int[]>   leftHighlights  = new ArrayList<>(); // [charOffset, length]
        List<int[]>   rightHighlights = new ArrayList<>();

        int leftOffset  = 0;
        int rightOffset = 0;

        for (DiffLine dl : diff) {
            switch (dl.type) {

                case UNCHANGED: {
                    String line = dl.leftText + "\n";
                    leftSb.append(line);
                    rightSb.append(line);
                    leftOffset  += line.length();
                    rightOffset += line.length();
                    break;
                }

                case REMOVED: {
                    String line = dl.leftText + "\n";
                    leftHighlights.add(new int[]{ leftOffset, line.length() });
                    leftSb.append(line);
                    leftOffset += line.length();

                    // Blank placeholder on right to keep lines aligned
                    rightSb.append("\n");
                    rightOffset += 1;
                    break;
                }

                case ADDED: {
                    String line = dl.rightText + "\n";
                    rightHighlights.add(new int[]{ rightOffset, line.length() });
                    rightSb.append(line);
                    rightOffset += line.length();

                    // Blank placeholder on left
                    leftSb.append("\n");
                    leftOffset += 1;
                    break;
                }
            }
        }

        leftText.setText(leftSb.toString());
        rightText.setText(rightSb.toString());

        applyHighlights(leftText,  leftHighlights,  colRemovedBg);
        applyHighlights(rightText, rightHighlights, colAddedBg);
    }

    private void applyHighlights(StyledText st, List<int[]> ranges, Color bg) {
        StyleRange[] styleRanges = new StyleRange[ranges.size()];
        for (int i = 0; i < ranges.size(); i++) {
            StyleRange sr = new StyleRange();
            sr.start      = ranges.get(i)[0];
            sr.length     = ranges.get(i)[1];
            sr.background = bg;
            styleRanges[i] = sr;
        }
        st.setStyleRanges(styleRanges);
    }

    /**
     * Simple LCS-based line diff. Returns a merged list of UNCHANGED / REMOVED /
     * ADDED entries suitable for side-by-side display.
     */
    private List<DiffLine> computeDiff(String[] a, String[] b) {
        int m = a.length;
        int n = b.length;

        // Build LCS table
        int[][] dp = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--) {
            for (int j = n - 1; j >= 0; j--) {
                if (a[i].equals(b[j])) {
                    dp[i][j] = 1 + dp[i + 1][j + 1];
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        // Trace back to produce diff
        List<DiffLine> result = new ArrayList<>();
        int i = 0, j = 0;
        while (i < m && j < n) {
            if (a[i].equals(b[j])) {
                result.add(new DiffLine(UNCHANGED, a[i], b[j]));
                i++; j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                result.add(new DiffLine(REMOVED, a[i], null));
                i++;
            } else {
                result.add(new DiffLine(ADDED, null, b[j]));
                j++;
            }
        }
        while (i < m) { result.add(new DiffLine(REMOVED, a[i++], null)); }
        while (j < n) { result.add(new DiffLine(ADDED,   null,   b[j++])); }
        return result;
    }

    private String[] splitLines(String content) {
        if (content == null || content.isEmpty()) return new String[0];
        // Preserve trailing blank line if content ends with newline
        String normalised = content.replace("\r\n", "\n").replace("\r", "\n");
        return normalised.split("\n", -1);
    }

    // ── Button area ───────────────────────────────────────────────────────────

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID,
                "✅ Apply", true);
        createButton(parent, IDialogConstants.CANCEL_ID,
                "❌ Cancel", false);
    }

    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == IDialogConstants.OK_ID) {
            if (applyChanges()) {
                setReturnCode(OK);
                close();
            }
        } else {
            setReturnCode(CANCEL);
            close();
        }
    }

    // ── Apply logic ───────────────────────────────────────────────────────────

    private boolean applyChanges() {
        try {
            Files.writeString(Paths.get(filePath), proposedContent,
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            showError("Failed to write file: " + e.getMessage());
            return false;
        }

        try {
            McpTools.call("eclipse_refresh", "{}");
        } catch (Exception e) {
            // Refresh failure is non-fatal — file was written successfully
        }

        return true;
    }

    private void showError(String message) {
        org.eclipse.jface.dialogs.MessageDialog.openError(
                getShell(), "Apply Failed", message);
    }

    // ── Color allocation ──────────────────────────────────────────────────────

    private void allocateColors(Display display) {
        boolean dark = isDarkTheme(display);

        if (dark) {
            colBg        = new Color(display, new RGB(0x1e, 0x1e, 0x1e));
            colFg        = new Color(display, new RGB(0xd4, 0xd4, 0xd4));
            colRemovedBg = new Color(display, new RGB(100, 20, 20));
            colAddedBg   = new Color(display, new RGB(20,  80, 20));
        } else {
            colBg        = new Color(display, new RGB(0xfa, 0xfa, 0xfa));
            colFg        = new Color(display, new RGB(0x1e, 0x1e, 0x1e));
            colRemovedBg = new Color(display, new RGB(255, 230, 230));
            colAddedBg   = new Color(display, new RGB(230, 255, 230));
        }
    }

    private static boolean isDarkTheme(Display display) {
        Color bg = display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
        // Perceived brightness via standard luminance coefficients
        double brightness = 0.299 * bg.getRed()
                          + 0.587 * bg.getGreen()
                          + 0.114 * bg.getBlue();
        return brightness < 128;
    }

    private Font buildMonoFont(Display display) {
        String[] candidates = { "Consolas", "Courier New", "Monospace", "Courier" };
        for (String name : candidates) {
            Font f = new Font(display, name, 10, SWT.NORMAL);
            if (f.getFontData()[0].getName().equalsIgnoreCase(name)) return f;
            f.dispose();
        }
        return new Font(display, display.getSystemFont().getFontData()[0].getName(), 10, SWT.NORMAL);
    }

    // ── Resource disposal ─────────────────────────────────────────────────────

    @Override
    public boolean close() {
        disposeResources();
        return super.close();
    }

    private void disposeResources() {
        disposeIfNotNull(colBg);
        disposeIfNotNull(colFg);
        disposeIfNotNull(colRemovedBg);
        disposeIfNotNull(colAddedBg);
        disposeIfNotNull(monoFont);
    }

    private static void disposeIfNotNull(org.eclipse.swt.graphics.Resource r) {
        if (r != null && !r.isDisposed()) r.dispose();
    }
}
