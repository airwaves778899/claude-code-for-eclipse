package io.github.airwaves778899.claudecode.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.core.resources.IFile;

import io.github.airwaves778899.claudecode.views.ClaudeTerminalView;

/**
 * "Ask Claude about selection" triggered from the Project/Package Explorer
 * context menu (objectContribution).  Reads the selected text from whatever
 * editor is currently active and forwards it to the Claude Chat view.
 */
public class AskClaudeProjectAction implements IObjectActionDelegate {

    @Override
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
        // Not needed — we read the active editor from the workbench directly.
    }

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        // Not needed.
    }

    @Override
    public void run(IAction action) {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null) return;

        IWorkbenchPage page = window.getActivePage();
        if (page == null) return;

        // Require an active text editor with a non-empty selection
        IEditorPart editor = page.getActiveEditor();
        if (!(editor instanceof ITextEditor)) return;

        ITextEditor textEditor = (ITextEditor) editor;
        ISelection sel = textEditor.getSelectionProvider().getSelection();
        if (!(sel instanceof ITextSelection)) return;

        ITextSelection textSel = (ITextSelection) sel;
        String selectedText = textSel.getText();
        if (selectedText == null || selectedText.trim().isEmpty()) return;

        // Resolve the file path (optional)
        String filePath = null;
        IEditorInput input = editor.getEditorInput();
        IFile iFile = input.getAdapter(IFile.class);
        if (iFile != null && iFile.getLocation() != null) {
            filePath = iFile.getLocation().toOSString();
        }

        // Open the Claude Chat view and send the selection
        ClaudeTerminalView view = null;
        try {
            view = (ClaudeTerminalView) page.showView(
                    ClaudeTerminalView.ID, null, IWorkbenchPage.VIEW_VISIBLE);
        } catch (PartInitException e) {
            return;
        }

        if (view != null) {
            view.setSelectedCode(selectedText, filePath);
        }
    }
}
