package com.lmscode.ai;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * Plugin lifecycle for LMS Code AI.
 */
public class Activator extends AbstractUIPlugin {

	public static final String PLUGIN_ID = "com.lmscode.ai"; //$NON-NLS-1$

	private static Activator plugin;

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	public static Activator getDefault() {
		return plugin;
	}

	public static ILog log() {
		return getDefault().getLog();
	}

	public static void logError(String message, Throwable t) {
		log().log(new Status(IStatus.ERROR, PLUGIN_ID, message, t));
	}

	public static void logInfo(String message) {
		log().log(new Status(IStatus.INFO, PLUGIN_ID, message));
	}
}
