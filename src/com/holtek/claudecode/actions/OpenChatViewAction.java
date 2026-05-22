package com.holtek.claudecode.actions;

import java.io.File;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;

import com.holtek.claudecode.views.ClaudeTerminalView;

/**
 * Right-click action for IProject objects (objectContribution).
 * Opens Claude Chat view and sets working dir to the selected project.
 */
public class OpenChatViewAction implements IObjectActionDelegate {

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

        IWorkbenchPage page = activePart.getSite().getPage();
        try {
            ClaudeTerminalView view = (ClaudeTerminalView) page.showView(ClaudeTerminalView.ID);
            if (view != null && projectPath != null) {
                view.setWorkDir(projectPath);
            }
        } catch (PartInitException e) {
            // ignore — view will still open without workdir change
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
