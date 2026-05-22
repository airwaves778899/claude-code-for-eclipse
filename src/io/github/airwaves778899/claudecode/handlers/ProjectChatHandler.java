package io.github.airwaves778899.claudecode.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

import io.github.airwaves778899.claudecode.context.ProjectContextProvider;
import io.github.airwaves778899.claudecode.context.ProblemsHelper;
import io.github.airwaves778899.claudecode.views.ClaudeView;

/**
 * "Project Chat" — asks Claude a question with full project context automatically included.
 *
 * Trigger: Alt+Shift+J, or Claude menu → Ask about Project
 *
 * Flow:
 *   1. Detect active project, build project summary
 *   2. Show input dialog with suggested questions
 *   3. Send: [project summary] + [user question] to ClaudeView
 */
public class ProjectChatHandler extends AbstractHandler {

    /** Default question shown in the dialog as a placeholder. */
    private static final String DEFAULT_QUESTION =
        "請分析這個專案的架構，指出潛在的問題或改善建議";

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {

        // ── 1. Get project ────────────────────────────────────────────────────
        IProject project = ProjectContextProvider.getActiveProject();
        if (project == null) {
            MessageDialog.openInformation(
                HandlerUtil.getActiveShell(event),
                "Project Chat",
                "找不到活動中的 Eclipse 專案。\n請先在編輯器中開啟一個 Java 檔案。");
            return null;
        }

        // ── 2. Build project summary ──────────────────────────────────────────
        String summary = ProjectContextProvider.buildProjectSummary(project);

        // Add problems info if there are errors
        String problemsInfo = "";
        if (ProblemsHelper.hasErrors(project)) {
            problemsInfo = "\n**目前狀態：** " + ProblemsHelper.getSummary(project) + "\n";
        }

        // ── 3. Show input dialog ──────────────────────────────────────────────
        String dialogMessage =
            "已讀取專案「" + project.getName() + "」的結構資訊。\n\n" +
            "請輸入您想問的問題：";

        IInputValidator validator = input ->
            (input == null || input.isBlank()) ? "問題不能為空" : null;

        InputDialog dialog = new InputDialog(
            HandlerUtil.getActiveShell(event),
            "Ask about Project",
            dialogMessage,
            DEFAULT_QUESTION,
            validator);

        // Increase dialog width
        dialog.setBlockOnOpen(true);
        if (dialog.open() != Window.OK) return null;

        String question = dialog.getValue().trim();
        if (question.isEmpty()) return null;

        // ── 4. Build final prompt ─────────────────────────────────────────────
        StringBuilder prompt = new StringBuilder();
        prompt.append(summary);
        if (!problemsInfo.isEmpty()) prompt.append(problemsInfo);
        prompt.append("\n---\n\n");
        prompt.append(question);

        // ── 5. Send to ClaudeView ─────────────────────────────────────────────
        IWorkbenchPage page =
            HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
        try {
            ClaudeView view = (ClaudeView) page.showView(ClaudeView.ID);
            view.sendWithContext(prompt.toString(), true);
        } catch (PartInitException e) {
            throw new ExecutionException("無法開啟 Claude View", e);
        }

        return null;
    }
}
