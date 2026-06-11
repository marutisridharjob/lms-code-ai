package com.lmscode.ai.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import com.lmscode.ai.Activator;

public class PreferenceInitializer extends AbstractPreferenceInitializer {

	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		store.setDefault(PreferenceConstants.P_PROVIDER, PreferenceConstants.PROVIDER_OPENAI);
		store.setDefault(PreferenceConstants.P_HOST, "localhost"); //$NON-NLS-1$
		store.setDefault(PreferenceConstants.P_PORT, 1234);
		store.setDefault(PreferenceConstants.P_API_KEY, ""); //$NON-NLS-1$
		store.setDefault(PreferenceConstants.P_MODEL, ""); //$NON-NLS-1$
		store.setDefault(PreferenceConstants.P_TIMEOUT, 120);
		store.setDefault(PreferenceConstants.P_MAX_TOKENS, 0);
		store.setDefault(PreferenceConstants.P_TEMPERATURE, "0.2"); //$NON-NLS-1$
		store.setDefault(PreferenceConstants.P_REFACTOR_APPLY_MODE, PreferenceConstants.APPLY_MODE_DIRECT);
		store.setDefault(PreferenceConstants.P_ANTHROPIC_AUTH, PreferenceConstants.AUTH_API_KEY);
		store.setDefault(PreferenceConstants.P_MAVEN_EXEC, ""); //$NON-NLS-1$
		store.setDefault(PreferenceConstants.P_GRADLE_EXEC, ""); //$NON-NLS-1$
		store.setDefault(PreferenceConstants.P_MAVEN_GOALS, PreferenceConstants.DEFAULT_MAVEN_GOALS);
		store.setDefault(PreferenceConstants.P_GRADLE_TASKS, PreferenceConstants.DEFAULT_GRADLE_TASKS);
	}
}
