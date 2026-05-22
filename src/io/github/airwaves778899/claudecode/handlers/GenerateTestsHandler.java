package io.github.airwaves778899.claudecode.handlers;

/**
 * "Generate Tests" — asks Claude to write unit tests for the selected code.
 */
public class GenerateTestsHandler extends EditorActionHandler {

    @Override
    protected String buildPrompt(String fileName, String ext, String codeBlock) {
        boolean isJava = "java".equalsIgnoreCase(ext);
        String framework = isJava ? "JUnit 5 + Mockito" : "a suitable test framework for this language";

        return "Please write complete unit tests for the following code using " + framework + ".\n" +
               "Test requirements:\n" +
               "- Cover all public methods\n" +
               "- Include normal cases (happy path)\n" +
               "- Include edge cases (null, empty, extreme values)\n" +
               "- Include exception cases (scenarios that should throw exceptions)\n" +
               "- Use meaningful test method names (@DisplayName)\n" +
               "- Name test class as original class name + Test suffix\n\n" +
               "Please output the test code first, then briefly explain the testing strategy.\n\n" +
               codeBlock;
    }
}
