package io.github.airwaves778899.claudecode.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

import io.github.airwaves778899.claudecode.views.ClaudeTerminalView;

/**
 * Opens (or focuses) the Claude Chat view.
 */
public class OpenChatViewHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
        try {
            page.showView(ClaudeTerminalView.ID);
        } catch (PartInitException e) {
            MessageDialog.openError(
                HandlerUtil.getActiveShell(event),
                "Claude Chat",
                "無法開啟 Claude Chat view。\n\n" + e.getMessage());
        }
        return null;
    }
}
