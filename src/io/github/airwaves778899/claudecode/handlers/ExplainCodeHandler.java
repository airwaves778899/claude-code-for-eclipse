package io.github.airwaves778899.claudecode.handlers;

/**
 * "Explain Code" — asks Claude to explain what the selected code does.
 */
public class ExplainCodeHandler extends EditorActionHandler {

    @Override
    protected String buildPrompt(String fileName, String ext, String codeBlock) {
        return "Please explain in detail the functionality, logic flow and key design decisions of the following code.\n" +
               "Please cover:\n" +
               "- Overall purpose and responsibilities\n" +
               "- Main logic steps\n" +
               "- Any noteworthy design patterns or techniques\n\n" +
               codeBlock;
    }
}
