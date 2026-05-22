package io.github.airwaves778899.claudecode.actions;

import java.io.File;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;

import io.github.airwaves778899.claudecode.Activator;
import io.github.airwaves778899.claudecode.terminal.TerminalLauncher;

/**
 * Right-click action for IProject: opens Claude CLI in a real terminal window
 * (Windows Terminal if available, otherwise cmd.exe) at the project directory.
 */
public class OpenClaudeCliTerminalAction implements IObjectActionDelegate {

    private ISelection currentSelection;
    private IWorkbenchPart activePart;

    @Override
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
        this.activePart = targetPart;
    }

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        this.currentSelection = selection;
    }

    @Override
    public void run(IAction action) {
        if (!(currentSelection instanceof IStructuredSelection)) return;
        Object first = ((IStructuredSelection) currentSelection).getFirstElement();
        String projectPath = resolveProjectPath(first);
        if (projectPath == null) return;

        // Resolve claude CLI path from preferences, fallback to "claude" on PATH
        String cliPath = Activator.getDefault()
                .getPreferenceStore().getString(Activator.PREF_CLI_PATH).trim();
        if (cliPath.isEmpty()) cliPath = "claude";

        try {
            TerminalLauncher.launch(projectPath, cliPath);
        } catch (Exception e) {
            MessageDialog.openError(
                activePart.getSite().getShell(),
                "Claude CLI",
                "無法開啟終端機：" + e.getMessage());
        }
    }

    private static String resolveProjectPath(Object element) {
        if (!(element instanceof IAdaptable)) return null;
        IResource res = ((IAdaptable) element).getAdapter(IResource.class);
        if (res == null) return null;
        IProject project = res.getProject();
        if (project == null || !project.isOpen()) return null;
        org.eclipse.core.runtime.IPath loc = project.getLocation();
        if (loc == null) return null;
        String path = loc.toOSString();
        return new File(path).isDirectory() ? path : null;
    }
}
