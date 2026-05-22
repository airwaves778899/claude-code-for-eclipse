package io.github.airwaves778899.claudecode.apply;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses markdown-fenced code blocks from a Claude response string.
 *
 * Handles:
 *   ```java\n...\n```          — labelled block
 *   ```\n...\n```              — unlabelled block
 *   ` ``` java\n...\n``` `     — with optional spaces after backticks
 */
public final class CodeBlockParser {

    /**
     * Matches:  ```[language]\n[content]```
     * Group 1 = language (may be empty)
     * Group 2 = code content
     */
    private static final Pattern FENCE_PATTERN = Pattern.compile(
            "```[ \\t]*(\\w*)[ \\t]*\\r?\\n([\\s\\S]*?)```",
            Pattern.MULTILINE);

    private CodeBlockParser() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Parse all fenced code blocks from the given text in order of appearance.
     */
    public static List<CodeBlock> parse(String text) {
        List<CodeBlock> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;

        Matcher m = FENCE_PATTERN.matcher(text);
        int idx = 0;
        while (m.find()) {
            String lang    = m.group(1);
            String content = m.group(2);
            // Strip single trailing newline that the closing ``` ate
            if (content.endsWith("\n")) {
                content = content.substring(0, content.length() - 1);
            }
            result.add(new CodeBlock(lang, content, idx++));
        }
        return result;
    }

    /** Returns true if the text contains at least one fenced code block. */
    public static boolean hasCodeBlocks(String text) {
        if (text == null) return false;
        return FENCE_PATTERN.matcher(text).find();
    }

    /**
     * Returns the "best" code block to apply:
     *   1. Prefer the largest block with a known source-code language.
     *   2. Fall back to the largest block overall.
     *   3. Returns null if there are no blocks.
     */
    public static CodeBlock getBestBlock(String text) {
        List<CodeBlock> blocks = parse(text);
        if (blocks.isEmpty()) return null;

        // Prefer source-code labelled blocks, largest first
        return blocks.stream()
                .filter(CodeBlock::isSourceCode)
                .max(Comparator.comparingInt(b -> b.getContent().length()))
                .orElseGet(() ->
                    blocks.stream()
                          .max(Comparator.comparingInt(b -> b.getContent().length()))
                          .orElse(null));
    }

    /**
     * Returns only blocks that are likely source code (filtered by language label).
     */
    public static List<CodeBlock> getSourceBlocks(String text) {
        List<CodeBlock> all = parse(text);
        List<CodeBlock> src = new ArrayList<>();
        for (CodeBlock b : all) {
            if (b.isSourceCode()) src.add(b);
        }
        return src;
    }
}
