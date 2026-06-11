package com.lmscode.ai.core;

import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;

import com.lmscode.ai.Activator;

/**
 * Formats Java source with the workspace's JDT code formatter. JDT is an
 * optional dependency — when it is not installed (or formatting fails) the
 * source is returned unchanged.
 */
public final class JavaFormatter {

	private JavaFormatter() {
	}

	/** Formats {@code source} when it is a Java file; otherwise returns it unchanged. */
	public static String formatIfJava(String fileName, String source) {
		if (fileName == null || !fileName.endsWith(".java")) { //$NON-NLS-1$
			return source;
		}
		try {
			return JdtDelegate.format(source);
		} catch (LinkageError e) {
			// JDT not installed — skip formatting
			return source;
		} catch (Exception e) {
			Activator.logError("Formatting failed for " + fileName + "; using unformatted content", e);
			return source;
		}
	}

	/** Separate class so JDT classes only load when actually invoked. */
	private static final class JdtDelegate {

		static String format(String source) throws Exception {
			CodeFormatter formatter = ToolFactory.createCodeFormatter(null);
			TextEdit edit = formatter.format(CodeFormatter.K_COMPILATION_UNIT,
					source, 0, source.length(), 0, null);
			if (edit == null) {
				return source; // unparsable (e.g. mid-refactor syntax) — keep as-is
			}
			IDocument document = new Document(source);
			edit.apply(document);
			return document.get();
		}
	}
}
