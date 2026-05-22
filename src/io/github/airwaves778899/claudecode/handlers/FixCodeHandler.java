package io.github.airwaves778899.claudecode.handlers;

/**
 * "Fix / Improve Code" — asks Claude to find bugs and suggest improvements.
 */
public class FixCodeHandler extends EditorActionHandler {

    @Override
    protected String buildPrompt(String fileName, String ext, String codeBlock) {
        return "Please review the following code, identify potential issues and provide a fixed version.\n" +
               "Please check:\n" +
               "- Bugs and logic errors\n" +
               "- Performance issues\n" +
               "- Exception handling completeness\n" +
               "- Code readability and naming\n" +
               "- Security concerns (if any)\n\n" +
               "Please explain the issues found first, then provide the complete fixed code.\n\n" +
               codeBlock;
    }
}
