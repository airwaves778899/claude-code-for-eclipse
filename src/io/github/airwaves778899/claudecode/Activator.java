package io.github.airwaves778899.claudecode;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import io.github.airwaves778899.claudecode.mcp.McpServer;

/**
 * Claude Code for Eclipse - Plugin Activator
 *
 * Controls the plug-in life cycle. Provides a singleton instance
 * accessible throughout the plug-in via Activator.getDefault().
 */
public class Activator extends AbstractUIPlugin {

    /** The plug-in ID (must match Bundle-SymbolicName in MANIFEST.MF) */
    public static final String PLUGIN_ID = "io.github.airwaves778899.claudecode";

    /** Preference key for the Claude Code CLI executable path (e.g. "claude" or full path) */
    public static final String PREF_CLI_PATH = "claude.cli.path";

    /** Preference key for the Claude model selection */
    public static final String PREF_MODEL = "claude.model";

    /** Preference key for Claude Terminal default working directory */
    public static final String PREF_WORK_DIR = "claude.workdir";

    /** Preference key: auto-switch workdir when editor tab changes */
    public static final String PREF_AUTO_SWITCH_WORKDIR = "claude.autoSwitchWorkdir";

    /** Preference key: auto-allow all file operations without asking */
    public static final String PREF_AUTO_PERMISSIONS = "claude.autoPermissions";

    /** Preference key: include currently active file in context */
    public static final String PREF_INCLUDE_ACTIVE_FILE = "claude.includeActiveFile";

    /** Default model */
    public static final String DEFAULT_MODEL = "claude-sonnet-4-5";

    /** Singleton instance */
    private static Activator plugin;

    /** Embedded MCP HTTP server */
    private McpServer mcpServer;

    public Activator() {
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
        enableNativeRefresh();
        startMcpServer();
    }

    private void startMcpServer() {
        mcpServer = new McpServer();
        try {
            mcpServer.start();
        } catch (Exception e) {
            System.err.println("[Claude Code] MCP Server failed to start: " + e.getMessage());
        }
    }

    /** Returns the running MCP server (may be null if start failed). */
    public McpServer getMcpServer() {
        return mcpServer;
    }

    /**
     * Enable "Refresh using native hooks or polling" so Eclipse automatically
     * detects file changes made by Claude Code CLI externally.
     * Equivalent to: Window > Preferences > General > Workspace > Refresh settings.
     */
    private static void enableNativeRefresh() {
        try {
            IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(
                    ResourcesPlugin.PI_RESOURCES);
            // "refresh.enabled" = true enables native filesystem event hooks
            prefs.putBoolean("refresh.enabled", true);
            prefs.flush();
        } catch (Exception ignored) {
            // Non-critical: user can still manually refresh with Alt+Shift+U
        }
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (mcpServer != null) {
            mcpServer.stop();
            mcpServer = null;
        }
        plugin = null;
        super.stop(context);
    }

    /**
     * Returns the shared instance (singleton).
     */
    public static Activator getDefault() {
        return plugin;
    }

    /**
     * Convenience: read a preference string.
     */
    public static String getPref(String key) {
        return getDefault().getPreferenceStore().getString(key);
    }
}
