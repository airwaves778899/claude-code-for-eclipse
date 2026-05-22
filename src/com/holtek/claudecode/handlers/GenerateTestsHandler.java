package com.holtek.claudecode.handlers;

/**
 * "Generate Tests" — asks Claude to write unit tests for the selected code.
 */
public class GenerateTestsHandler extends EditorActionHandler {

    @Override
    protected String buildPrompt(String fileName, String ext, String codeBlock) {
        boolean isJava = "java".equalsIgnoreCase(ext);
        String framework = isJava ? "JUnit 5 + Mockito" : "適合此語言的測試框架";

        return "請為以下程式碼使用 " + framework + " 撰寫完整的單元測試。\n" +
               "測試要求：\n" +
               "- 涵蓋所有 public method\n" +
               "- 包含正常情況（happy path）\n" +
               "- 包含邊界條件（null、空值、極大值）\n" +
               "- 包含例外情況（應拋出例外的場景）\n" +
               "- 使用有意義的測試方法名稱（@DisplayName）\n" +
               "- 測試類別命名為原類別名稱加上 Test 後綴\n\n" +
               "請先輸出測試程式碼，再簡短說明測試策略。\n\n" +
               codeBlock;
    }
}
