package io.github.airwaves778899.claudecode.handlers;

import java.io.File;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.handlers.HandlerUtil;

import io.github.airwaves778899.claudecode.Activator;
import io.github.airwaves778899.claudecode.terminal.ClaudeConsoleManager;
import io.github.airwaves778899.claudecode.terminal.TerminalLauncher;

/**
 * Opens Claude Code CLI in Windows Terminal at the current project directory.
 *
 * Working directory priority:
 *   1. User preference (Preferences > Claude Code > Working Directory)
 *   2. Currently open editor file → its project root
 *   3. Selected resource in Explorer → its project root
 *   4. First open project in workspace
 *   5. Eclipse workspace root (last resort)
 */
public class OpenClaudeTerminalHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        // ── Resolve CLI path ─────────────────────────────────────────────────
        String cliPref = Activator.getPref(Activator.PREF_CLI_PATH);
        if (cliPref == null || cliPref.isBlank()) cliPref = "claude";
        String claude = ClaudeConsoleManager.resolveCli(cliPref);

        // ── Resolve working directory ────────────────────────────────────────
        String workDir = resolveWorkDir(event);

        // ── Launch terminal ──────────────────────────────────────────────────
        try {
            TerminalLauncher.launch(workDir, claude);
        } catch (Exception e) {
            MessageDialog.openError(
                HandlerUtil.getActiveShell(event),
                "Claude Code",
                "無法開啟終端機。\n\n" + e.getMessage()
                + "\n\n請確認 claude CLI 路徑設定（Preferences > Claude Code）。");
        }
        return null;
    }

    private static String resolveWorkDir(ExecutionEvent event) {
        // Priority 1: user preference
        String pref = Activator.getPref(Activator.PREF_WORK_DIR);
        if (pref != null && !pref.isBlank() && new File(pref).isDirectory()) return pref;

        IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();

        // Priority 2: active editor's project
        IEditorPart editor = page.getActiveEditor();
        if (editor != null) {
            IEditorInput input = editor.getEditorInput();
            IFile iFile = input.getAdapter(IFile.class);
            if (iFile != null) {
                String dir = projectPath(iFile.getProject());
                if (dir != null) return dir;
            }
        }

        // Priority 3: selected resource's project
        ISelection sel = HandlerUtil.getCurrentSelection(event);
        if (sel instanceof IStructuredSelection) {
            Object first = ((IStructuredSelection) sel).getFirstElement();
            if (first instanceof IAdaptable) {
                IResource res = ((IAdaptable) first).getAdapter(IResource.class);
                if (res != null) {
                    String dir = projectPath(res.getProject());
                    if (dir != null) return dir;
                }
            }
        }

        // Priority 4: first open project
        for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (p.isOpen()) {
                String dir = projectPath(p);
                if (dir != null) return dir;
            }
        }

        // Priority 5: workspace root
        return ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString();
    }

    private static String projectPath(IProject project) {
        if (project == null || !project.isOpen()) return null;
        org.eclipse.core.runtime.IPath loc = project.getLocation();
        if (loc == null) return null;
        String path = loc.toOSString();
        return new File(path).isDirectory() ? path : null;
    }
}
