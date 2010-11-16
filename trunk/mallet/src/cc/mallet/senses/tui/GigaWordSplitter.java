
package cc.mallet.senses.tui;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import cc.mallet.util.CommandOption;

/**
 * Split a sentence aligned giga word corpus into smaller chunks:
 * splitting it into 20 million word chunks
 * throwing out any sentence pair where either the French of the English tokenized sentence is >70 words long
 * or where there is a ratio of words 2x on one side
 * where either side has no words
 * @author Xuchen Yao
 *
 */
public class GigaWordSplitter {

	static CommandOption.File inputFileLeft = new CommandOption.File
	(GigaWordSplitter.class, "input-left", "FILE", true, null,
			"Input file, one sentence per line, lowercased. For langauge l1 in alignment l1-l2.", null);

	static CommandOption.File inputFileRight = new CommandOption.File
	(GigaWordSplitter.class, "input-right", "FILE", true, null,
			"Input file, one sentence per line, lowercased. For langauge l2 in alignment l1-l2." +
			" Note: l2 should be the target language!", null);

	static CommandOption.String outputDir = new CommandOption.String
	(GigaWordSplitter.class, "output-dir", "STRING", true, null,
			"output directory for mixed languages", null);

	static CommandOption.Double chunkSize = new CommandOption.Double
	(GigaWordSplitter.class, "chunk-size", "INTEGER", false, 2,
			"chunk size in million words of input-left.", null);

	protected int fileCounter;
	protected String outputFolder;
	protected String leftFilePath;
	protected String rightFilePath;
	protected long chunkLength;

	public GigaWordSplitter () {
		this.fileCounter = 1;
		this.outputFolder = outputDir.value()+"/";
		new File(this.outputFolder).mkdirs();
		this.leftFilePath = this.outputFolder + inputFileLeft.value().getName()+".";
		this.rightFilePath = this.outputFolder + inputFileRight.value().getName()+".";
		this.chunkLength = (long) (chunkSize.value() * 1000000);
	}

	public void run () {
		FileLineTokenIterator leftIte = new FileLineTokenIterator(inputFileLeft.value());
		FileLineTokenIterator rightIte = new FileLineTokenIterator(inputFileRight.value());
		List<String> leftList, rightList;
		long tokenCounter = 0;
		int leftSize, rightSize;
		String fileLeft = this.leftFilePath+this.fileCounter;
		String fileRight = this.rightFilePath+this.fileCounter;
		BufferedWriter bwLeft = null, bwRight = null;

		try {
			bwLeft = new BufferedWriter(new FileWriter(fileLeft));
			bwRight = new BufferedWriter(new FileWriter(fileRight));
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}

		try {
			while (leftIte.hasNext()) {
				if (tokenCounter > this.chunkLength) {
					tokenCounter = 0;
					bwLeft.close();
					bwRight.close();
					this.fileCounter++;
					fileLeft = this.leftFilePath+this.fileCounter;
					fileRight = this.rightFilePath+this.fileCounter;
					bwLeft = new BufferedWriter(new FileWriter(fileLeft));
					bwRight = new BufferedWriter(new FileWriter(fileRight));
				}
				leftList = leftIte.next();
				rightList = rightIte.next();
				leftSize = leftList.size();
				rightSize = rightList.size();
				if (leftSize == 0 || rightSize == 0 ||
						leftSize > 70 || rightSize > 70 ||
						leftSize/rightSize >= 2 || rightSize/leftSize >= 2 )
					continue;
				tokenCounter += leftSize;
				StringBuilder sbLeft = new StringBuilder(), sbRight = new StringBuilder();
				for (int i=0; i<leftSize; i++) {
					sbLeft.append(leftList.get(i));
					sbLeft.append(" ");
				}
				sbLeft.append("\n");
				for (int i=0; i<rightSize; i++) {
					sbRight.append(rightList.get(i));
					sbRight.append(" ");
				}
				sbRight.append("\n");
				bwLeft.write(sbLeft.toString());
				bwRight.write(sbRight.toString());

			}
			bwLeft.close();
			bwRight.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		leftIte.close();
		rightIte.close();
	}


	/**
	 * @param args
	 */
	public static void main(String[] args) throws FileNotFoundException, IOException {
		// Process the command-line options
		CommandOption.setSummary (GigaWordSplitter.class,
				"A preprocessing tool for extracting aligned context words from" +
		" multi-lingual aligned documents.\n");
		CommandOption.process (GigaWordSplitter.class, args);

		// Print some helpful messages for error cases
		if (args.length == 0) {
			CommandOption.getList(GigaWordSplitter.class).printUsage(false);
			System.exit (-1);
		}

		GigaWordSplitter e = new GigaWordSplitter();
		e.run();
		System.out.println("done");
	}

}
