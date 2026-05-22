package io.github.airwaves778899.claudecode.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

import io.github.airwaves778899.claudecode.Activator;
import io.github.airwaves778899.claudecode.context.EclipseProjectHelper;
import io.github.airwaves778899.claudecode.terminal.ClaudeConsoleManager;
import io.github.airwaves778899.claudecode.terminal.TerminalLauncher;

/**
 * Handler for "Open with Claude" in the Package Explorer right-click menu.
 *
 * Gets the selected file's project-relative path and opens Claude Terminal
 * in the project directory with:  claude "<relative-path>"
 *
 * Claude Code CLI will immediately load the file for context.
 */
public class OpenFileWithClaudeHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        // ── 1. Get selected file ─────────────────────────────────────────────
        ISelection sel = HandlerUtil.getCurrentSelection(event);
        IFile file = null;

        if (sel instanceof IStructuredSelection) {
            Object first = ((IStructuredSelection) sel).getFirstElement();
            if (first instanceof org.eclipse.core.runtime.IAdaptable) {
                file = ((org.eclipse.core.runtime.IAdaptable) first).getAdapter(IFile.class);
                if (file == null) {
                    // Might be a folder/project; still use its project
                    IResource res = ((org.eclipse.core.runtime.IAdaptable) first)
                            .getAdapter(IResource.class);
                    if (res != null) {
                        // Just open terminal at project root
                        openAtProject(res.getProject().getLocation().toOSString(), event);
                        return null;
                    }
                }
            }
        }

        if (file == null) {
            // Fallback: use active editor file
            file = EclipseProjectHelper.getActiveEditorFile();
        }

        if (file == null) {
            MessageDialog.openInformation(
                HandlerUtil.getActiveShell(event),
                "Open with Claude",
                "No file selected. Please right-click a file in the Package Explorer.");
            return null;
        }

        // ── 2. Build paths ───────────────────────────────────────────────────
        String projectPath   = file.getProject().getLocation().toOSString();
        String relativePath  = EclipseProjectHelper.getRelativePath(file);   // e.g. src/com/Foo.java

        // ── 3. Launch ────────────────────────────────────────────────────────
        String cliPref = Activator.getPref(Activator.PREF_CLI_PATH);
        if (cliPref == null || cliPref.isBlank()) cliPref = "claude";
        String claude = ClaudeConsoleManager.resolveCli(cliPref);

        try {
            TerminalLauncher.launchWithArg(projectPath, claude, relativePath);
        } catch (Exception e) {
            throw new ExecutionException("Failed to open Claude Terminal", e);
        }
        return null;
    }

    private void openAtProject(String projectPath, ExecutionEvent event)
            throws ExecutionException {
        String cliPref = Activator.getPref(Activator.PREF_CLI_PATH);
        if (cliPref == null || cliPref.isBlank()) cliPref = "claude";
        String claude = ClaudeConsoleManager.resolveCli(cliPref);
        try {
            TerminalLauncher.launch(projectPath, claude);
        } catch (Exception e) {
            throw new ExecutionException("Failed to open Claude Terminal", e);
        }
    }
}
