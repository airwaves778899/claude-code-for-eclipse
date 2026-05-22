package com.holtek.claudecode.context;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * Claude Code for Eclipse - Project Context Provider (Phase 5)
 *
 * Scans the active Eclipse project and builds a structured summary
 * that can be prepended to Claude prompts for project-wide awareness.
 *
 * Summary includes:
 *   - Project name and build tool (Maven / Gradle)
 *   - Key dependencies (from pom.xml / build.gradle)
 *   - Source package tree with class counts
 *   - Config files present (application.yml, logback.xml, etc.)
 */
public final class ProjectContextProvider {

    /** Max Java files to enumerate individually; larger projects show package summaries. */
    private static final int MAX_FILE_LIST = 60;

    /** Folders to skip during scanning. */
    private static final String[] SKIP_FOLDERS =
        { "target", "bin", ".settings", ".git", "node_modules", ".mvn", "build" };

    private ProjectContextProvider() {}

    // ── Project access ────────────────────────────────────────────────────────

    /**
     * Returns the project associated with the currently active editor,
     * or the first open project in the workspace as fallback.
     */
    public static IProject getActiveProject() {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window != null) {
            IWorkbenchPage page = window.getActivePage();
            if (page != null) {
                IEditorPart editor = page.getActiveEditor();
                if (editor != null) {
                    IEditorInput input = editor.getEditorInput();
                    IFile file = input.getAdapter(IFile.class);
                    if (file != null) return file.getProject();
                }
            }
        }
        // Fallback: first open project
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        for (IProject p : projects) {
            if (p.isOpen()) return p;
        }
        return null;
    }

    // ── Summary builder ───────────────────────────────────────────────────────

    /**
     * Build a compact project summary string suitable for inclusion in Claude prompts.
     * Typically 200–500 tokens.
     *
     * @param project  the Eclipse project to summarise
     * @return formatted summary text
     */
    public static String buildProjectSummary(IProject project) {
        if (project == null || !project.isOpen()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## 專案概覽：").append(project.getName()).append("\n\n");

        // ── Build tool & dependencies ─────────────────────────────────────────
        try {
            IFile pom = project.getFile("pom.xml");
            if (pom.exists()) {
                sb.append(parseMaven(pom));
            } else {
                IFile gradle = project.getFile("build.gradle");
                if (!gradle.exists()) gradle = project.getFile("build.gradle.kts");
                if (gradle.exists()) sb.append(parseGradle(gradle));
            }
        } catch (Exception ignored) {}

        // ── Source structure ──────────────────────────────────────────────────
        try {
            sb.append(buildSourceTree(project));
        } catch (CoreException ignored) {}

        // ── Config files ──────────────────────────────────────────────────────
        List<String> configs = findConfigFiles(project);
        if (!configs.isEmpty()) {
            sb.append("\n**設定檔：** ").append(String.join(", ", configs)).append("\n");
        }

        return sb.toString();
    }

    // ── Maven parser ──────────────────────────────────────────────────────────

    private static String parseMaven(IFile pom) {
        String content = readFile(pom);
        if (content == null) return "";
        StringBuilder sb = new StringBuilder("**建置工具：** Maven\n");

        // Java version
        String javaVer = extractXmlValue(content, "java.version");
        if (javaVer == null) javaVer = extractXmlValue(content, "maven.compiler.source");
        if (javaVer != null) sb.append("**Java：** ").append(javaVer).append("\n");

        // Spring Boot version
        Pattern sbPat = Pattern.compile(
            "<parent>[\\s\\S]*?<artifactId>spring-boot-starter-parent</artifactId>" +
            "[\\s\\S]*?<version>([^<]+)</version>", Pattern.MULTILINE);
        Matcher m = sbPat.matcher(content);
        if (m.find()) sb.append("**Spring Boot：** ").append(m.group(1)).append("\n");

        // Key dependencies
        List<String> deps = extractMavenDeps(content);
        if (!deps.isEmpty()) {
            sb.append("**主要依賴：** ").append(String.join(", ", deps)).append("\n");
        }
        return sb.toString();
    }

    private static List<String> extractMavenDeps(String pom) {
        String[] interesting = {
            "spring-boot-starter-web", "spring-boot-starter-data-jpa",
            "spring-boot-starter-security", "spring-boot-starter-test",
            "lombok", "mapstruct", "jackson", "flyway", "liquibase",
            "postgresql", "mysql", "h2", "kafka", "rabbitmq",
            "junit-jupiter", "mockito", "hibernate"
        };
        List<String> found = new ArrayList<>();
        for (String dep : interesting) {
            if (pom.contains(dep)) found.add(dep);
        }
        return found;
    }

    // ── Gradle parser ─────────────────────────────────────────────────────────

    private static String parseGradle(IFile gradle) {
        String content = readFile(gradle);
        if (content == null) return "";
        StringBuilder sb = new StringBuilder("**建置工具：** Gradle\n");
        // Look for java version
        Matcher m = Pattern.compile("sourceCompatibility\\s*=\\s*['\"]?([\\d.]+)").matcher(content);
        if (m.find()) sb.append("**Java：** ").append(m.group(1)).append("\n");
        return sb.toString();
    }

    // ── Source tree ───────────────────────────────────────────────────────────

    private static String buildSourceTree(IProject project) throws CoreException {
        // Try Maven layout first
        IFolder src = project.getFolder("src/main/java");
        if (!src.exists()) src = project.getFolder("src");
        if (!src.exists()) return "";

        StringBuilder sb = new StringBuilder("\n**原始碼結構：**\n");

        // Collect packages → file count
        Map<String, Integer> packages = new LinkedHashMap<>();
        List<String> allFiles = new ArrayList<>();

        src.accept((IResourceVisitor) resource -> {
            if (resource instanceof IFolder) {
                String name = ((IFolder) resource).getName();
                for (String skip : SKIP_FOLDERS) {
                    if (skip.equalsIgnoreCase(name)) return false;
                }
                return true;
            }
            if (resource instanceof IFile) {
                IFile f = (IFile) resource;
                if ("java".equalsIgnoreCase(f.getFileExtension())) {
                    String pkg = f.getParent().getProjectRelativePath().toString();
                    packages.merge(pkg, 1, Integer::sum);
                    allFiles.add(f.getName().replace(".java", ""));
                }
            }
            return true;
        });

        int totalFiles = allFiles.size();
        sb.append("  총 ").append(totalFiles).append(" 個 .java 檔案\n");

        if (totalFiles <= MAX_FILE_LIST) {
            for (Map.Entry<String, Integer> entry : packages.entrySet()) {
                String pkg = entry.getKey()
                        .replace("src/main/java/", "")
                        .replace("src/", "")
                        .replace("/", ".");
                sb.append("  ").append(pkg)
                  .append("  (").append(entry.getValue()).append(" 個類別)\n");
            }
        } else {
            // Too many — just show top-level packages
            sb.append("  主要套件：\n");
            packages.entrySet().stream()
                    .limit(15)
                    .forEach(e -> {
                        String pkg = e.getKey()
                                .replace("src/main/java/", "")
                                .replace("src/", "")
                                .replace("/", ".");
                        sb.append("    ").append(pkg)
                          .append(" (").append(e.getValue()).append(")\n");
                    });
        }
        return sb.toString();
    }

    // ── Config file finder ────────────────────────────────────────────────────

    private static List<String> findConfigFiles(IProject project) {
        String[] candidates = {
            "src/main/resources/application.yml",
            "src/main/resources/application.properties",
            "src/main/resources/application-dev.yml",
            "src/main/resources/logback.xml",
            "src/main/resources/logback-spring.xml",
            "src/main/resources/META-INF/persistence.xml",
            "pom.xml", "build.gradle", "build.gradle.kts",
            "docker-compose.yml", "Dockerfile", ".env"
        };
        List<String> found = new ArrayList<>();
        for (String path : candidates) {
            IFile f = project.getFile(path);
            if (f.exists()) {
                found.add(f.getName());
            }
        }
        return found;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static String readFile(IFile file) {
        try (InputStream is = file.getContents()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractXmlValue(String xml, String tag) {
        Pattern p = Pattern.compile("<" + tag + ">([^<]+)</" + tag + ">");
        Matcher m = p.matcher(xml);
        return m.find() ? m.group(1).trim() : null;
    }

    /**
     * Read a specific file from the project and return its content.
     * Returns empty string if not found or unreadable.
     *
     * @param project   the Eclipse project
     * @param relPath   path relative to project root (e.g., "pom.xml")
     * @param maxBytes  maximum characters to return
     */
    public static String readProjectFile(IProject project, String relPath, int maxBytes) {
        if (project == null) return "";
        IFile file = project.getFile(relPath);
        if (!file.exists()) return "";
        String content = readFile(file);
        if (content == null) return "";
        return content.length() > maxBytes
                ? content.substring(0, maxBytes) + "\n// ... [截斷]"
                : content;
    }
}
