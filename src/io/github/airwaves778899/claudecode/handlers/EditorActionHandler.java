package io.github.airwaves778899.claudecode.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

import io.github.airwaves778899.claudecode.editor.EditorContextHelper;
import io.github.airwaves778899.claudecode.views.ClaudeView;

/**
 * Abstract base handler for all editor-aware Claude actions.
 *
 * Subclasses implement {@link #buildPrompt(String, String, String)} to
 * define the specific instruction sent to Claude.
 *
 * Flow:
 *   1. Locate the active ITextEditor
 *   2. Read selected text (falls back to full file if nothing selected)
 *   3. Call buildPrompt() — implemented by subclass
 *   4. Open ClaudeView and send the prompt
 */
public abstract class EditorActionHandler extends AbstractHandler {

    @Override
    public final Object execute(ExecutionEvent event) throws ExecutionException {

        ITextEditor editor = EditorContextHelper.getActiveTextEditor();

        if (editor == null) {
            MessageDialog.openInformation(
                HandlerUtil.getActiveShell(event),
                "Claude Code",
                "請先在文字編輯器中開啟一個檔案。");
            return null;
        }

        String selectedText = EditorContextHelper.getSelectedText(editor);
        String fileName     = EditorContextHelper.getFileName(editor);
        String ext          = EditorContextHelper.getFileExtension(editor);

        // If nothing selected, offer to use whole file or bail
        if (selectedText.isBlank()) {
            boolean useFile = askUseWholeFile(event, fileName);
            if (!useFile) return null;
            selectedText = EditorContextHelper.getDocumentContent(editor);
            if (selectedText.isBlank()) {
                MessageDialog.openInformation(
                    HandlerUtil.getActiveShell(event),
                    "Claude Code",
                    "檔案內容為空。");
                return null;
            }
        }

        String codeBlock = EditorContextHelper.buildCodeBlock(selectedText, ext, fileName);
        String prompt    = buildPrompt(fileName, ext, codeBlock);

        // Open (or bring to front) the Claude View and send
        IWorkbenchPage page =
            HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
        try {
            ClaudeView view = (ClaudeView) page.showView(ClaudeView.ID);
            view.sendWithContext(prompt, autoSend());
        } catch (PartInitException e) {
            throw new ExecutionException("無法開啟 Claude View", e);
        }

        return null;
    }

    /**
     * Build the prompt string to send to Claude.
     *
     * @param fileName  e.g., "MyService.java"
     * @param ext       e.g., "java"
     * @param codeBlock pre-formatted markdown code block (already includes fileName header)
     * @return the full message to send
     */
    protected abstract String buildPrompt(String fileName, String ext, String codeBlock);

    /**
     * Whether to auto-send immediately (true) or just populate the input field (false).
     * Defaults to true. Override in subclasses that want the user to review first.
     */
    protected boolean autoSend() {
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean askUseWholeFile(ExecutionEvent event, String fileName) {
        return MessageDialog.openQuestion(
            HandlerUtil.getActiveShell(event),
            "Claude Code",
            "尚未選取程式碼。\n\n要將整個 " + fileName + " 的內容傳給 Claude 嗎？");
    }
}
