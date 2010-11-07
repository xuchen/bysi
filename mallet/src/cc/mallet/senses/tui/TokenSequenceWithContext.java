/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package cc.mallet.senses.tui;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.*;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.FeatureSequenceWithBigrams;
import cc.mallet.types.Instance;
import cc.mallet.types.Token;
import cc.mallet.types.TokenSequence;
/**
 * Remove tokens from the token sequence in the data field whose text is in the stopword list.
 @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class TokenSequenceWithContext extends Pipe implements Serializable
{
	// xxx Use a gnu.trove collection instead
	HashSet<String> stoplist = null;
	boolean caseSensitive = true;
	boolean markDeletions = false;

	/** left/right context size */
	protected int lrContextSize;

//	protected ArrayList<Instance> carrierList = null;
//	protected int current = -1;

	private HashSet<String> newDefaultStopList ()
	{
		HashSet<String> sl = new HashSet<String>();
		for (int i = 0; i < stopwords.length; i++)
			sl.add (stopwords[i]);
		return sl;
	}


	public TokenSequenceWithContext (boolean caseSensitive, boolean markDeletions, int lrContextSize)
	{
		stoplist = newDefaultStopList();
		this.caseSensitive = caseSensitive;
		this.markDeletions = markDeletions;
		this.lrContextSize = lrContextSize;
	}

	public TokenSequenceWithContext (boolean caseSensitive, int lrContextSize)
	{
		stoplist = newDefaultStopList();
		this.caseSensitive = caseSensitive;
		this.lrContextSize = lrContextSize;
	}

	public TokenSequenceWithContext ()
	{
		this (false, 10);
	}

	/**
	 *  Load a stoplist from a file.
	 *  @param stoplistFile    The file to load
	 *  @param encoding        The encoding of the stoplist file (eg UTF-8)
	 *  @param includeDefault  Whether to include the standard mallet English stoplist
	 */
	public TokenSequenceWithContext(File stoplistFile, String encoding, boolean includeDefault,
			boolean caseSensitive, boolean markDeletions) {
		if (! includeDefault) { stoplist = new HashSet<String>(); }
		else { stoplist = newDefaultStopList(); }

		addStopWords (fileToStringArray(stoplistFile, encoding));

		this.caseSensitive = caseSensitive;
		this.markDeletions = markDeletions;
	}

//	public boolean isOneToMultiPipes() {
//		return true;
//	}


//	public boolean hasNextInMulti(Instance carrier) {
//		if (this.current == -1) {
//			this.carrierList = this.pipeExpand(carrier);
//			if (this.carrierList.size() != 0) {
//				this.current = 0;
//				return true;
//			} else
//				return false;
//		}
//		if (this.current != -1 && this.current != this.carrierList.size()) {
//			return true;
//		}
//		return false;
//	}
//
//	public Instance getNextInMulti() {
//		if (this.current == -1 || this.carrierList == null || this.carrierList.size() == 0)
//			return null;
//		else {
//			return this.carrierList.get(this.current);
//		}
//	}

	public TokenSequenceWithContext setCaseSensitive (boolean flag)
	{
		this.caseSensitive = flag;
		return this;
	}

	public TokenSequenceWithContext setMarkDeletions (boolean flag)
	{
		this.markDeletions = flag;
		return this;
	}

	public TokenSequenceWithContext addStopWords (String[] words)
	{
		for (int i = 0; i < words.length; i++)
			stoplist.add (words[i]);
		return this;
	}


	public TokenSequenceWithContext removeStopWords (String[] words)
	{
		for (int i = 0; i < words.length; i++)
			stoplist.remove (words[i]);
		return this;
	}

	/** Remove whitespace-separated tokens in file "wordlist" to the stoplist. */
	public TokenSequenceWithContext removeStopWords (File wordlist)
	{
		this.removeStopWords (fileToStringArray(wordlist, null));
		return this;
	}

	/** Add whitespace-separated tokens in file "wordlist" to the stoplist. */
	public TokenSequenceWithContext addStopWords (File wordlist)
	{
		if (wordlist != null)
			this.addStopWords (fileToStringArray(wordlist, null));
		return this;
	}


	private String[] fileToStringArray (File f, String encoding)
	{
		ArrayList<String> wordarray = new ArrayList<String>();

		try {

			BufferedReader input = null;
			if (encoding == null) {
				input = new BufferedReader (new FileReader (f));
			}
			else {
				input = new BufferedReader( new InputStreamReader( new FileInputStream(f), encoding ));
			}
			String line;

			while (( line = input.readLine()) != null) {
				String[] words = line.split ("\\s+");
				for (int i = 0; i < words.length; i++)
					wordarray.add (words[i]);
			}

		} catch (IOException e) {
			throw new IllegalArgumentException("Trouble reading file "+f);
		}
		return (String[]) wordarray.toArray(new String[]{});
	}

	public ArrayList<Instance> pipeExpand (Instance carrier)
	{
		ArrayList<Instance> carrierList = new ArrayList<Instance>();
		TokenSequence ts = (TokenSequence) carrier.getData();
		// xxx This doesn't seem so efficient.  Perhaps have TokenSequence
		// use a LinkedList, and remove Tokens from it? -?
		// But a LinkedList implementation of TokenSequence would be quite inefficient -AKM
		TokenSequence ret = new TokenSequence ();
		Token prevToken = null;
		Instance newCarrier;
		for (int i = 0; i < ts.size(); i++) {
			Token t = ts.get(i);
			Token tj;
			if (stoplist.contains (caseSensitive ? t.getText() : t.getText().toLowerCase())) {
				// xxx Should we instead make and add a copy of the Token?
				ret.clear();
				int left = (i-this.lrContextSize<0?0:i-this.lrContextSize);
				int right = (i+this.lrContextSize+1>ts.size()?ts.size():i+this.lrContextSize+1);
				for (int j= left; j<right ; j++) {
					// don't add the target word itself
					tj = ts.get(j);
					if (!stoplist.contains (caseSensitive ? tj.getText() : tj.getText().toLowerCase())) {
						ret.add (tj);
					}
				}
				newCarrier = (Instance) carrier.clone();
				newCarrier.setData(ret);
				carrierList.add(newCarrier);
				prevToken = t;
			} else if (markDeletions && prevToken != null)
				prevToken.setProperty (FeatureSequenceWithBigrams.deletionMark, t.getText());
		}
		return carrierList;
	}

	public Iterator<Instance> newIteratorFrom (Iterator<Instance> source)
	{
		return new MultiPipeInstanceIterator (source);
	}

	// The InstanceIterator used to implement the one-to-one pipe() method behavior.
	private class MultiPipeInstanceIterator implements Iterator<Instance>
	{
		Iterator<Instance> source;
		Iterator<Instance> target;

		public MultiPipeInstanceIterator (Iterator<Instance> source) {
			this.source = source;
			ArrayList<Instance> carrierList = new ArrayList<Instance>();
			while (source.hasNext()) {
				carrierList.addAll(pipeExpand(source.next()));
			}
			this.target = carrierList.iterator();
		}
		public boolean hasNext () { return target.hasNext(); }
		public Instance next() {
			return target.next();
			}
		/** Return the @link{Pipe} that processes @link{Instance}s going through this iterator. */
		public Pipe getPipe () { return null; }
		public Iterator<Instance> getSourceIterator () { return source; }
		public void remove() { throw new IllegalStateException ("Not supported."); }
	}

	// Serialization

	//private static final long serialVersionUID = 1;
	static final long serialVersionUID = -1091332240793191237L;
	private static final int CURRENT_SERIAL_VERSION = 2;

	private void writeObject (ObjectOutputStream out) throws IOException {
		out.writeInt (CURRENT_SERIAL_VERSION);
		out.writeBoolean(caseSensitive);
		out.writeBoolean(markDeletions);
		out.writeInt(lrContextSize);
		out.writeObject(stoplist); // New as of CURRENT_SERIAL_VERSION 2
	}

	private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException {
		int version = in.readInt ();
		caseSensitive = in.readBoolean();
		if (version > 0)
			markDeletions = in.readBoolean();
		lrContextSize = in.readInt();
		if (version > 1) {
			stoplist = (HashSet<String>) in.readObject();
		}

	}


	static final String[] stopwords =
	{
		"mushroom",
		"mushrooms"
	};


}
