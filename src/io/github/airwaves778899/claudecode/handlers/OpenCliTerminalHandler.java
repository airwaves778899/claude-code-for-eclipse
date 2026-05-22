package io.github.airwaves778899.claudecode.handlers;

import java.io.File;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

import io.github.airwaves778899.claudecode.Activator;
import io.github.airwaves778899.claudecode.terminal.TerminalLauncher;

/**
 * Command handler for "Open Claude CLI Terminal".
 * Opens a real terminal window (Windows Terminal / cmd.exe) at the selected
 * project directory and launches the Claude CLI.
 */
public class OpenCliTerminalHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {

        ISelection sel = HandlerUtil.getCurrentSelection(event);
        if (!(sel instanceof IStructuredSelection)) return null;

        Object first = ((IStructuredSelection) sel).getFirstElement();
        String projectPath = resolveProjectPath(first);
        if (projectPath == null) return null;

        String cliPath = Activator.getDefault()
                .getPreferenceStore().getString(Activator.PREF_CLI_PATH).trim();
        if (cliPath.isEmpty()) cliPath = "claude";

        try {
            TerminalLauncher.launch(projectPath, cliPath);
        } catch (Exception e) {
            MessageDialog.openError(
                HandlerUtil.getActiveShell(event),
                "Claude CLI",
                "Cannot open terminal: " + e.getMessage());
        }
        return null;
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
