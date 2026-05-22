package com.holtek.claudecode.context;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * Utility: resolve current Eclipse project / file context.
 *
 * Priority order for project detection:
 *   1. Active text editor's file → project
 *   2. Package Explorer / Navigator selection → project
 *   3. null (no project open / determinable)
 */
public final class EclipseProjectHelper {

    private EclipseProjectHelper() {}

    // ── Project ────────────────────────────────────────────────────────────────

    /**
     * Returns the filesystem path of the current project (e.g. D:\workspace\myapp),
     * or null if no project can be determined.
     */
    public static String getCurrentProjectPath() {
        IProject p = getCurrentProject();
        if (p == null) return null;
        org.eclipse.core.runtime.IPath loc = p.getLocation();
        return loc != null ? loc.toOSString() : null;
    }

    /** Returns the current IProject, or null. */
    public static IProject getCurrentProject() {
        // 1. Active editor
        IFile f = getActiveEditorFile();
        if (f != null) return f.getProject();
        // 2. Selection
        return getProjectFromSelection();
    }

    // ── File ───────────────────────────────────────────────────────────────────

    /** Returns the IFile open in the active editor, or null. */
    public static IFile getActiveEditorFile() {
        IWorkbenchPage page = getActivePage();
        if (page == null) return null;
        IEditorPart editor = page.getActiveEditor();
        if (editor == null) return null;
        return editor.getEditorInput().getAdapter(IFile.class);
    }

    /**
     * Returns the project-relative path of a file (e.g. "src/com/Foo.java").
     * Uses forward slashes for compatibility with Claude CLI on all platforms.
     */
    public static String getRelativePath(IFile file) {
        return file.getProjectRelativePath().toString();
    }

    /**
     * Returns the absolute OS path of a file.
     */
    public static String getAbsolutePath(IFile file) {
        org.eclipse.core.runtime.IPath loc = file.getLocation();
        return loc != null ? loc.toOSString() : null;
    }

    // ── Selection ──────────────────────────────────────────────────────────────

    /**
     * Returns the first IFile selected in the Package Explorer / Navigator,
     * or null if nothing (or a non-file) is selected.
     */
    public static IFile getSelectedFile() {
        IWorkbenchPage page = getActivePage();
        if (page == null) return null;
        ISelection sel = page.getSelection();
        if (!(sel instanceof IStructuredSelection)) return null;
        Object first = ((IStructuredSelection) sel).getFirstElement();
        if (first instanceof IAdaptable) {
            return ((IAdaptable) first).getAdapter(IFile.class);
        }
        return null;
    }

    /**
     * Returns the first IResource selected in the active view, or null.
     */
    public static IResource getSelectedResource() {
        IWorkbenchPage page = getActivePage();
        if (page == null) return null;
        ISelection sel = page.getSelection();
        if (!(sel instanceof IStructuredSelection)) return null;
        Object first = ((IStructuredSelection) sel).getFirstElement();
        if (first instanceof IAdaptable) {
            return ((IAdaptable) first).getAdapter(IResource.class);
        }
        return null;
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    private static IProject getProjectFromSelection() {
        IResource res = getSelectedResource();
        return res != null ? res.getProject() : null;
    }

    static IWorkbenchPage getActivePage() {
        try {
            IWorkbenchWindow w = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            return w != null ? w.getActivePage() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
