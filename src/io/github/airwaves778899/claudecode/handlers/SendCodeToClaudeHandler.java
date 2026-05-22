package io.github.airwaves778899.claudecode.handlers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

import io.github.airwaves778899.claudecode.Activator;
import io.github.airwaves778899.claudecode.context.EclipseProjectHelper;
import io.github.airwaves778899.claudecode.terminal.ClaudeConsoleManager;
import io.github.airwaves778899.claudecode.terminal.TerminalLauncher;

/**
 * Handler for "Send Code to Claude" (Alt+Shift+K).
 *
 * Flow:
 *   1. Get the selected text from the active editor
 *   2. Write it to <project-root>/.eclipse_selection.tmp
 *   3. Open Claude Terminal in the project directory
 *   4. An introductory cmd message tells the user the code is in the temp file
 *
 * Once the terminal opens, the user can tell Claude:
 *   "Please review .eclipse_selection.tmp"
 *   or drag the file into the Claude prompt.
 */
public class SendCodeToClaudeHandler extends AbstractHandler {

    private static final String TEMP_FILE = ".eclipse_selection.tmp";

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {

        // ── 1. Get selected text ─────────────────────────────────────────────
        IEditorPart editor = HandlerUtil.getActiveEditor(event);
        if (!(editor instanceof ITextEditor)) {
            MessageDialog.openInformation(
                HandlerUtil.getActiveShell(event),
                "Send Code to Claude",
                "Please open a text editor and select some code first.");
            return null;
        }

        ITextEditor textEditor = (ITextEditor) editor;
        ISelection rawSel = textEditor.getSelectionProvider().getSelection();

        String selectedCode = "";
        String fileName     = "";

        if (rawSel instanceof ITextSelection) {
            selectedCode = ((ITextSelection) rawSel).getText();
        }

        IFile editorFile = EclipseProjectHelper.getActiveEditorFile();
        if (editorFile != null) {
            fileName = editorFile.getName();
        }

        if (selectedCode == null || selectedCode.isBlank()) {
            // No selection → use entire file content
            if (editorFile != null) {
                selectedCode = readFileContent(editorFile);
                fileName = editorFile.getName() + " (entire file)";
            }
        }

        if (selectedCode == null || selectedCode.isBlank()) {
            MessageDialog.openInformation(
                HandlerUtil.getActiveShell(event),
                "Send Code to Claude",
                "No code selected and no file open.\nPlease select code in the editor first.");
            return null;
        }

        // ── 2. Resolve project path ──────────────────────────────────────────
        IProject project = EclipseProjectHelper.getCurrentProject();
        String projectPath = (project != null && project.getLocation() != null)
                ? project.getLocation().toOSString()
                : System.getProperty("user.home");

        // ── 3. Write to temp file ────────────────────────────────────────────
        File tmpFile = new File(projectPath, TEMP_FILE);
        try (OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(tmpFile), StandardCharsets.UTF_8)) {
            w.write("// Eclipse selection from: " + fileName + "\n");
            w.write("// -------------------------------------------------\n");
            w.write(selectedCode);
        } catch (Exception e) {
            throw new ExecutionException("Cannot write temp file: " + e.getMessage(), e);
        }

        // ── 4. Launch terminal with context message ──────────────────────────
        String cliPref = Activator.getPref(Activator.PREF_CLI_PATH);
        if (cliPref == null || cliPref.isBlank()) cliPref = "claude";
        String claude = ClaudeConsoleManager.resolveCli(cliPref);

        // Build a cmd command that shows a header before starting claude:
        //   echo [Eclipse] Code from Foo.java saved to .eclipse_selection.tmp
        //   echo You can tell Claude: "review .eclipse_selection.tmp"
        //   claude
        String header =
            "echo [Eclipse] Code from \"" + fileName + "\" saved to " + TEMP_FILE + " && " +
            "echo Tell Claude: review " + TEMP_FILE + " && " +
            "echo. && " +
            "\"" + claude + "\"";

        try {
            String wt = TerminalLauncher.findWindowsTerminal();
            if (wt != null) {
                new ProcessBuilder(wt, "new-tab", "-d", projectPath,
                        "--", "cmd.exe", "/k", header)
                        .start();
            } else {
                String inner = "cd /d \"" + projectPath + "\" && " + header;
                new ProcessBuilder("cmd.exe", "/c", "start", "cmd.exe", "/k", inner).start();
            }
        } catch (Exception e) {
            throw new ExecutionException("Failed to launch Claude Terminal", e);
        }

        return null;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static String readFileContent(IFile file) {
        try (java.io.InputStream in = file.getContents()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }
}
