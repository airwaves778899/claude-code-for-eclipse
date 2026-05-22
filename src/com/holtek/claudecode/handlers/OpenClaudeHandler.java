package com.holtek.claudecode.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;

/**
 * Toolbar / Alt+Shift+C shortcut — delegates to OpenClaudeTerminalHandler.
 */
public class OpenClaudeHandler extends AbstractHandler {

    private final IHandler delegate = new OpenClaudeTerminalHandler();

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        return delegate.execute(event);
    }
}
