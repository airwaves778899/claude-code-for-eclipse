package io.github.airwaves778899.claudecode.handlers;

/**
 * "Explain Code" — asks Claude to explain what the selected code does.
 */
public class ExplainCodeHandler extends EditorActionHandler {

    @Override
    protected String buildPrompt(String fileName, String ext, String codeBlock) {
        return "請詳細解釋以下程式碼的功能、邏輯流程與關鍵設計決策。\n" +
               "說明時請涵蓋：\n" +
               "- 整體目的與職責\n" +
               "- 主要邏輯步驟\n" +
               "- 任何值得注意的設計模式或技巧\n\n" +
               codeBlock;
    }
}
