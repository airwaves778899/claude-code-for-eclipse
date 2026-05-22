package io.github.airwaves778899.claudecode.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Handler for "Refresh Workspace" (Alt+Shift+U).
 *
 * Triggers a full workspace refresh so Eclipse picks up any files
 * that Claude Code CLI created or modified externally.
 *
 * Runs as a background Job to avoid blocking the UI thread.
 */
public class RefreshWorkspaceHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Job job = new Job("Refreshing workspace after Claude...") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    ResourcesPlugin.getWorkspace().getRoot()
                            .refreshLocal(IResource.DEPTH_INFINITE, monitor);
                    return Status.OK_STATUS;
                } catch (Exception e) {
                    return new Status(IStatus.ERROR, "io.github.airwaves778899.claudecode",
                            "Workspace refresh failed: " + e.getMessage(), e);
                }
            }
        };
        job.setUser(true);   // shows progress in the status bar
        job.schedule();
        return null;
    }
}
