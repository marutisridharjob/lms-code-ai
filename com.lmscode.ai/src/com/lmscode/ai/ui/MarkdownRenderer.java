package com.lmscode.ai.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;

/**
 * Renders a lightweight subset of Markdown into a {@link StyledText}:
 * fenced code blocks, inline {@code `code`}, {@code **bold**}, headings,
 * bullet lists and horizontal rules. Pure SWT — no browser widget — so the
 * views stay fast and lightweight while responses read like a drafted
 * document instead of a raw dump.
 */
public final class MarkdownRenderer {

	private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$"); //$NON-NLS-1$
	private static final Pattern BULLET = Pattern.compile("^(\\s*)[-*]\\s+(.*)$"); //$NON-NLS-1$
	private static final Pattern RULE = Pattern.compile("^\\s*([-*_])\\1{2,}\\s*$"); //$NON-NLS-1$
	private static final Pattern INLINE = Pattern.compile("(\\*\\*(.+?)\\*\\*)|(`([^`\\n]+)`)"); //$NON-NLS-1$

	private MarkdownRenderer() {
	}

	/** Appends {@code markdown} to the widget, styling it with {@code theme}. */
	public static void append(StyledText widget, String markdown, DarkTheme theme) {
		if (markdown == null || markdown.isEmpty()) {
			return;
		}
		StringBuilder out = new StringBuilder();
		List<StyleRange> ranges = new ArrayList<>();
		boolean inCode = false;

		for (String line : markdown.split("\n", -1)) { //$NON-NLS-1$
			String trimmed = line.strip();
			if (trimmed.startsWith("```")) { //$NON-NLS-1$
				inCode = !inCode;
				continue; // fence line itself is not rendered
			}
			if (inCode) {
				int start = out.length();
				out.append("  ").append(line).append('\n'); //$NON-NLS-1$
				StyleRange code = new StyleRange(start, out.length() - start - 1, theme.codeFg, theme.codeBg);
				ranges.add(code);
				continue;
			}
			Matcher heading = HEADING.matcher(line);
			if (heading.matches()) {
				int start = out.length();
				out.append(heading.group(2)).append('\n');
				StyleRange range = new StyleRange(start, heading.group(2).length(), theme.heading, null, SWT.BOLD);
				ranges.add(range);
				continue;
			}
			if (RULE.matcher(line).matches()) {
				int start = out.length();
				out.append("────────────────────────────\n"); //$NON-NLS-1$
				ranges.add(new StyleRange(start, out.length() - start - 1, theme.dim, null));
				continue;
			}
			Matcher bullet = BULLET.matcher(line);
			String body = line;
			if (bullet.matches()) {
				int start = out.length();
				out.append(bullet.group(1)).append("• "); //$NON-NLS-1$
				ranges.add(new StyleRange(start, out.length() - start, theme.accentAssistant, null));
				body = bullet.group(2);
			}
			appendInline(out, ranges, body, theme);
			out.append('\n');
		}

		applyTo(widget, out.toString(), ranges);
	}

	/** Handles {@code **bold**} and {@code `inline code`} inside one line. */
	private static void appendInline(StringBuilder out, List<StyleRange> ranges, String line, DarkTheme theme) {
		Matcher matcher = INLINE.matcher(line);
		int last = 0;
		while (matcher.find()) {
			out.append(line, last, matcher.start());
			if (matcher.group(2) != null) { // **bold**
				int start = out.length();
				out.append(matcher.group(2));
				ranges.add(new StyleRange(start, matcher.group(2).length(), null, null, SWT.BOLD));
			} else { // `code`
				int start = out.length();
				out.append(matcher.group(4));
				ranges.add(new StyleRange(start, matcher.group(4).length(), theme.inlineCode, theme.codeBg));
			}
			last = matcher.end();
		}
		out.append(line, last, line.length());
	}

	/** Appends plain text (no markdown parsing) in the given color. */
	public static void appendPlain(StyledText widget, String text, DarkTheme theme, org.eclipse.swt.graphics.Color color, int fontStyle) {
		if (text == null || text.isEmpty()) {
			return;
		}
		List<StyleRange> ranges = new ArrayList<>();
		if (color != null || fontStyle != SWT.NORMAL) {
			ranges.add(new StyleRange(0, text.length(), color, null, fontStyle));
		}
		applyTo(widget, text, ranges);
	}

	private static void applyTo(StyledText widget, String text, List<StyleRange> ranges) {
		int base = widget.getCharCount();
		widget.append(text);
		for (StyleRange range : ranges) {
			if (range.length <= 0) {
				continue;
			}
			range.start += base;
			widget.setStyleRange(range);
		}
		widget.setTopIndex(widget.getLineCount() - 1);
	}
}
