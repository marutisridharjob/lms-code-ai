package com.lmscode.ai.ui;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

/**
 * Shared dark "editor" palette for the LMS Code views (VS Code-inspired).
 * Each view allocates its own instance and disposes it with the view.
 */
public final class DarkTheme {

	/** View background. */
	public final Color background;
	/** Slightly raised surface (input fields, code blocks). */
	public final Color surface;
	/** Primary text. */
	public final Color foreground;
	/** Secondary/dim text (timestamps, hints). */
	public final Color dim;
	/** User accent (blue). */
	public final Color accentUser;
	/** Assistant accent (teal). */
	public final Color accentAssistant;
	/** Error accent (red). */
	public final Color accentError;
	/** Headings (light blue). */
	public final Color heading;
	/** Code block text (amber). */
	public final Color codeFg;
	/** Code block background. */
	public final Color codeBg;
	/** Inline code (soft orange). */
	public final Color inlineCode;

	public DarkTheme(Display display) {
		background = new Color(display, 30, 30, 30);
		surface = new Color(display, 42, 42, 46);
		foreground = new Color(display, 214, 214, 214);
		dim = new Color(display, 138, 138, 138);
		accentUser = new Color(display, 97, 175, 239);
		accentAssistant = new Color(display, 86, 211, 180);
		accentError = new Color(display, 240, 90, 90);
		heading = new Color(display, 120, 180, 250);
		codeFg = new Color(display, 220, 195, 130);
		codeBg = new Color(display, 44, 44, 50);
		inlineCode = new Color(display, 214, 157, 133);
	}

	public void dispose() {
		background.dispose();
		surface.dispose();
		foreground.dispose();
		dim.dispose();
		accentUser.dispose();
		accentAssistant.dispose();
		accentError.dispose();
		heading.dispose();
		codeFg.dispose();
		codeBg.dispose();
		inlineCode.dispose();
	}
}
