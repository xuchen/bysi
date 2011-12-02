/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/**
	@author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
*/

package cc.mallet.senses.tui;

import java.io.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.net.URI;


public class FileLineTokenIterator implements Iterator<List<String>> {

	BufferedReader reader = null;
	int index = -1;
	String currentLine = null;
	boolean hasNextUsed = false;

	int progressDisplayInterval = 0;

	public FileLineTokenIterator (String filename) {
		try {
			this.reader = new BufferedReader (new FileReader(filename));
			this.index = 0;
		} catch (IOException e) {
			throw new RuntimeException (e);
		}
	}

	public void close () {
		try {
			this.reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public FileLineTokenIterator (File file) {
		try {
			if (file.getName().endsWith(".gz")) {
				this.reader = new BufferedReader (new InputStreamReader(new GZIPInputStream(new FileInputStream(file))));
			} else
				this.reader = new BufferedReader (new FileReader(file));
			this.index = 0;
			GZIPInputStream in;

		} catch (IOException e) {
			throw new RuntimeException (e);
		}
	}

	/** Set the iterator to periodically print the
	 *   total number of lines read to standard out.
	 *  @param interval how often to print
	 */
	public void setProgressDisplayInterval(int interval) {
		progressDisplayInterval = interval;
	}

	public List<String> next () {

		URI uri = null;
		try { uri = new URI ("array:" + index++); }
		catch (Exception e) { throw new RuntimeException (e); }

		if (!hasNextUsed) {
			try {
				currentLine = reader.readLine();
			}
			catch (IOException e) {
				throw new RuntimeException (e);
			}
		}
		else {
			hasNextUsed = false;
		}

		if (progressDisplayInterval != 0 &&
			index > 0 &&
			index % progressDisplayInterval == 0) {
			System.out.println(index);
		}

		return Arrays.asList(this.currentLine.split("\\s+"));
	}

	public boolean hasNext ()	{
		hasNextUsed = true;
		try {
			currentLine = reader.readLine();
		}
		catch (IOException e) {
			throw new RuntimeException (e);
		}
		return (currentLine != null);
	}

	public void remove () {
		throw new IllegalStateException ("This Iterator<Instance> does not support remove().");
	}

}
