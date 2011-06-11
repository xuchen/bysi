
package cc.mallet.senses.tui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import cc.mallet.util.CommandOption;

/**
 * Extract translation pairs from aligned multi-lingual corpora
 * Don't write output to a directory, instead, to a single file.
 * Users should use "bin/mallet import-file" to import the data.
 *
 * Note: for some unknown reason, the output files are often
 * corrupted (checked by gzip -t option). So use "AlignedCorporaExtractorToFile"
 * to output just plain-text files
 * @author Xuchen Yao
 *
 */
public class AlignedCorporaExtractorToZipFile {

	static CommandOption.File alignFile = new CommandOption.File
	(AlignedCorporaExtractorToZipFile.class, "align", "FILE", true, null,
	 "alignment file.", null);

	static CommandOption.File inputFileLeft = new CommandOption.File
	(AlignedCorporaExtractorToZipFile.class, "input-left", "FILE", true, null,
	 "Input file, one sentence per line, lowercased. For langauge l1 in alignment l1-l2.", null);

	static CommandOption.File inputFileRight = new CommandOption.File
	(AlignedCorporaExtractorToZipFile.class, "input-right", "FILE", true, null,
	 "Input file, one sentence per line, lowercased. For langauge l2 in alignment l1-l2." +
	 " Note: l2 should be the target language!", null);

	static CommandOption.File stoplistFileLeft = new CommandOption.File
	(AlignedCorporaExtractorToZipFile.class, "stoplist-left", "FILE", true, null,
	 "Read \"stop words\" from a file, one per line. For langauge l1 in alignment l1-l2.", null);

	static CommandOption.File stoplistFileRight = new CommandOption.File
	(AlignedCorporaExtractorToZipFile.class, "stoplist-right", "FILE", true, null,
	 "Read \"stop words\" from a file, one per line. For langauge l2 in alignment l1-l2.", null);

	static CommandOption.File outputGzipFileMixed = new CommandOption.File
	(AlignedCorporaExtractorToZipFile.class, "output-gzip-file-mix", "File", true, null,
	 "output gzipped file for mixed languages", null);

	static CommandOption.File outputGzipFileTarget = new CommandOption.File
	(AlignedCorporaExtractorToZipFile.class, "output-gzip-file-target", "File", true, null,
	 "output gzipped file for the target language", null);

	static CommandOption.File targetWordFile = new CommandOption.File
	(AlignedCorporaExtractorToZipFile.class, "target-word-file", "FILE", false, null,
	 "Input file containing all variantions of the target word, such as 'drug', 'drugs', " +
	 "one per line, lowercased.", null);

	static CommandOption.SpacedStrings targetWordList = new CommandOption.SpacedStrings
	(AlignedCorporaExtractorToZipFile.class, "target-word-list", "SPACED_STRING", false, null,
			"a list of target words, such as drug drugs. use either this option or target-word-file", null);

	static CommandOption.Integer windowSize = new CommandOption.Integer
		(AlignedCorporaExtractorToZipFile.class, "window-size", "INTEGER", false, 10,
		 "window size for both left context and right context. The final window" +
		 " will be window-size*2", null);

	static CommandOption.Boolean writeLeftTargetWord = new CommandOption.Boolean
	(AlignedCorporaExtractorToZipFile.class, "write-left-target-word", "BOOLEAN", false, false,
			"whether to output the target word of l1 (say, 'banque' or 'banc')", null);

	public HashSet<String> stopwordsLeft;
	public HashSet<String> stopwordsRight;
	public HashSet<String> targetWords;
	public HashSet<String> targetWordsTranslation;
	protected int fileCounter;
	protected long lineCounter;
	GZIPOutputStream outGzipMix;
	GZIPOutputStream outGzipTarget;
	protected String label = null;

	private static final Pattern LEX_NON_ALPHA = Pattern.compile("\\p{Digit}+|\\p{P}");

	public AlignedCorporaExtractorToZipFile () {
		this.stopwordsLeft = readFile(stoplistFileLeft.value(), null);
		this.stopwordsRight = readFile(stoplistFileRight.value(), null);
		if (targetWordList.value() != null) {
			this.targetWords = new HashSet<String> ();
			for (String s:targetWordList.value())
				this.targetWords.add(s);
		} else
			this.targetWords = readFile(targetWordFile.value(), null);
		for (String l:this.targetWords) {
			// use the shortest one as label
			if (this.label == null || l.length() < this.label.length())
				this.label = l;
		}
		this.targetWordsTranslation = new HashSet<String> ();
		this.fileCounter = 0;
		this.lineCounter = 0;
		try {
			outGzipMix = new GZIPOutputStream(new FileOutputStream(outputGzipFileMixed.value()));
			outGzipTarget = new GZIPOutputStream(new FileOutputStream(outputGzipFileTarget.value()));
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public void run () {

		FileLineTokenIterator alignIte = new FileLineTokenIterator(alignFile.value());
		FileLineTokenIterator leftIte = new FileLineTokenIterator(inputFileLeft.value());
		FileLineTokenIterator rightIte = new FileLineTokenIterator(inputFileRight.value());
		List<String> alignList, leftList, rightList;
		int left, leftIdx, rightIdx;
		String[] alignNum;
		LinkedList<String> contextLeft = new LinkedList<String>();
		LinkedList<String> contextRight = new LinkedList<String>();
		LinkedList<Long> lineNumList = new LinkedList<Long>();
		// we keep a large window
		final int size = 2*windowSize.value()+1, middle = windowSize.value();
		// whether the context window is full
		boolean full = false;
		long i = 0;
		// mapping between the right and left index
		HashMap<Integer, Integer> right2left = new HashMap<Integer, Integer>();

		while (alignIte.hasNext()) {
			this.lineCounter++;
			alignList = alignIte.next();
			leftList = leftIte.next();
			rightList = rightIte.next();
			right2left.clear();
			for (String align:alignList) {
				// 11-9
				alignNum = align.split("-");
				if (alignNum.length != 2)
					continue;
				try {
					leftIdx = Integer.parseInt(alignNum[0]);
					rightIdx = Integer.parseInt(alignNum[1]);
					right2left.put(rightIdx, leftIdx);
				} catch (Exception e) {
					System.out.println("parseInt exception: "+Arrays.asList(alignNum));
					e.printStackTrace();
					continue;
				}
			}
			// right list contains the target language (say, English)
			for (int right=0; right<rightList.size(); right++) {
				if (right2left.containsKey(right))
					left = right2left.get(right);
				else continue;
				if (!this.shouldRemoveLeft(leftList.get(left)) && !this.shouldRemoveRight(rightList.get(right))) {
					if (i<size) {
						i++;
					} else {
						full = true;
						// remove the first one to keep fixed size;
						contextLeft.remove();
						contextRight.remove();
						lineNumList.remove();
					}
					contextLeft.add(leftList.get(left));
					contextRight.add(rightList.get(right));
					lineNumList.add(this.lineCounter);
					if (full) {
						/*
						 * only pay attention to the middle one. we might lose some
						 * instances at the beginning&end of the file, but compared
						 * to the large data set, this shouldn't hurt
						 */
						if (targetWords.contains(contextRight.get(middle))) {
							this.writeContext(contextLeft, contextRight, lineNumList.get(middle));
						}
					}
				}
			}
		}

		alignIte.close();
		leftIte.close();
		rightIte.close();
		try {
			outGzipMix.close();
			outGzipTarget.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println(this.targetWords);
		System.out.println(this.targetWordsTranslation);
	}

	/**
	 * write stings in left&right context to a file, don't write the middle one
	 * @param contextLeft
	 * @param contextRight
	 */
	protected void writeContext (List<String> contextLeft, List<String> contextRight, long lineNum) {
		int len = contextLeft.size(), half = len/2;
		if (len != contextRight.size()) {
			System.out.println("left/right context should have the same size!");
			System.out.println("left: "+len+" right: "+contextRight.size());
			System.exit(-1);
		}
		StringBuilder sbLeft = new StringBuilder(), sbRight = new StringBuilder();
		this.fileCounter++;
		if (fileCounter % 1000 == 0) {
			System.out.print(".");
		}
		for (int i=0; i<len; i++) {
			// don't write the middle one
			if (i==half) {
				if (writeLeftTargetWord.value()) {
					sbLeft.append(contextLeft.get(i));
					this.targetWordsTranslation.add(contextLeft.get(i));
					sbLeft.append("/l ");
				}
				continue;
			}
			// don't write any target word
			if (this.targetWords.contains(contextRight.get(i))) continue;
			sbLeft.append(contextLeft.get(i));
			sbLeft.append("/l ");
			sbRight.append(contextRight.get(i));
			sbRight.append("/r ");
		}
		try {
			/*
			 * One file, one instance per line: Assume the data is in the following format:
			 *
		     * [name]  [label]  [text of the page...]
		     *
		     * In this case, the first token of each line (whitespace delimited,
		     * with optional comma) becomes the instance name, the second token
		     * becomes the label, and all additional text on the line is interpreted
		     * as a sequence of word tokens.
			 */
			String name_label = this.fileCounter + "_" + lineNum + " " + this.label + " ";
			String mixStr = name_label + sbLeft.toString() + " " + sbRight.toString() + "\n";
			String targetStr = name_label + sbRight.toString() + "\n";
			this.outGzipMix.write(mixStr.getBytes());
			this.outGzipTarget.write(targetStr.getBytes());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private boolean shouldRemoveLeft (String token) {
		boolean ret = false;
		if (stopwordsLeft.contains(token))
			ret = true;
		else if (LEX_NON_ALPHA.matcher(token).find())
			// if contains any punctuation or digits, then remove
			ret = true;

		return ret;
	}


	private boolean shouldRemoveRight (String token) {
		boolean ret = false;
		if (stopwordsRight.contains(token))
			ret = true;
		else if (LEX_NON_ALPHA.matcher(token).find())
			ret = true;

		return ret;
	}

	public static HashSet<String> readFile (File f, String encoding)
	{
		HashSet<String> wordSet = new HashSet<String>();

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
					wordSet.add (words[i]);
			}

		} catch (IOException e) {
			throw new IllegalArgumentException("Trouble reading file "+f);
		}
		return wordSet;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws FileNotFoundException, IOException {
		// Process the command-line options
		CommandOption.setSummary (AlignedCorporaExtractorToZipFile.class,
								  "A preprocessing tool for extracting aligned context words from" +
								  " multi-lingual aligned documents.\n");
		CommandOption.process (AlignedCorporaExtractorToZipFile.class, args);

		// Print some helpful messages for error cases
		if (args.length == 0) {
			CommandOption.getList(AlignedCorporaExtractorToZipFile.class).printUsage(false);
			System.exit (-1);
		}

		AlignedCorporaExtractorToZipFile e = new AlignedCorporaExtractorToZipFile();
		e.run();
		System.out.println("done");
	}

}
