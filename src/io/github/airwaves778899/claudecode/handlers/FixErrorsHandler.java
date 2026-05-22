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
                "No active Eclipse project found.\nPlease open a Java file in the editor first.");
            return null;
        }

        // ── 2. Read compile errors ────────────────────────────────────────────
        List<String> errors;
        try {
            errors = ProblemsHelper.getErrors(project);
        } catch (CoreException e) {
            throw new ExecutionException("Cannot read project error list", e);
        }

        if (errors.isEmpty()) {
            MessageDialog.openInformation(
                HandlerUtil.getActiveShell(event),
                "Fix Compile Errors",
                "Project \"" + project.getName() + "\" has no compilation errors!");
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
        prompt.append("Please help me fix the following Eclipse compilation errors.\n\n");
        prompt.append("**Project:** ").append(project.getName()).append("\n");
        prompt.append("**Issue summary:** ").append(ProblemsHelper.getSummary(project)).append("\n\n");

        // Error list
        prompt.append("**Compilation error list:**\n```\n");
        int shown = Math.min(errors.size(), ProblemsHelper.MAX_PROBLEMS);
        for (int i = 0; i < shown; i++) {
            prompt.append(errors.get(i)).append("\n");
        }
        if (errors.size() > shown) {
            prompt.append("... and " + (errors.size() - shown) + " more error(s)\n");
        }
        prompt.append("```\n");

        // Editor code
        if (!editorCode.isBlank()) {
            String codeBlock = EditorContextHelper.buildCodeBlock(editorCode, ext, fileName);
            prompt.append("\n**Current code in editor:**\n").append(codeBlock).append("\n");
        }

        prompt.append("\nFor each error please:\n")
              .append("1. Explain the cause\n")
              .append("2. Provide the fixed code\n")
              .append("3. If multiple errors, address them in order");

        // ── 5. Send to ClaudeView ─────────────────────────────────────────────
        IWorkbenchPage page =
            HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
        try {
            ClaudeView view = (ClaudeView) page.showView(ClaudeView.ID);
            view.sendWithContext(prompt.toString(), true);
        } catch (PartInitException e) {
            throw new ExecutionException("Cannot open Claude View", e);
        }

        return null;
    }
}
