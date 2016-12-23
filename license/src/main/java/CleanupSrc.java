/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This class ensures that there is a boiler-place licence at the top of each source file
 * and strips out all "@author" tags.
 */
public class CleanupSrc {
	private static final File SRC_DIR = new File(".");
	private static final URL BOILERPLATE = CleanupSrc.class.getResource("/boilerplate");
	
	private static final List<String> boilerplate = read(BOILERPLATE);
	private static final Pattern AUTHOR_PATTERN = Pattern.compile("\\*?\\s*@author.*");

	private static List<String> read(URL url) {
		try {
			List<String> lines = new ArrayList<String>();
			BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
			try {
				String line;
				while ((line = in.readLine()) != null)
					lines.add(line);
				return lines;
			} finally {
				in.close();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static List<String> read(File file) {
		try {
			return read(file.toURL());
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	private static void writeFile(File file, List<String> lines) {
		try {
			PrintWriter out = new PrintWriter(file);
			try {
				for (String line : lines) {
					out.println(line);
				}
			} finally {
				out.close();
			}
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public static void main(String[] args) {
		scan(SRC_DIR);
	}

	private static void scan(File dir) {
		System.out.println("Scanning " + dir);
		File[] files = dir.listFiles();
		if (files == null)
			return;
		for (File file : files) {
			if (file.getName().equals("CVS") || file.getName().startsWith("."))
				continue;
			if (file.isDirectory())
				scan(file);
			else if (file.getName().endsWith(".java"))
				new CleanupSrc(file).process();
		}
	}

	private final File file;

	private CleanupSrc(File file) {
		this.file = file;
	}

	private void process() {
		System.out.println("Processing " + file);
		List<String> original = read(file);
		List<String> lines = new ArrayList<String>(original);
		fixBoilerplate(lines);
		fixJavadoc(lines);
		if (!lines.equals(original)) {
			System.out.println("Writing " + file);
			writeFile(file, lines);
		}
	}

	private void fixBoilerplate(List<String> lines) {
		if (lines.get(0).trim().equals("/*")) {
			// has boiler plate
			for (int i = 0; i < lines.size(); i++) {
				if (lines.get(i).trim().equals("*/")) {
					while (lines.get(i + 1).trim().isEmpty())
						i++;
					lines.subList(0, i + 1).clear(); // trim boilerplate
					break;
				}
			}
		}
		lines.addAll(0, boilerplate);
	}

	private void fixJavadoc(List<String> lines) {
		int start = -1;
		int lastEmpty = -1;
		int lastText = -1;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			if (line.equals("/**")) {
				start = i; // start javadoc
				lastEmpty = i;
				lastText = -1;
			} else if (start >= 0) {
				// in javadoc
				if (line.equals("*/")) {
					if (lastText < 0) {
						// it was an empty javadoc
						lines.subList(start, i + 1).clear();
						i = start - 1;
					} else if (lastEmpty > lastText) {
						// empty line(s) at the end of javadoc
						lines.subList(lastEmpty, i).clear();
						i = lastEmpty;
					}
					start = -1; // end javadoc
				} else if (AUTHOR_PATTERN.matcher(line).matches()) {
					lines.remove(i);
					i--;
				} else if (line.equals("*") || line.isEmpty()) {
					if (lastEmpty < lastText)
						lastEmpty = i;
				} else
					lastText = i;
			}
		}
	}
}
