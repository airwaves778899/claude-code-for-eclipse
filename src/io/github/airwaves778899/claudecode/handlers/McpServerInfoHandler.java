package io.github.airwaves778899.claudecode.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import io.github.airwaves778899.claudecode.Activator;
import io.github.airwaves778899.claudecode.mcp.McpServer;

/**
 * Shows the MCP server connection info dialog.
 *
 * Displays the port, server status, and the exact claude CLI command
 * the user needs to run to connect Claude Code to this Eclipse instance.
 */
public class McpServerInfoHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Shell shell = HandlerUtil.getActiveShell(event);

        McpServer srv = Activator.getDefault().getMcpServer();
        boolean running = srv != null && srv.isRunning();
        int port = srv != null ? srv.getPort() : McpServer.DEFAULT_PORT;

        String url     = "http://localhost:" + port + "/mcp";
        String cliCmd  = "claude mcp add eclipse --transport http " + url;

        String status = running ? "Running on port " + port : "NOT running (start failed)";

        String msg = "Eclipse MCP Server\n"
                   + "══════════════════════════════════════\n\n"
                   + "Status : " + status + "\n"
                   + "URL    : " + url + "\n\n"
                   + "To connect Claude Code, run once in any terminal:\n\n"
                   + "  " + cliCmd + "\n\n"
                   + "Then use 'claude' normally — it will have access\n"
                   + "to your Eclipse workspace via MCP tools:\n\n"
                   + "  eclipse_list_projects\n"
                   + "  eclipse_read_file / eclipse_write_file\n"
                   + "  eclipse_build / eclipse_get_problems\n"
                   + "  eclipse_get_active_file\n"
                   + "  eclipse_run_config";

        String[] buttons = running
                ? new String[]{"Copy CLI Command", "Close"}
                : new String[]{"Close"};

        MessageDialog dlg = new MessageDialog(
                shell,
                "MCP Server Info",
                null,
                msg,
                running ? MessageDialog.INFORMATION : MessageDialog.WARNING,
                buttons,
                0);

        int result = dlg.open();

        // Copy button (index 0 when running)
        if (running && result == 0) {
            Clipboard cb = new Clipboard(Display.getDefault());
            cb.setContents(new Object[]{cliCmd}, new Transfer[]{TextTransfer.getInstance()});
            cb.dispose();
        }

        return null;
    }
}
