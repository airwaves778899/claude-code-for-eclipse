package io.github.airwaves778899.claudecode.apply;

/**
 * Represents a single fenced code block extracted from a Claude response.
 *
 * Example markdown:
 *   ```java
 *   public int add(int a, int b) { return a + b; }
 *   ```
 *
 * → language = "java", content = "public int add(int a, int b) { return a + b; }\n"
 */
public class CodeBlock {

    private final String language;   // e.g., "java", "xml", "python", or "text"
    private final String content;    // raw code inside the fence (trailing \n stripped)
    private final int    index;      // position among all blocks in the response (0-based)

    public CodeBlock(String language, String content, int index) {
        this.language = (language == null || language.isBlank()) ? "text" : language.trim();
        this.content  = content != null ? content : "";
        this.index    = index;
    }

    public String getLanguage()  { return language; }
    public String getContent()   { return content; }
    public int    getIndex()     { return index; }

    /** Human-readable label, e.g. "程式碼區塊 1 (java)" */
    public String getLabel() {
        return "程式碼區塊 " + (index + 1) + " (" + language + ")";
    }

    /** True if this block is likely source code (not shell output, plain text, etc.) */
    public boolean isSourceCode() {
        return switch (language.toLowerCase()) {
            case "java", "kotlin", "scala", "groovy",
                 "javascript", "typescript", "python", "go",
                 "rust", "cpp", "c", "cs", "php", "ruby",
                 "swift", "dart", "xml", "json", "yaml",
                 "sql", "html", "css" -> true;
            default -> content.length() > 20;   // heuristic for unlabelled blocks
        };
    }

    @Override
    public String toString() {
        String preview = content.length() > 80 ? content.substring(0, 80) + "…" : content;
        return "[" + language + "] " + preview;
    }
}
