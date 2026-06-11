package com.lmscode.ai.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;

/**
 * Charset-aware read/write helpers for workspace files.
 */
public final class FileContents {

	private FileContents() {
	}

	public static String read(IFile file) throws CoreException, IOException {
		try (InputStream in = file.getContents(true)) {
			return new String(in.readAllBytes(), charsetOf(file));
		}
	}

	public static void write(IFile file, String content) throws CoreException {
		file.setContents(new ByteArrayInputStream(content.getBytes(charsetOf(file))), true, true, null);
	}

	public static Charset charsetOf(IFile file) {
		try {
			return Charset.forName(file.getCharset());
		} catch (Exception e) {
			return StandardCharsets.UTF_8;
		}
	}
}
