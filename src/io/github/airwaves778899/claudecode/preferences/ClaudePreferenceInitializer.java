package io.github.airwaves778899.claudecode.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import io.github.airwaves778899.claudecode.Activator;

/**
 * Sets default preference values on first launch.
 */
public class ClaudePreferenceInitializer extends AbstractPreferenceInitializer {

    @Override
    public void initializeDefaultPreferences() {
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        store.setDefault(Activator.PREF_CLI_PATH,            "");     // auto-detect
        store.setDefault(Activator.PREF_MODEL,               Activator.DEFAULT_MODEL);
        store.setDefault(Activator.PREF_WORK_DIR,            "");     // auto-detect from project
        store.setDefault(Activator.PREF_AUTO_SWITCH_WORKDIR, true);   // on by default
        store.setDefault(Activator.PREF_AUTO_PERMISSIONS,    false);  // ask before write
        store.setDefault(Activator.PREF_INCLUDE_ACTIVE_FILE, true);   // include open file
    }
}
