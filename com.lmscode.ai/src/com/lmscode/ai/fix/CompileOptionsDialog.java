package com.lmscode.ai.fix;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.lmscode.ai.Activator;
import com.lmscode.ai.preferences.PreferenceConstants;

/**
 * Lets the user pick the Maven goals / Gradle tasks to run for the Compile
 * action. The previous choice is persisted in the preference store and
 * pre-selected the next time the dialog opens.
 */
public class CompileOptionsDialog extends Dialog {

	private static final String[] MAVEN_PRESETS = {
			PreferenceConstants.DEFAULT_MAVEN_GOALS,
			"clean compile", //$NON-NLS-1$
			"clean verify", //$NON-NLS-1$
			"clean install", //$NON-NLS-1$
			"clean install -DskipTests", //$NON-NLS-1$
			"dependency:tree", //$NON-NLS-1$
	};
	private static final String[] GRADLE_PRESETS = {
			PreferenceConstants.DEFAULT_GRADLE_TASKS,
			"clean build", //$NON-NLS-1$
			"clean build -x test", //$NON-NLS-1$
			"clean assemble", //$NON-NLS-1$
			"dependencies", //$NON-NLS-1$
	};

	private final boolean showMaven;
	private final boolean showGradle;

	private Combo mavenCombo;
	private Combo gradleCombo;
	private String mavenGoals;
	private String gradleTasks;

	public CompileOptionsDialog(Shell parentShell, boolean showMaven, boolean showGradle) {
		super(parentShell);
		this.showMaven = showMaven;
		this.showGradle = showGradle;
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("LMS Code - Compile");
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite area = (Composite) super.createDialogArea(parent);
		Composite container = new Composite(area, SWT.NONE);
		container.setLayout(new GridLayout(2, false));
		container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Label hint = new Label(container, SWT.WRAP);
		hint.setText("Choose the build actions to run. The output is analyzed by the AI"
				+ " and explained in the LMS Response view.");
		GridData hintData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
		hintData.widthHint = 420;
		hint.setLayoutData(hintData);

		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		if (showMaven) {
			new Label(container, SWT.NONE).setText("Maven goals:");
			mavenCombo = new Combo(container, SWT.DROP_DOWN);
			mavenCombo.setItems(MAVEN_PRESETS);
			mavenCombo.setText(store.getString(PreferenceConstants.P_MAVEN_GOALS));
			mavenCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		}
		if (showGradle) {
			new Label(container, SWT.NONE).setText("Gradle tasks:");
			gradleCombo = new Combo(container, SWT.DROP_DOWN);
			gradleCombo.setItems(GRADLE_PRESETS);
			gradleCombo.setText(store.getString(PreferenceConstants.P_GRADLE_TASKS));
			gradleCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		}
		return area;
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, "Compile", true);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
	}

	@Override
	protected void okPressed() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		if (mavenCombo != null) {
			mavenGoals = mavenCombo.getText().trim();
			if (mavenGoals.isEmpty()) {
				mavenGoals = PreferenceConstants.DEFAULT_MAVEN_GOALS;
			}
			store.setValue(PreferenceConstants.P_MAVEN_GOALS, mavenGoals); // retained for next time
		}
		if (gradleCombo != null) {
			gradleTasks = gradleCombo.getText().trim();
			if (gradleTasks.isEmpty()) {
				gradleTasks = PreferenceConstants.DEFAULT_GRADLE_TASKS;
			}
			store.setValue(PreferenceConstants.P_GRADLE_TASKS, gradleTasks);
		}
		super.okPressed();
	}

	public String mavenGoals() {
		return mavenGoals != null ? mavenGoals : PreferenceConstants.DEFAULT_MAVEN_GOALS;
	}

	public String gradleTasks() {
		return gradleTasks != null ? gradleTasks : PreferenceConstants.DEFAULT_GRADLE_TASKS;
	}
}
