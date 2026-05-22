package io.github.airwaves778899.claudecode.handlers;

/**
 * "Add Comments" — asks Claude to add Javadoc and inline comments.
 */
public class AddCommentsHandler extends EditorActionHandler {

    @Override
    protected String buildPrompt(String fileName, String ext, String codeBlock) {
        boolean isJava = "java".equalsIgnoreCase(ext);
        String docStyle = isJava ? "Javadoc" : "documentation comments";

        return "Please add complete " + docStyle + " and necessary inline comments to the following code.\n" +
               "Requirements:\n" +
               "- Every class and public method needs " + docStyle + "\n" +
               "- Add inline explanations for complex logic\n" +
               "- Keep the original logic and formatting completely unchanged, only add comments\n" +
               "- Output the complete code with comments added\n\n" +
               codeBlock;
    }
}
