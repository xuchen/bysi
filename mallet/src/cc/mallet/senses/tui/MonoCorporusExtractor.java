
package cc.mallet.senses.tui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import cc.mallet.util.CommandOption;

/**
 * Extract the context of target words from an input corpus (say, BNC, WSJ, etc)
 * @author Xuchen Yao
 *
 */
public class MonoCorporusExtractor {

	static CommandOption.File inputFile = new CommandOption.File
	(MonoCorporusExtractor.class, "input", "FILE", true, null,
	 "Input file, one sentence per line, lowercased. For langauge l2 in alignment l1-l2." +
	 " Note: l2 should be the target language!", null);

	static CommandOption.File stoplistFile = new CommandOption.File
	(MonoCorporusExtractor.class, "stoplist", "FILE", true, null,
	 "Read \"stop words\" from a file, one per line. For langauge l2 in alignment l1-l2.", null);

	static CommandOption.String outputDir = new CommandOption.String
	(MonoCorporusExtractor.class, "output-dir", "STRING", true, null,
	 "output directory for the target language", null);

	static CommandOption.File targetWordFile = new CommandOption.File
	(MonoCorporusExtractor.class, "target-word-file", "FILE", false, null,
	 "Input file containing all variantions of the target word, such as 'drug', 'drugs', " +
	 "one per line, lowercased.", null);

	static CommandOption.SpacedStrings targetWordList = new CommandOption.SpacedStrings
	(MonoCorporusExtractor.class, "target-word-list", "SPACED_STRING", false, null,
			"a list of target words, such as drug drugs. use either this option or target-word-file", null);

	static CommandOption.Integer windowSize = new CommandOption.Integer
		(MonoCorporusExtractor.class, "window-size", "INTEGER", false, 10,
		 "window size for both left context and right context. The final window" +
		 " will be window-size*2", null);

	public HashSet<String> stopwords;
	public HashSet<String> targetWords;
	protected int fileCounter;
	protected long lineCounter;
	protected String outputFolder;

	private static final Pattern LEX_NON_ALPHA = Pattern.compile("\\p{Digit}+|\\p{P}");

	public MonoCorporusExtractor () {
		this.stopwords = readFile(stoplistFile.value(), null);
		if (targetWordList.value() != null) {
			this.targetWords = new HashSet<String> ();
			for (String s:targetWordList.value())
				this.targetWords.add(s);
		} else
			this.targetWords = readFile(targetWordFile.value(), null);
		this.fileCounter = 1;
		this.lineCounter = 0;
		this.outputFolder = outputDir.value()+"/";
		new File(this.outputFolder).mkdirs();
	}

	public void run () {
		FileLineTokenIterator rightIte = new FileLineTokenIterator(inputFile.value());
		List<String> rightList;
		LinkedList<String> contextRight = new LinkedList<String>();
		// we keep a large window
		final int size = 2*windowSize.value()+1, middle = windowSize.value();
		// whether the context window is full
		boolean full = false;
		long i = 0;

		while (rightIte.hasNext()) {
			rightList = rightIte.next();
			this.lineCounter++;

			for (int right=0; right<rightList.size(); right++) {

				if (!this.shouldRemoveRight(rightList.get(right))) {
					if (i<size) {
						i++;
					} else {
						full = true;
						// remove the first one to keep fixed size;
						contextRight.remove();
					}
					contextRight.add(rightList.get(right));
					if (full) {
						/*
						 * only pay attention to the middle one. we might lose some
						 * instances at the beginning&end of the file, but compared
						 * to the large data set, this shouldn't hurt
						 */
						if (targetWords.contains(contextRight.get(middle))) {
							this.writeContext(contextRight);
						}
					}
				}
			}
		}

		rightIte.close();
	}

	/**
	 * write stings in right context to a file, don't write the middle one
	 * @param contextRight
	 */
	protected void writeContext (List<String> contextRight) {
		int len = contextRight.size(), half = len/2;


		String fileTarget = this.outputFolder+this.fileCounter+"_"+this.lineCounter+".txt";
		StringBuilder sbRight = new StringBuilder();
		this.fileCounter++;

		for (int i=0; i<len; i++) {
			// don't write the middle one
			if (i==half) {
				continue;
			}
			// don't write any target word
			if (this.targetWords.contains(contextRight.get(i))) continue;
			sbRight.append(contextRight.get(i));
			sbRight.append("/r ");
		}
		try {
			BufferedWriter fosTarget = new BufferedWriter(new FileWriter(fileTarget));
			fosTarget.write(sbRight.toString());
			fosTarget.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	private boolean shouldRemoveRight (String token) {
		boolean ret = false;
		if (stopwords.contains(token))
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
		CommandOption.setSummary (MonoCorporusExtractor.class,
								  "A preprocessing tool for extracting aligned context words from" +
								  " multi-lingual aligned documents.\n");
		CommandOption.process (MonoCorporusExtractor.class, args);

		// Print some helpful messages for error cases
		if (args.length == 0) {
			CommandOption.getList(MonoCorporusExtractor.class).printUsage(false);
			System.exit (-1);
		}

		MonoCorporusExtractor e = new MonoCorporusExtractor();
		e.run();
		System.out.println("done");
	}

}
