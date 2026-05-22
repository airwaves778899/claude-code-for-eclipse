package com.holtek.claudecode.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.core.resources.IFile;

import com.holtek.claudecode.views.ClaudeTerminalView;

/**
 * Handler for "Ask Claude about selection" — triggered from editor right-click menu.
 * Gets the currently selected text, opens Claude Chat view, and populates the input.
 */
public class AskClaudeAboutSelectionHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
        if (window == null) return null;

        IWorkbenchPage page = window.getActivePage();
        if (page == null) return null;

        // Get selected text from active editor
        IEditorPart editor = page.getActiveEditor();
        if (!(editor instanceof ITextEditor)) return null;

        ITextEditor textEditor = (ITextEditor) editor;
        ISelection selection = textEditor.getSelectionProvider().getSelection();
        if (!(selection instanceof ITextSelection)) return null;

        ITextSelection textSel = (ITextSelection) selection;
        String selectedText = textSel.getText();
        if (selectedText == null || selectedText.trim().isEmpty()) return null;

        // Get the file path
        String filePath = null;
        IEditorInput input = editor.getEditorInput();
        IFile iFile = input.getAdapter(IFile.class);
        if (iFile != null && iFile.getLocation() != null) {
            filePath = iFile.getLocation().toOSString();
        }

        // Open Claude Chat view
        ClaudeTerminalView view = null;
        try {
            view = (ClaudeTerminalView) page.showView(ClaudeTerminalView.ID,
                    null, IWorkbenchPage.VIEW_VISIBLE);
        } catch (PartInitException e) {
            return null;
        }

        if (view != null) {
            view.setSelectedCode(selectedText, filePath);
        }

        return null;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
