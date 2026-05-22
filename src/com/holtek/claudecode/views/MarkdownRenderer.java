package com.holtek.claudecode.views;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;

/**
 * Parses Markdown in a plain-text string and returns SWT StyleRanges
 * that can be applied to a StyledText widget.
 *
 * Supported syntax:
 *   # / ## / ### headings
 *   **bold** / __bold__
 *   *italic* / _italic_
 *   `inline code`
 *   ```code block``` (fenced, multi-line)
 *   - / * list items (indented bullet)
 *
 * Usage:
 *   int offset = output.getCharCount();
 *   output.append(text);
 *   for (StyleRange sr : MarkdownRenderer.render(text, offset, colors)) {
 *       output.setStyleRange(sr);
 *   }
 */
public final class MarkdownRenderer {

    private MarkdownRenderer() {}

    /** Color + font bundle passed by the caller. */
    public static final class Colors {
        public final Color normal;     // default text colour
        public final Color heading;    // heading colour  (#569CD6 blue)
        public final Color bold;       // bold colour
        public final Color italic;     // italic colour   (light grey)
        public final Color code;       // inline code     (#CE9178 orange)
        public final Color codeBg;     // code block bg
        public final Color bullet;     // list bullet     (colMeta green)
        public final Font  monoFont;   // monospace font for code (may be null)

        public Colors(Color normal, Color heading, Color bold, Color italic,
                      Color code, Color codeBg, Color bullet) {
            this(normal, heading, bold, italic, code, codeBg, bullet, null);
        }
        public Colors(Color normal, Color heading, Color bold, Color italic,
                      Color code, Color codeBg, Color bullet, Font monoFont) {
            this.normal   = normal;
            this.heading  = heading;
            this.bold     = bold;
            this.italic   = italic;
            this.code     = code;
            this.codeBg   = codeBg;
            this.bullet   = bullet;
            this.monoFont = monoFont;
        }
    }

    // ── Patterns ───────────────────────────────────────────────────────────────

    // Fenced code block  ```...``` (DOTALL)
    private static final Pattern CODE_BLOCK =
            Pattern.compile("```(?:[a-zA-Z0-9]*)?\n?(.*?)```", Pattern.DOTALL);

    // Inline code `...`  (must not cross line)
    private static final Pattern INLINE_CODE =
            Pattern.compile("`([^`\n]+)`");

    // Heading  # / ## / ###
    private static final Pattern HEADING =
            Pattern.compile("^(#{1,3}) (.+)$", Pattern.MULTILINE);

    // Bold  **...** or __...__
    private static final Pattern BOLD =
            Pattern.compile("\\*\\*(.+?)\\*\\*|__(.+?)__");

    // Italic  *...* or _..._  (not if surrounded by *)
    private static final Pattern ITALIC =
            Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)|(?<!_)_(?!_)(.+?)(?<!_)_(?!_)");

    // List bullet  - or *  at start of line
    private static final Pattern BULLET =
            Pattern.compile("^[ \\t]*[-*] ", Pattern.MULTILINE);

    // ── Public entry point ─────────────────────────────────────────────────────

    /**
     * @param text   The plain text that was just appended
     * @param offset char offset in the StyledText where {@code text} starts
     * @param cols   colour bundle
     * @return list of StyleRanges to apply (may overlap; caller applies them in order)
     */
    public static List<StyleRange> render(String text, int offset, Colors cols) {
        List<StyleRange> out = new ArrayList<>();

        // Track which character ranges are code blocks so we skip inline parsing there
        boolean[] isCode = new boolean[text.length()];

        // 1. Fenced code blocks ─────────────────────────────────────────────────
        Matcher cb = CODE_BLOCK.matcher(text);
        while (cb.find()) {
            int s = cb.start(), e = cb.end();
            for (int i = s; i < e; i++) isCode[i] = true;

            StyleRange sr = new StyleRange();
            sr.start       = offset + s;
            sr.length      = e - s;
            sr.foreground  = cols.code;
            sr.background  = cols.codeBg;
            sr.fontStyle   = SWT.NORMAL;
            if (cols.monoFont != null) sr.font = cols.monoFont;
            out.add(sr);
        }

        // 2. Inline code ────────────────────────────────────────────────────────
        Matcher ic = INLINE_CODE.matcher(text);
        while (ic.find()) {
            if (anyTrue(isCode, ic.start(), ic.end())) continue;
            StyleRange sr = new StyleRange();
            sr.start      = offset + ic.start();
            sr.length     = ic.end() - ic.start();
            sr.foreground = cols.code;
            sr.fontStyle  = SWT.NORMAL;
            if (cols.monoFont != null) sr.font = cols.monoFont;
            out.add(sr);
        }

        // 3. Headings ───────────────────────────────────────────────────────────
        Matcher hm = HEADING.matcher(text);
        while (hm.find()) {
            if (anyTrue(isCode, hm.start(), hm.end())) continue;
            int level  = hm.group(1).length();          // 1, 2, or 3
            StyleRange sr = new StyleRange();
            sr.start      = offset + hm.start();
            sr.length     = hm.end() - hm.start();
            sr.foreground = cols.heading;
            sr.fontStyle  = level == 1 ? SWT.BOLD : (level == 2 ? SWT.BOLD : SWT.NORMAL);
            out.add(sr);
        }

        // 4. Bold ───────────────────────────────────────────────────────────────
        Matcher bm = BOLD.matcher(text);
        while (bm.find()) {
            if (anyTrue(isCode, bm.start(), bm.end())) continue;
            StyleRange sr = new StyleRange();
            sr.start      = offset + bm.start();
            sr.length     = bm.end() - bm.start();
            sr.foreground = cols.bold;
            sr.fontStyle  = SWT.BOLD;
            out.add(sr);
        }

        // 5. Italic ─────────────────────────────────────────────────────────────
        Matcher im = ITALIC.matcher(text);
        while (im.find()) {
            if (anyTrue(isCode, im.start(), im.end())) continue;
            StyleRange sr = new StyleRange();
            sr.start      = offset + im.start();
            sr.length     = im.end() - im.start();
            sr.foreground = cols.italic;
            sr.fontStyle  = SWT.ITALIC;
            out.add(sr);
        }

        // 6. List bullets ───────────────────────────────────────────────────────
        Matcher lm = BULLET.matcher(text);
        while (lm.find()) {
            if (anyTrue(isCode, lm.start(), lm.end())) continue;
            StyleRange sr = new StyleRange();
            sr.start      = offset + lm.start();
            sr.length     = lm.end() - lm.start();
            sr.foreground = cols.bullet;
            sr.fontStyle  = SWT.BOLD;
            out.add(sr);
        }

        return out;
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private static boolean anyTrue(boolean[] arr, int from, int to) {
        int end = Math.min(to, arr.length);
        for (int i = from; i < end; i++) if (arr[i]) return true;
        return false;
    }
}
