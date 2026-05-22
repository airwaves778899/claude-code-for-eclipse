package com.holtek.claudecode.editor;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Claude Code for Eclipse - Editor Context Helper (Phase 3)
 *
 * Utility methods to extract context from the active Eclipse text editor:
 *   - selected text
 *   - full document content
 *   - file name / extension
 *   - a formatted code block for use in prompts
 */
public final class EditorContextHelper {

    /** Maximum characters to include from selected code (≈2500 tokens). */
    public static final int MAX_CODE_LENGTH = 12_000;

    /** Maximum characters to include when sending the whole file. */
    public static final int MAX_FILE_LENGTH = 20_000;

    private EditorContextHelper() {}

    // ── Editor access ─────────────────────────────────────────────────────────

    /**
     * Returns the currently active ITextEditor, or null if none is open.
     */
    public static ITextEditor getActiveTextEditor() {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null) return null;
        IWorkbenchPage page = window.getActivePage();
        if (page == null) return null;
        IEditorPart editor = page.getActiveEditor();
        if (editor instanceof ITextEditor) {
            return (ITextEditor) editor;
        }
        // Try adapter (e.g., Java editor wraps ITextEditor)
        if (editor != null) {
            Object adapted = editor.getAdapter(ITextEditor.class);
            if (adapted instanceof ITextEditor) return (ITextEditor) adapted;
        }
        return null;
    }

    // ── Text / selection ──────────────────────────────────────────────────────

    /**
     * Returns the currently selected text in the editor, trimmed.
     * Returns an empty string if nothing is selected.
     */
    public static String getSelectedText(ITextEditor editor) {
        if (editor == null) return "";
        ISelectionProvider sp = editor.getSelectionProvider();
        if (sp == null) return "";
        ISelection sel = sp.getSelection();
        if (sel instanceof ITextSelection) {
            String text = ((ITextSelection) sel).getText();
            return text != null ? text : "";
        }
        return "";
    }

    /**
     * Returns the full content of the editor's document.
     * Truncated to {@link #MAX_FILE_LENGTH} if necessary.
     */
    public static String getDocumentContent(ITextEditor editor) {
        if (editor == null) return "";
        IDocumentProvider dp = editor.getDocumentProvider();
        if (dp == null) return "";
        IDocument doc = dp.getDocument(editor.getEditorInput());
        if (doc == null) return "";
        String content = doc.get();
        if (content == null) return "";
        if (content.length() > MAX_FILE_LENGTH) {
            return content.substring(0, MAX_FILE_LENGTH) +
                   "\n\n// ... [檔案內容已截斷，超過 " + MAX_FILE_LENGTH + " 字元]";
        }
        return content;
    }

    // ── File metadata ─────────────────────────────────────────────────────────

    /**
     * Returns the file name of the active editor (e.g., "MyClass.java").
     */
    public static String getFileName(ITextEditor editor) {
        if (editor == null) return "unknown";
        IEditorInput input = editor.getEditorInput();
        return input != null ? input.getName() : "unknown";
    }

    /**
     * Returns the file extension in lower-case (e.g., "java", "xml", "py").
     * Returns "txt" as fallback.
     */
    public static String getFileExtension(ITextEditor editor) {
        String name = getFileName(editor);
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "txt";
    }

    /**
     * Returns the full path of the editor input if available, otherwise the file name.
     */
    public static String getFilePath(ITextEditor editor) {
        if (editor == null) return "unknown";
        IEditorInput input = editor.getEditorInput();
        if (input == null) return "unknown";
        // IFileEditorInput has getFile().getFullPath(), but we avoid hard dependency
        // Use the toolTipText as it usually contains the full path
        String tooltip = input.getToolTipText();
        return (tooltip != null && !tooltip.isEmpty()) ? tooltip : input.getName();
    }

    // ── Prompt builder ────────────────────────────────────────────────────────

    /**
     * Build a formatted code block for inclusion in a Claude prompt.
     *
     * @param code     the raw source code
     * @param ext      file extension for syntax highlighting hint (e.g., "java")
     * @param fileName the file name (for context label)
     * @return formatted markdown-style code block
     */
    public static String buildCodeBlock(String code, String ext, String fileName) {
        String truncated = code;
        boolean wasTruncated = false;
        if (code.length() > MAX_CODE_LENGTH) {
            truncated = code.substring(0, MAX_CODE_LENGTH);
            wasTruncated = true;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("**檔案：** `").append(fileName).append("`\n");
        sb.append("```").append(ext).append("\n");
        sb.append(truncated);
        if (!truncated.endsWith("\n")) sb.append("\n");
        if (wasTruncated) {
            sb.append("// ... [程式碼已截斷，超過 ").append(MAX_CODE_LENGTH).append(" 字元]\n");
        }
        sb.append("```");
        return sb.toString();
    }

    /**
     * Check if there is a non-empty text selection in the given editor.
     */
    public static boolean hasSelection(ITextEditor editor) {
        return !getSelectedText(editor).isBlank();
    }
}
