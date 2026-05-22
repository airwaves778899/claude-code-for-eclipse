package io.github.airwaves778899.claudecode.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

import io.github.airwaves778899.claudecode.editor.EditorContextHelper;
import io.github.airwaves778899.claudecode.views.ClaudeView;

/**
 * "Ask Claude..." — opens a dialog for the user to type a custom question,
 * then sends the question together with any selected code as context.
 *
 * If no code is selected the user can still ask a general question.
 */
public class AskWithCodeHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {

        ITextEditor editor    = EditorContextHelper.getActiveTextEditor();
        String selectedText   = editor != null ? EditorContextHelper.getSelectedText(editor)  : "";
        String fileName       = editor != null ? EditorContextHelper.getFileName(editor)       : "";
        String ext            = editor != null ? EditorContextHelper.getFileExtension(editor)  : "txt";

        // Open input dialog
        String dialogTitle   = "Ask Claude";
        String dialogMessage = selectedText.isBlank()
                ? "Please enter your question:"
                : "Please enter your question about selected code in " + fileName + ":";

        InputDialog dialog = new InputDialog(
                HandlerUtil.getActiveShell(event),
                dialogTitle,
                dialogMessage,
                "",
                input -> (input == null || input.isBlank()) ? "Question cannot be empty" : null);

        if (dialog.open() != Window.OK) return null;

        String question = dialog.getValue().trim();
        if (question.isEmpty()) return null;

        // Build prompt
        String prompt;
        if (selectedText.isBlank()) {
            prompt = question;
        } else {
            String codeBlock = EditorContextHelper.buildCodeBlock(selectedText, ext, fileName);
            prompt = question + "\n\n" + codeBlock;
        }

        // Open ClaudeView — false means: show in input field (user can review before sending)
        IWorkbenchPage page =
                HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
        try {
            ClaudeView view = (ClaudeView) page.showView(ClaudeView.ID);
            view.sendWithContext(prompt, true);   // auto-send since user already confirmed in dialog
        } catch (PartInitException e) {
            throw new ExecutionException("Cannot open Claude View", e);
        }

        return null;
    }
}
