
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import cc.mallet.util.CommandOption;

/**
 * Extract translation pairs from aligned multi-lingual corpora
 * Don't write output to a directory, instead, to a single file.
 * Users should use "bin/mallet import-file" to import the data.
 * @author Xuchen Yao
 *
 */
public class AlignedCorporaExtractorToFileForWSI {

	static CommandOption.File alignFile = new CommandOption.File
	(AlignedCorporaExtractorToFileForWSI.class, "align", "FILE", true, null,
	 "alignment file.", null);

	static CommandOption.File inputFileLeft = new CommandOption.File
	(AlignedCorporaExtractorToFileForWSI.class, "input-left", "FILE", true, null,
	 "Input file, one sentence per line, lowercased. For langauge l1 in alignment l1-l2.", null);

	static CommandOption.File vocabFileLeft = new CommandOption.File
	(AlignedCorporaExtractorToFileForWSI.class, "vocab-left", "FILE", false, null,
	"Vocabulary file, word per line, lowercased. For langauge l1 in alignment l1-l2.", null);

	static CommandOption.File inputFileRight = new CommandOption.File
	(AlignedCorporaExtractorToFileForWSI.class, "input-right", "FILE", true, null,
	 "Input file, one sentence per line, lowercased. For langauge l2 in alignment l1-l2." +
	 " Note: l2 should be the target language!", null);

	static CommandOption.File vocabFileRight = new CommandOption.File
	(AlignedCorporaExtractorToFileForWSI.class, "vocab-right", "FILE", false, null,
	"Vocabulary file, word per line, lowercased. For langauge l2 in alignment l1-l2.", null);

	static CommandOption.File outputFileMixed = new CommandOption.File
	(AlignedCorporaExtractorToFileForWSI.class, "output-file-mix", "File", true, null,
	 "output file for mixed languages", null);

	static CommandOption.File targetWordFile = new CommandOption.File
	(AlignedCorporaExtractorToFileForWSI.class, "target-word-file", "FILE", false, null,
	 "Input file containing all variantions of the target word, such as 'drug', 'drugs', " +
	 "one per line, lowercased.", null);

	static CommandOption.SpacedStrings targetWordList = new CommandOption.SpacedStrings
	(AlignedCorporaExtractorToFileForWSI.class, "target-word-list", "SPACED_STRING", false, null,
			"a list of target words, such as drug drugs. use either this option or target-word-file", null);

	public HashSet<String> vocabLeft = null;
	public HashSet<String> vocabRight = null;
	public HashSet<String> targetWords;
	public HashSet<String> targetWordsTranslation;
	public HashMap<String, Integer> targetWordsTranslationMap = new HashMap<String, Integer>();
	protected int fileCounter;
	protected long lineCounter;
	BufferedWriter outMix;
	protected String label = null;

	public AlignedCorporaExtractorToFileForWSI () {
		if (vocabFileLeft.wasInvoked())
			this.vocabLeft = readFile(vocabFileLeft.value(), null);
		if (vocabFileRight.wasInvoked())
			this.vocabRight = readFile(vocabFileRight.value(), null);
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
			outMix = new BufferedWriter(new FileWriter(outputFileMixed.value()));
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
		HashSet<Integer> leftSet;
		String[] alignNum;
		// mapping between the right and left index
		HashMap<Integer, HashSet<Integer>> right2left = new HashMap<Integer, HashSet<Integer>>();
		HashMap<Integer, HashSet<Integer>> left2right = new HashMap<Integer, HashSet<Integer>>();
		StringBuilder outBuffer = new StringBuilder();
		//ArrayList<Integer> leftIdxList = new ArrayList<Integer>(), rightIdxList = new ArrayList<Integer>();
		Integer[] leftIdxList , rightIdxList;
		HashSet<Integer> rightIdxSet = new HashSet<Integer> ();

		while (alignIte.hasNext()) {
			outBuffer = new StringBuilder();
			this.lineCounter++;
			alignList = alignIte.next();
			leftList = leftIte.next();
			rightList = rightIte.next();
			right2left.clear();
			left2right.clear();
			for (String align:alignList) {
				// 11-9
				alignNum = align.split("-");
				if (alignNum.length != 2)
					continue;
				try {
					leftIdx = Integer.parseInt(alignNum[0]);
					rightIdx = Integer.parseInt(alignNum[1]);
					if (!right2left.containsKey(rightIdx)) {
						right2left.put(rightIdx, new HashSet<Integer> ());
					}
					right2left.get(rightIdx).add(leftIdx);
					if (!left2right.containsKey(leftIdx)) {
						left2right.put(leftIdx, new HashSet<Integer> ());
					}
					left2right.get(leftIdx).add(rightIdx);
				} catch (Exception e) {
					System.out.println("parseInt exception: "+Arrays.asList(alignNum));
					e.printStackTrace();
					continue;
				}
			}
			// right list contains the target language (say, English)
			String englishTarget, frenchTarget, target;
			for (int right=0; right<rightList.size(); right++) {
				if (targetWords.contains(rightList.get(right))) {
					if (right2left.containsKey(right))
						leftSet = right2left.get(right);
					else continue;
					rightIdxSet.clear();
					for (Integer ii:leftSet) {
						rightIdxSet.addAll(left2right.get(ii));
					}
					rightIdxList = rightIdxSet.toArray(new Integer[0]);
					leftIdxList = leftSet.toArray(new Integer[0]);
					left = leftIdxList[0];
					Arrays.sort(rightIdxList);
					Arrays.sort(leftIdxList);
					englishTarget = frenchTarget = "";
					for (int ii:rightIdxList) {
						englishTarget += "_" + rightList.get(ii);
					}
					for (int ii:leftIdxList) {
						frenchTarget += "_" + leftList.get(ii);
					}
					englishTarget = englishTarget.replaceFirst("_", "");
					frenchTarget = frenchTarget.replaceFirst("_", "");
					target = englishTarget + ":" + frenchTarget;
					if (!this.targetWordsTranslationMap.containsKey(target)) {
						this.targetWordsTranslationMap.put(target, 0);
					}
					this.targetWordsTranslationMap.put(target, this.targetWordsTranslationMap.get(target) + 1);
					this.fileCounter ++;
					outBuffer.append("<pair id='" + this.fileCounter + "' line='" + this.lineCounter + "'>\n");
					outBuffer.append("\t<English head='" + englishTarget + "'>\n\t\t");
					for (int k=0; k<rightList.size(); k++) {
						if (k == right) {
							outBuffer.append("<main_head>" + rightList.get(k) + "</main_head> ");
						} else if (rightIdxSet.contains(k) && k != right) {
							outBuffer.append("<head>" + rightList.get(k) + "</head> ");
						} else {
							outBuffer.append(rightList.get(k) + " ");
						}
					}
					outBuffer.append("\n\t</English>\n");

					outBuffer.append("\t<French head='" + frenchTarget + "'>\n\t\t");
					for (int k=0; k<leftList.size(); k++) {
						if (k == left) {
							outBuffer.append("<main_head>" + leftList.get(k) + "</main_head> ");
						} else if (leftSet.contains(k) && k != left) {
							outBuffer.append("<head>" + leftList.get(k) + "</head> ");
						} else {
							outBuffer.append(leftList.get(k) + " ");
						}
					}
					outBuffer.append("\n\t</French>\n");
					outBuffer.append("</pair>\n");

					break;
				}
			}

			try {
				this.outMix.write(outBuffer.toString());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		outBuffer.append("<stat>\n");
		LinkedHashMap sortedMap = this.sortByValue(this.targetWordsTranslationMap);
		outBuffer.append(sortedMap.toString());
		outBuffer.append("\n</stat>\n");

		alignIte.close();
		leftIte.close();
		rightIte.close();
		try {
			this.outMix.write(outBuffer.toString());

			outMix.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	LinkedHashMap sortByValue(Map map) {
	     List list = new LinkedList(map.entrySet());
	     Collections.sort(list, new Comparator() {
	          public int compare(Object o1, Object o2) {
	               return ((Comparable) ((Map.Entry) (o1)).getValue())
	              .compareTo(((Map.Entry) (o2)).getValue());
	          }
	     });

	    LinkedHashMap result = new LinkedHashMap();
	    for (Iterator it = list.iterator(); it.hasNext();) {
	        Map.Entry entry = (Map.Entry)it.next();
	        result.put(entry.getKey(), entry.getValue());
	    }
	    return result;
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
		CommandOption.setSummary (AlignedCorporaExtractorToFileForWSI.class,
								  "A preprocessing tool for extracting aligned context words from" +
								  " multi-lingual aligned documents.\n");
		CommandOption.process (AlignedCorporaExtractorToFileForWSI.class, args);

		// Print some helpful messages for error cases
		if (args.length == 0) {
			CommandOption.getList(AlignedCorporaExtractorToFileForWSI.class).printUsage(false);
			System.exit (-1);
		}

		AlignedCorporaExtractorToFileForWSI e = new AlignedCorporaExtractorToFileForWSI();
		e.run();
	}

}
