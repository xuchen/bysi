
package cc.mallet.senses.tui;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import cc.mallet.util.CommandOption;

/**
 * Split malformed-xml-based SenseEval dataset (English_sense_induction.tok.lc.txt) per instance per file:
 * throw away punctuation
 * throw away key word in < head > explains < / head >
 * throw away stop words
 *
 * Note: the file should be first tokenized and lowercased by:
 * perl scripts/tokenizer.perl < English_sense_induction.xml | tr [A-Z] [a-z] > English_sense_induction.tok.lc.xml
 * @author Xuchen Yao
 *
 */
public class SenseEvalSplitter {

	private static final Pattern LEX_NON_ALPHA = Pattern.compile("\\p{Digit}+|\\p{P}");

	static CommandOption.File inputFile = new CommandOption.File
	(SenseEvalSplitter.class, "input", "FILE", true, null,
			"Input file, one sentence per line, lowercased. For langauge l1 in alignment l1-l2.", null);

	static CommandOption.String outputDir = new CommandOption.String
	(SenseEvalSplitter.class, "output-dir", "STRING", true, null,
			"output directory for mixed languages", null);

	static CommandOption.File stoplistFile = new CommandOption.File
	(SenseEvalSplitter.class, "stoplist", "FILE", true, null,
	 "Read \"stop words\" from a file, one per line.", null);

	protected String outputFolder;
	public HashSet<String> stopwords;
	// < lexelt item = " explain.v " >
	protected Pattern itemDirPattern = Pattern.compile("< lexelt item = \" (\\S)+ \" >");
	// < instance id = " explain.v.4 " corpus = " wsj " >
	protected Pattern instanceFilePattern = Pattern.compile("< instance id = \" (\\S)+ \".*>");

	public SenseEvalSplitter () {
		this.outputFolder = outputDir.value()+"/";
		new File(this.outputFolder).mkdirs();
		this.stopwords = AlignedCorporaExtractor.readFile(stoplistFile.value(), null);
	}


	private boolean shouldRemove (String token) {
		boolean ret = false;
		if (stopwords.contains(token))
			ret = true;
		else if (LEX_NON_ALPHA.matcher(token).find())
			// if contains any punctuation or digits, then remove
			ret = true;

		return ret;
	}

	public void run () {
		FileLineTokenIterator inputIte = new FileLineTokenIterator(inputFile.value());
		List<String> inputList;
		BufferedWriter bw = null;
		String dir = null, dirBase = null, file = null;

		try {
			while (inputIte.hasNext()) {
				inputList = inputIte.next();
				if (inputList.get(0).equals("<")) {
					// markup
					if (inputList.get(1).equals("lexelt")) {
						// < lexelt item = " explain.v " >
						dirBase = inputList.get(5);
						dir = this.outputFolder+dirBase+"/";
						if (!new File(dir).mkdirs()) {
							System.out.println("mkdir failed: "+dir);
							System.exit(-1);
						}
					} else if (inputList.get(1).equals("instance")) {
						// < instance id = " explain.v.4 " corpus = " wsj " >
						file = inputList.get(5);
						if (!file.contains(dirBase)) {
							System.out.println("file "+file+" doesn't contain "+dirBase);
							System.exit(-1);
						}
						file = dir + file;
						bw = new BufferedWriter(new FileWriter(file));
					}
				} else if (!inputList.get(0).startsWith("<")) {
					// a normal string, such as:
					// opec secretary-general subroto < head > explains < / head > : consumers
					StringBuilder sb = new StringBuilder();
					for (int i=0; i<inputList.size(); i++) {
						if (inputList.get(i).equals("<") && inputList.get(i+1).equals("head")
								&& inputList.get(i+7).equals(">")) {
							i += 7;
							continue;
						}
						if (this.shouldRemove(inputList.get(i)))
							continue;
						sb.append(inputList.get(i));
						/*
						 * match with the original training vectors, whereas
						 * the /l marks the 'left' language, which is foreign,
						 * and /r marks the 'right' language, which is english.
						 */
						sb.append("/r ");
					}
					bw.write(sb.toString());
					bw.close();
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		inputIte.close();
	}


	/**
	 * @param args
	 */
	public static void main(String[] args) throws FileNotFoundException, IOException {
		// Process the command-line options
		CommandOption.setSummary (SenseEvalSplitter.class,
				"A preprocessing tool for extracting words from" +
		" pre-processed SenseEval dataset.\n");
		CommandOption.process (SenseEvalSplitter.class, args);

		// Print some helpful messages for error cases
		if (args.length == 0) {
			CommandOption.getList(SenseEvalSplitter.class).printUsage(false);
			System.exit (-1);
		}

		SenseEvalSplitter e = new SenseEvalSplitter();
		e.run();
		System.out.println("done");
	}

}
