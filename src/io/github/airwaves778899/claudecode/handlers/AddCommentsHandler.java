package io.github.airwaves778899.claudecode.handlers;

/**
 * "Add Comments" — asks Claude to add Javadoc and inline comments.
 */
public class AddCommentsHandler extends EditorActionHandler {

    @Override
    protected String buildPrompt(String fileName, String ext, String codeBlock) {
        boolean isJava = "java".equalsIgnoreCase(ext);
        String docStyle = isJava ? "Javadoc" : "文件註解";

        return "請為以下程式碼加上完整的 " + docStyle + " 和必要的行內註解。\n" +
               "要求：\n" +
               "- 每個 class 和 public method 都需要 " + docStyle + "\n" +
               "- 複雜邏輯加上行內說明\n" +
               "- 保持原始邏輯與格式完全不變，只增加註解\n" +
               "- 請直接輸出加上註解後的完整程式碼\n\n" +
               codeBlock;
    }
}
