package io.github.airwaves778899.claudecode.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

/**
 * Claude Code for Eclipse - Problems View Helper (Phase 5)
 *
 * Reads Eclipse IMarker problems (compile errors, warnings) from the active project
 * and formats them into a prompt-friendly list for Claude to diagnose and fix.
 */
public final class ProblemsHelper {

    /** Max problems to include in a single prompt. */
    public static final int MAX_PROBLEMS = 30;

    private ProblemsHelper() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns all compile errors in the given project as a list of formatted strings.
     * Each string is: "path/File.java:line  — error message"
     *
     * @param project  the project to check
     * @return list of error descriptions (empty if none)
     */
    public static List<String> getErrors(IProject project) throws CoreException {
        return getProblems(project, IMarker.SEVERITY_ERROR);
    }

    /**
     * Returns all warnings in the given project.
     */
    public static List<String> getWarnings(IProject project) throws CoreException {
        return getProblems(project, IMarker.SEVERITY_WARNING);
    }

    /**
     * Returns a combined count summary: "3 errors, 5 warnings".
     */
    public static String getSummary(IProject project) {
        try {
            int errors   = getErrors(project).size();
            int warnings = getWarnings(project).size();
            if (errors == 0 && warnings == 0) return "無編譯問題";
            List<String> parts = new ArrayList<>();
            if (errors   > 0) parts.add(errors   + " 個錯誤");
            if (warnings > 0) parts.add(warnings + " 個警告");
            return String.join("，", parts);
        } catch (CoreException e) {
            return "無法讀取問題列表";
        }
    }

    /**
     * Build a formatted prompt section listing all compile errors.
     * Returns empty string if there are no errors.
     */
    public static String buildErrorPrompt(IProject project) throws CoreException {
        List<String> errors = getErrors(project);
        if (errors.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("**Eclipse 編譯錯誤 (").append(errors.size()).append(" 個)：**\n");
        sb.append("```\n");
        int shown = Math.min(errors.size(), MAX_PROBLEMS);
        for (int i = 0; i < shown; i++) {
            sb.append(errors.get(i)).append("\n");
        }
        if (errors.size() > MAX_PROBLEMS) {
            sb.append("... 以及另外 ").append(errors.size() - MAX_PROBLEMS)
              .append(" 個錯誤（已省略）\n");
        }
        sb.append("```\n");
        return sb.toString();
    }

    /**
     * True if the project has at least one compile error.
     */
    public static boolean hasErrors(IProject project) {
        try {
            return !getErrors(project).isEmpty();
        } catch (CoreException e) {
            return false;
        }
    }

    // ── Private implementation ────────────────────────────────────────────────

    private static List<String> getProblems(IProject project, int severity)
            throws CoreException {
        if (project == null || !project.isOpen()) return List.of();

        IMarker[] markers = project.findMarkers(
            IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);

        List<ProblemEntry> entries = new ArrayList<>();
        for (IMarker marker : markers) {
            Object sev = marker.getAttribute(IMarker.SEVERITY);
            if (sev instanceof Integer && (Integer) sev == severity) {
                String message = (String) marker.getAttribute(IMarker.MESSAGE);
                String path    = marker.getResource()
                                       .getProjectRelativePath().toString();
                int    line    = marker.getAttribute(IMarker.LINE_NUMBER, 0);
                entries.add(new ProblemEntry(path, line, message));
            }
        }

        // Sort by file then line number for readability
        entries.sort(Comparator.comparing((ProblemEntry e) -> e.path)
                               .thenComparingInt(e -> e.line));

        List<String> result = new ArrayList<>();
        for (ProblemEntry e : entries) {
            result.add(e.path + ":" + e.line + "  —  " + e.message);
        }
        return result;
    }

    // ── Inner classes ─────────────────────────────────────────────────────────

    private static class ProblemEntry {
        final String path;
        final int    line;
        final String message;

        ProblemEntry(String path, int line, String message) {
            this.path    = path;
            this.line    = line;
            this.message = message != null ? message : "(no message)";
        }
    }
}
