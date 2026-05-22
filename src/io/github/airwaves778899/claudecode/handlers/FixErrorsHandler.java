package io.github.airwaves778899.claudecode.handlers;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

import io.github.airwaves778899.claudecode.context.ProjectContextProvider;
import io.github.airwaves778899.claudecode.context.ProblemsHelper;
import io.github.airwaves778899.claudecode.editor.EditorContextHelper;
import io.github.airwaves778899.claudecode.views.ClaudeView;

/**
 * "Fix Compile Errors" — reads Eclipse Problems view errors and sends them
 * to Claude along with the relevant source file for diagnosis and fix.
 *
 * Trigger: Alt+Shift+R, or Claude menu → Fix Compile Errors
 *
 * Flow:
 *   1. Get active project's compile errors from Eclipse markers
 *   2. Get current editor file content (or selected code)
 *   3. Build a structured fix prompt
 *   4. Send to ClaudeView
 */
public class FixErrorsHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {

        // ── 1. Get active project ─────────────────────────────────────────────
        IProject project = ProjectContextProvider.getActiveProject();
        if (project == null) {
            MessageDialog.openInformation(
                HandlerUtil.getActiveShell(event),
                "Fix Compile Errors",
                "找不到活動中的 Eclipse 專案。\n請先在編輯器中開啟一個 Java 檔案。");
            return null;
        }

        // ── 2. Read compile errors ────────────────────────────────────────────
        List<String> errors;
        try {
            errors = ProblemsHelper.getErrors(project);
        } catch (CoreException e) {
            throw new ExecutionException("無法讀取專案錯誤清單", e);
        }

        if (errors.isEmpty()) {
            MessageDialog.openInformation(
                HandlerUtil.getActiveShell(event),
                "Fix Compile Errors",
                "專案「" + project.getName() + "」目前沒有編譯錯誤！");
            return null;
        }

        // ── 3. Get editor context ─────────────────────────────────────────────
        ITextEditor editor  = EditorContextHelper.getActiveTextEditor();
        String editorCode   = "";
        String fileName     = "";
        String ext          = "java";

        if (editor != null) {
            fileName  = EditorContextHelper.getFileName(editor);
            ext       = EditorContextHelper.getFileExtension(editor);
            // Prefer selection; fall back to whole file
            String sel = EditorContextHelper.getSelectedText(editor);
            editorCode = !sel.isBlank() ? sel
                       : EditorContextHelper.getDocumentContent(editor);
        }

        // ── 4. Build prompt ───────────────────────────────────────────────────
        StringBuilder prompt = new StringBuilder();
        prompt.append("請幫我修正以下 Eclipse 編譯錯誤。\n\n");
        prompt.append("**專案：** ").append(project.getName()).append("\n");
        prompt.append("**問題摘要：** ").append(ProblemsHelper.getSummary(project)).append("\n\n");

        // Error list
        prompt.append("**編譯錯誤清單：**\n```\n");
        int shown = Math.min(errors.size(), ProblemsHelper.MAX_PROBLEMS);
        for (int i = 0; i < shown; i++) {
            prompt.append(errors.get(i)).append("\n");
        }
        if (errors.size() > shown) {
            prompt.append("... 以及另外 ").append(errors.size() - shown).append(" 個錯誤\n");
        }
        prompt.append("```\n");

        // Editor code
        if (!editorCode.isBlank()) {
            String codeBlock = EditorContextHelper.buildCodeBlock(editorCode, ext, fileName);
            prompt.append("\n**目前編輯器中的程式碼：**\n").append(codeBlock).append("\n");
        }

        prompt.append("\n請針對每個錯誤：\n")
              .append("1. 說明原因\n")
              .append("2. 提供修正後的程式碼\n")
              .append("3. 如有多個錯誤，請依序處理");

        // ── 5. Send to ClaudeView ─────────────────────────────────────────────
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
