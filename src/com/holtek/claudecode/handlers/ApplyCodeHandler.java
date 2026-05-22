package com.holtek.claudecode.handlers;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

import com.holtek.claudecode.apply.ApplyCodeDialog;
import com.holtek.claudecode.apply.CodeBlock;
import com.holtek.claudecode.editor.EditorContextHelper;
import com.holtek.claudecode.views.ClaudeView;

/**
 * Handler for the "Apply Code to Editor" command (Alt+Shift+P).
 *
 * Flow:
 *   1. Get the last code block(s) from ClaudeView
 *   2. Get current editor selection (if any)
 *   3. Open ApplyCodeDialog with diff preview
 *   4. On confirm: replace selection or whole document
 */
public class ApplyCodeHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {

        IWorkbenchPage page =
            HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();

        // ── 1. Get code blocks from ClaudeView ──────────────────────────────
        ClaudeView view = (ClaudeView) page.findView(ClaudeView.ID);
        if (view == null) {
            MessageDialog.openInformation(
                HandlerUtil.getActiveShell(event),
                "Apply Code",
                "Claude Chat 面板尚未開啟，請先使用 Alt+Shift+C 開啟。");
            return null;
        }

        List<CodeBlock> blocks = view.getLastCodeBlocks();
        if (blocks == null || blocks.isEmpty()) {
            MessageDialog.openInformation(
                HandlerUtil.getActiveShell(event),
                "Apply Code",
                "上一則 Claude 回應中未包含程式碼區塊。\n請先向 Claude 提問並取得包含程式碼的回應。");
            return null;
        }

        // Pick the best block (largest source code block)
        CodeBlock target = blocks.size() == 1
                ? blocks.get(0)
                : pickBlock(event, blocks);
        if (target == null) return null;

        // ── 2. Get editor context ────────────────────────────────────────────
        ITextEditor editor  = EditorContextHelper.getActiveTextEditor();
        String currentCode  = "";
        boolean hasSelection = false;
        String  fileName    = "editor";

        if (editor != null) {
            fileName = EditorContextHelper.getFileName(editor);
            String sel = EditorContextHelper.getSelectedText(editor);
            if (!sel.isBlank()) {
                currentCode  = sel;
                hasSelection = true;
            } else {
                currentCode = EditorContextHelper.getDocumentContent(editor);
            }
        }

        // ── 3. Open diff dialog ──────────────────────────────────────────────
        ApplyCodeDialog dialog = new ApplyCodeDialog(
            HandlerUtil.getActiveShell(event),
            target, currentCode, fileName, hasSelection);

        int result = dialog.open();
        String code = dialog.getResultCode();
        if (code == null || code.isEmpty()) return null;

        // ── 4. Apply ─────────────────────────────────────────────────────────
        if (editor == null) return null;   // already copied to clipboard

        try {
            if (result == ApplyCodeDialog.APPLY_SELECTION && hasSelection) {
                applyToSelection(editor, code);
            } else if (result == ApplyCodeDialog.APPLY_FILE) {
                applyToFile(editor, code);
            }
        } catch (BadLocationException e) {
            throw new ExecutionException("套用程式碼時發生錯誤", e);
        }

        return null;
    }

    // ── Apply helpers ─────────────────────────────────────────────────────────

    private void applyToSelection(ITextEditor editor, String code)
            throws BadLocationException {
        ISelection sel = editor.getSelectionProvider().getSelection();
        if (!(sel instanceof ITextSelection)) return;

        ITextSelection textSel = (ITextSelection) sel;
        IDocument doc = getDocument(editor);
        if (doc == null) return;

        doc.replace(textSel.getOffset(), textSel.getLength(), code);
    }

    private void applyToFile(ITextEditor editor, String code)
            throws BadLocationException {
        IDocument doc = getDocument(editor);
        if (doc == null) return;
        doc.replace(0, doc.getLength(), code);
    }

    private IDocument getDocument(ITextEditor editor) {
        IDocumentProvider dp = editor.getDocumentProvider();
        if (dp == null) return null;
        return dp.getDocument(editor.getEditorInput());
    }

    // ── Block selector (when multiple blocks exist) ───────────────────────────

    private CodeBlock pickBlock(ExecutionEvent event, List<CodeBlock> blocks)
            throws ExecutionException {
        // Build labels for selection dialog
        String[] labels = blocks.stream()
                .map(CodeBlock::getLabel)
                .toArray(String[]::new);

        org.eclipse.ui.dialogs.ListDialog picker =
            new org.eclipse.ui.dialogs.ListDialog(HandlerUtil.getActiveShell(event));
        picker.setTitle("選擇要套用的程式碼區塊");
        picker.setMessage("此回應包含多個程式碼區塊，請選擇要套用的一個：");
        picker.setContentProvider(
            new org.eclipse.jface.viewers.ArrayContentProvider());
        picker.setLabelProvider(
            new org.eclipse.jface.viewers.LabelProvider());
        picker.setInput(labels);

        if (picker.open() != org.eclipse.jface.window.Window.OK) return null;

        Object[] selected = picker.getResult();
        if (selected == null || selected.length == 0) return null;

        // Match back to block index
        String chosenLabel = (String) selected[0];
        return blocks.stream()
                .filter(b -> b.getLabel().equals(chosenLabel))
                .findFirst()
                .orElse(blocks.get(0));
    }
}
