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
                "Eclipse Console has no output.\n\n" +
                "Please run your program first, then use this feature after an error occurs.");
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
        prompt.append("The following error occurred. Please diagnose the cause and provide a fix.\n\n");

        prompt.append("**Console Output / Stack Trace:**\n```\n")
              .append(stackTrace)
              .append("\n```\n");

        if (!selectedCode.isBlank()) {
            String codeBlock = EditorContextHelper.buildCodeBlock(
                    selectedCode, ext, fileName);
            prompt.append("\n**Related code:**\n").append(codeBlock).append("\n");
        }

        prompt.append("\nPlease:\n")
              .append("1. Explain the root cause of this error\n")
              .append("2. Provide a fix (with code)\n")
              .append("3. Include preventive suggestions if applicable");

        // ── 4. Open ClaudeView and send ───────────────────────────────────────
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
