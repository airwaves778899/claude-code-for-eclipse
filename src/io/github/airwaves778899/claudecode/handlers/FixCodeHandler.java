package io.github.airwaves778899.claudecode.handlers;

/**
 * "Fix / Improve Code" — asks Claude to find bugs and suggest improvements.
 */
public class FixCodeHandler extends EditorActionHandler {

    @Override
    protected String buildPrompt(String fileName, String ext, String codeBlock) {
        return "請審查以下程式碼，找出潛在問題並提供修正後的版本。\n" +
               "請檢查：\n" +
               "- Bug 與邏輯錯誤\n" +
               "- 效能問題\n" +
               "- 例外處理是否完整\n" +
               "- 程式碼可讀性與命名\n" +
               "- 安全性疑慮（若有）\n\n" +
               "請先說明發現的問題，再提供修正後的完整程式碼。\n\n" +
               codeBlock;
    }
}
