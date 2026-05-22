package io.github.airwaves778899.claudecode.mcp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.*;

/**
 * Implementations of every MCP tool exposed to Claude Code.
 *
 * Tools:
 *   eclipse_list_projects     — list all open projects
 *   eclipse_workspace_info    — workspace root, project count
 *   eclipse_read_file         — read file content
 *   eclipse_write_file        — write / create file
 *   eclipse_build             — build workspace or specific project
 *   eclipse_get_problems      — errors / warnings from Problems view
 *   eclipse_refresh           — refresh workspace
 *   eclipse_get_active_file   — get current editor file + content
 *   eclipse_run_config        — launch a run configuration by name
 */
public final class McpTools {

    private McpTools() {}

    // ── Tool registry ──────────────────────────────────────────────────────────

    /** Returns the tools/list result JSON */
    public static String toolsList() {
        StringBuilder sb = new StringBuilder("{\"tools\":[");
        boolean first = true;
        for (ToolDef t : TOOLS) {
            if (!first) sb.append(',');
            sb.append(t.toJson());
            first = false;
        }
        sb.append("]}");
        return sb.toString();
    }

    /** Dispatch a tools/call */
    public static String call(String name, String argsJson) {
        try {
            switch (name) {
                case "eclipse_list_projects":  return listProjects();
                case "eclipse_workspace_info": return workspaceInfo();
                case "eclipse_read_file":      return readFile(argsJson);
                case "eclipse_write_file":     return writeFile(argsJson);
                case "eclipse_build":          return build(argsJson);
                case "eclipse_get_problems":   return getProblems(argsJson);
                case "eclipse_refresh":        return refresh();
                case "eclipse_get_active_file":return getActiveFile();
                case "eclipse_run_config":     return runConfig(argsJson);
                default:
                    return McpHandler.errorResult("Unknown tool: " + name);
            }
        } catch (Exception e) {
            return McpHandler.errorResult(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
        }
    }

    // ── Tool definitions ───────────────────────────────────────────────────────

    private static final ToolDef[] TOOLS = {

        new ToolDef("eclipse_list_projects",
            "List all open Eclipse projects with their filesystem paths",
            "{}"),

        new ToolDef("eclipse_workspace_info",
            "Get Eclipse workspace root path and basic info",
            "{}"),

        new ToolDef("eclipse_read_file",
            "Read the content of a file. Accepts absolute path or workspace-relative path (project/path/to/file).",
            "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"File path — absolute or project-relative\"}},\"required\":[\"path\"]}"),

        new ToolDef("eclipse_write_file",
            "Write (create or overwrite) a file. Use absolute path or workspace-relative path.",
            "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"content\":{\"type\":\"string\"}},\"required\":[\"path\",\"content\"]}"),

        new ToolDef("eclipse_build",
            "Build the Eclipse workspace or a specific project. Triggers an incremental build.",
            "{\"type\":\"object\",\"properties\":{\"project\":{\"type\":\"string\",\"description\":\"Project name, or omit to build all\"}}}"),

        new ToolDef("eclipse_get_problems",
            "Get errors and warnings from the Eclipse Problems view",
            "{\"type\":\"object\",\"properties\":{\"severity\":{\"type\":\"string\",\"enum\":[\"error\",\"warning\",\"all\"],\"description\":\"Filter by severity (default: error)\"}}}"),

        new ToolDef("eclipse_refresh",
            "Refresh the Eclipse workspace so changes made by Claude Code are visible",
            "{}"),

        new ToolDef("eclipse_get_active_file",
            "Get the path and content of the file currently open in the Eclipse editor",
            "{}"),

        new ToolDef("eclipse_run_config",
            "Launch an Eclipse run configuration by name",
            "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"description\":\"Run configuration name\"}},\"required\":[\"name\"]}")
    };

    // ── Implementations ────────────────────────────────────────────────────────

    private static String listProjects() {
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        StringBuilder sb = new StringBuilder();
        sb.append("Eclipse Projects (").append(projects.length).append("):\n\n");
        for (IProject p : projects) {
            if (!p.isOpen()) continue;
            IPath loc = p.getLocation();
            String path = loc != null ? loc.toOSString() : "(no location)";
            boolean isGit = new File(path, ".git").exists();
            sb.append("  ").append(p.getName())
              .append(isGit ? " [git]" : "")
              .append("\n    ").append(path).append("\n");
        }
        return McpHandler.textResult(sb.toString());
    }

    private static String workspaceInfo() {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        String wsPath = root.getLocation().toOSString();
        IProject[] projects = root.getProjects();
        long open = Arrays.stream(projects).filter(IProject::isOpen).count();
        String info = "Workspace: " + wsPath + "\n"
                    + "Projects:  " + open + " open / " + projects.length + " total\n";
        return McpHandler.textResult(info);
    }

    private static String readFile(String argsJson) throws IOException {
        String path = McpHandler.extractStr(argsJson, "path");
        if (path == null || path.isBlank()) return McpHandler.errorResult("Missing 'path' argument");

        File file = resolveFile(path);
        if (!file.exists()) return McpHandler.errorResult("File not found: " + file.getAbsolutePath());
        if (!file.isFile())  return McpHandler.errorResult("Not a file: " + file.getAbsolutePath());

        // Limit to 100 KB to keep token count reasonable
        long size = file.length();
        if (size > 100_000) {
            return McpHandler.textResult("[File too large (" + size + " bytes). Reading first 100 KB]\n\n"
                    + readBytes(file, 100_000));
        }
        return McpHandler.textResult(readBytes(file, (int) size));
    }

    private static String writeFile(String argsJson) throws IOException {
        String path    = McpHandler.extractStr(argsJson, "path");
        String content = McpHandler.extractStr(argsJson, "content");
        if (path == null)    return McpHandler.errorResult("Missing 'path' argument");
        if (content == null) return McpHandler.errorResult("Missing 'content' argument");

        File file = resolveFile(path);
        file.getParentFile().mkdirs();
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);

        // Refresh the IFile in Eclipse
        try {
            IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
            IFile iFile = root.getFileForLocation(new org.eclipse.core.runtime.Path(file.getAbsolutePath()));
            if (iFile != null) {
                iFile.refreshLocal(IResource.DEPTH_ZERO, new NullProgressMonitor());
            }
        } catch (CoreException ignored) {}

        return McpHandler.textResult("Written: " + file.getAbsolutePath() + " (" + content.length() + " chars)");
    }

    private static String build(String argsJson) {
        String projectName = McpHandler.extractStr(argsJson, "project");
        try {
            if (projectName != null && !projectName.isBlank()) {
                IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
                if (!project.exists()) return McpHandler.errorResult("Project not found: " + projectName);
                project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor());
                return McpHandler.textResult("Built project: " + projectName);
            } else {
                ResourcesPlugin.getWorkspace().build(
                        IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor());
                return McpHandler.textResult("Workspace build complete");
            }
        } catch (CoreException e) {
            return McpHandler.errorResult("Build failed: " + e.getMessage());
        }
    }

    private static String getProblems(String argsJson) {
        String sev = McpHandler.extractStr(argsJson, "severity");
        int severity = "warning".equalsIgnoreCase(sev) ? IMarker.SEVERITY_WARNING
                     : "all".equalsIgnoreCase(sev)     ? -1
                     : IMarker.SEVERITY_ERROR;          // default: errors only

        try {
            IMarker[] markers = ResourcesPlugin.getWorkspace().getRoot()
                    .findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (IMarker m : markers) {
                int s = (Integer) m.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO);
                if (severity != -1 && s != severity) continue;
                String sevStr = s == IMarker.SEVERITY_ERROR   ? "ERROR"
                              : s == IMarker.SEVERITY_WARNING ? "WARN " : "INFO ";
                String res    = m.getResource() != null ? m.getResource().getFullPath().toString() : "?";
                int    line   = m.getAttribute(IMarker.LINE_NUMBER, 0);
                String msg2   = (String) m.getAttribute(IMarker.MESSAGE);
                sb.append(sevStr).append("  ").append(res)
                  .append(':').append(line).append("  ").append(msg2).append('\n');
                count++;
                if (count >= 50) { sb.append("... (truncated at 50)"); break; }
            }
            if (count == 0) sb.append("No problems found");
            return McpHandler.textResult(sb.toString());
        } catch (CoreException e) {
            return McpHandler.errorResult(e.getMessage());
        }
    }

    private static String refresh() {
        try {
            ResourcesPlugin.getWorkspace().getRoot()
                    .refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
            return McpHandler.textResult("Workspace refreshed");
        } catch (CoreException e) {
            return McpHandler.errorResult(e.getMessage());
        }
    }

    private static String getActiveFile() {
        AtomicReference<String> result = new AtomicReference<>();
        Display.getDefault().syncExec(() -> {
            try {
                IWorkbench wb = PlatformUI.getWorkbench();
                IWorkbenchWindow win = wb.getActiveWorkbenchWindow();
                if (win == null) { result.set(McpHandler.errorResult("No workbench window")); return; }
                IEditorPart editor = win.getActivePage().getActiveEditor();
                if (editor == null) { result.set(McpHandler.textResult("No active editor")); return; }

                IEditorInput input = editor.getEditorInput();
                IFile iFile = input.getAdapter(IFile.class);
                if (iFile == null) { result.set(McpHandler.textResult("Active editor has no file")); return; }

                String path = iFile.getLocation().toOSString();
                String content;
                try {
                    content = readBytes(new File(path), 100_000);
                } catch (IOException e) {
                    content = "(unreadable: " + e.getMessage() + ")";
                }
                result.set(McpHandler.textResult("File: " + path + "\n\n" + content));
            } catch (Exception e) {
                result.set(McpHandler.errorResult(e.getMessage()));
            }
        });
        return result.get();
    }

    private static String runConfig(String argsJson) {
        String name = McpHandler.extractStr(argsJson, "name");
        if (name == null || name.isBlank()) return McpHandler.errorResult("Missing 'name' argument");

        AtomicReference<String> result = new AtomicReference<>();
        Display.getDefault().syncExec(() -> {
            try {
                org.eclipse.debug.core.ILaunchManager mgr = org.eclipse.debug.core.DebugPlugin.getDefault().getLaunchManager();
                org.eclipse.debug.core.ILaunchConfiguration[] configs = mgr.getLaunchConfigurations();
                org.eclipse.debug.core.ILaunchConfiguration match = null;
                for (org.eclipse.debug.core.ILaunchConfiguration c : configs) {
                    if (c.getName().equalsIgnoreCase(name)) { match = c; break; }
                }
                if (match == null) {
                    result.set(McpHandler.errorResult("Run configuration not found: " + name));
                    return;
                }
                match.launch("run", new NullProgressMonitor());
                result.set(McpHandler.textResult("Launched: " + name));
            } catch (Exception e) {
                result.set(McpHandler.errorResult(e.getMessage()));
            }
        });
        return result.get();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Resolve a path: if absolute use as-is, otherwise treat as
     * workspace-relative (project/path/to/file).
     */
    private static File resolveFile(String path) {
        File f = new File(path);
        if (f.isAbsolute()) return f;
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IPath wsRoot = root.getLocation();
        return new File(wsRoot.toOSString(), path);
    }

    private static String readBytes(File file, long maxBytes) throws IOException {
        byte[] bytes = new byte[(int) Math.min(file.length(), maxBytes)];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(bytes);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // ── ToolDef inner class ────────────────────────────────────────────────────

    private static final class ToolDef {
        final String name, description, inputSchema;
        ToolDef(String name, String description, String inputSchema) {
            this.name = name; this.description = description; this.inputSchema = inputSchema;
        }
        String toJson() {
            return "{\"name\":\"" + name + "\","
                 + "\"description\":\"" + McpHandler.esc(description) + "\","
                 + "\"inputSchema\":" + inputSchema + "}";
        }
    }
}
