package io.github.airwaves778899.claudecode.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

import io.github.airwaves778899.claudecode.context.ConsoleOutputCapture;
import io.github.airwaves778899.claudecode.editor.EditorContextHelper;
import io.github.airwaves778899.claudecode.views.ClaudeView;

/**
 * "Debug with Claude" — captures Eclipse console output and sends it to Claude
 * along with the current editor code for diagnosis.
 *
 * Trigger: Alt+Shift+D, or Claude menu → Debug with Claude
 *
 * Flow:
 *   1. Read last 150 lines from Eclipse console (focuses on stack traces)
 *   2. Read selected code from active editor (if any)
 *   3. Build a structured debug prompt
 *   4. Send to ClaudeView for immediate analysis
 */
public class DebugWithClaudeHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {

        // ── 1. Console output ─────────────────────────────────────────────────
        if (!ConsoleOutputCapture.hasConsoleOutput()) {
            MessageDialog.openInformation(
                HandlerUtil.getActiveShell(event),
                "Debug with Claude",
                "Eclipse Console 目前沒有輸出內容。\n\n" +
                "請先執行您的程式，出現錯誤後再使用此功能。");
            return null;
        }

        String rawOutput = ConsoleOutputCapture.getLatestOutput(
                ConsoleOutputCapture.DEFAULT_MAX_LINES);
        String stackTrace = ConsoleOutputCapture.extractStackTrace(rawOutput);

        // ── 2. Editor context (optional) ──────────────────────────────────────
        ITextEditor editor    = EditorContextHelper.getActiveTextEditor();
        String selectedCode   = editor != null
                ? EditorContextHelper.getSelectedText(editor) : "";
        String fileName       = editor != null
                ? EditorContextHelper.getFileName(editor) : "";
        String ext            = editor != null
                ? EditorContextHelper.getFileExtension(editor) : "java";

        // ── 3. Build prompt ───────────────────────────────────────────────────
        StringBuilder prompt = new StringBuilder();
        prompt.append("我的程式發生了以下錯誤，請幫我診斷原因並提供修正方法。\n\n");

        prompt.append("**Console 輸出 / Stack Trace：**\n```\n")
              .append(stackTrace)
              .append("\n```\n");

        if (!selectedCode.isBlank()) {
            String codeBlock = EditorContextHelper.buildCodeBlock(
                    selectedCode, ext, fileName);
            prompt.append("\n**相關程式碼：**\n").append(codeBlock).append("\n");
        }

        prompt.append("\n請：\n")
              .append("1. 說明這個錯誤的根本原因\n")
              .append("2. 提供修正方法（包含程式碼）\n")
              .append("3. 如果有預防性建議也請一併提供");

        // ── 4. Open ClaudeView and send ───────────────────────────────────────
        IWorkbenchPage page =
            HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
        try {
            ClaudeView view = (ClaudeView) page.showView(ClaudeView.ID);
            view.sendWithContext(prompt.toString(), true);
        } catch (PartInitException e) {
            throw new ExecutionException("無法開啟 Claude View", e);
        }

        return null;
    }
}
