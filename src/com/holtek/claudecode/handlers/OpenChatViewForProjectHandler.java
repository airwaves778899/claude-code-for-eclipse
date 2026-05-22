package com.holtek.claudecode.handlers;

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
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

import com.holtek.claudecode.views.ClaudeTerminalView;

/**
 * Right-click handler for Package Explorer / Project Explorer.
 *
 * Opens the Claude Chat view and switches its working directory
 * to the selected project's filesystem root.
 */
public class OpenChatViewForProjectHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {

        // ── Resolve selected project ─────────────────────────────────────────
        String projectPath = resolveProjectPath(event);

        // ── Open (or focus) Claude Chat view ────────────────────────────────
        IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
        ClaudeTerminalView view = null;
        try {
            view = (ClaudeTerminalView) page.showView(ClaudeTerminalView.ID);
        } catch (PartInitException e) {
            MessageDialog.openError(
                HandlerUtil.getActiveShell(event),
                "Claude Chat",
                "無法開啟 Claude Chat view。\n\n" + e.getMessage());
            return null;
        }

        // ── Switch working directory ─────────────────────────────────────────
        if (view != null && projectPath != null) {
            view.setWorkDir(projectPath);
        }

        return null;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String resolveProjectPath(ExecutionEvent event) {
        ISelection sel = HandlerUtil.getCurrentSelection(event);
        if (!(sel instanceof IStructuredSelection)) return null;

        Object first = ((IStructuredSelection) sel).getFirstElement();
        if (!(first instanceof IAdaptable)) return null;

        IResource res = ((IAdaptable) first).getAdapter(IResource.class);
        if (res == null) return null;

        IProject project = res.getProject();
        if (project == null || !project.isOpen()) return null;

        org.eclipse.core.runtime.IPath loc = project.getLocation();
        if (loc == null) return null;

        String path = loc.toOSString();
        return new File(path).isDirectory() ? path : null;
    }
}
