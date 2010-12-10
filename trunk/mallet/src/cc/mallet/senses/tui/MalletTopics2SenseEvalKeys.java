
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
 * @author Xuchen Yao
 *
 */
public class MalletTopics2SenseEvalKeys {

	static CommandOption.File inputFile = new CommandOption.File
	(MalletTopics2SenseEvalKeys.class, "input", "FILE", true, null,
			"Input file, which is the output of mallet infer-topics.", null);

	static CommandOption.File outputFile = new CommandOption.File
	(MalletTopics2SenseEvalKeys.class, "output", "FILE", true, null,
			"Output file, should conform to SenseEval .keys format.", null);

	static CommandOption.Boolean applyWeight = new CommandOption.Boolean
	(MalletTopics2SenseEvalKeys.class, "weight", "String", false, false,
			"whether to apply weight in output", null);

	protected File input;
	protected File output;

	public MalletTopics2SenseEvalKeys () {
		this.input = inputFile.value();
		this.output = outputFile.value();
	}



	public void run () {
		FileLineTokenIterator inputIte = new FileLineTokenIterator(this.input);
		List<String> inputList;
		BufferedWriter bw = null;
		String[] tokens;
		String filePath, senseNum, noun, nounWithNum;
		StringBuffer sb = new StringBuffer();

		try {
			bw = new BufferedWriter(new FileWriter(this.output));
			while (inputIte.hasNext()) {
				inputList = inputIte.next();
				if (inputList.get(0).startsWith("#")) {
					continue;
				} else {
					// 0 /home/xuchen/giga/senses/SenseEval07Splitted/area.n/area.n.10 1 0.29345695577656067 2 0.2911547075668216 3 0.2589755955289252 0 0.15641274112769285
					filePath = inputList.get(1);
					tokens = filePath.split("/");
					noun = tokens[tokens.length-2];
					nounWithNum = tokens[tokens.length-1];
					senseNum = noun+".C"+inputList.get(2);
					sb.append(noun);
					sb.append(" ");
					sb.append(nounWithNum);
					sb.append(" ");
					sb.append(senseNum);
					if (applyWeight.value()) {
						sb.append("/"+inputList.get(3)+" ");
						sb.append(noun+".C"+inputList.get(4)+"/"+inputList.get(5)+" ");
						sb.append(noun+".C"+inputList.get(6)+"/"+inputList.get(7)+" ");
						sb.append(noun+".C"+inputList.get(8)+"/"+inputList.get(9));
					}
					sb.append("\n");
				}
			}
			bw.write(sb.toString());
			bw.close();
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
		CommandOption.setSummary (MalletTopics2SenseEvalKeys.class,
				"A preprocessing tool for converting the output of mallet infer-topics" +
		" to SenseEval keys.\n");
		CommandOption.process (MalletTopics2SenseEvalKeys.class, args);

		// Print some helpful messages for error cases
		if (args.length == 0) {
			CommandOption.getList(MalletTopics2SenseEvalKeys.class).printUsage(false);
			System.exit (-1);
		}

		MalletTopics2SenseEvalKeys e = new MalletTopics2SenseEvalKeys();
		e.run();
		System.out.println("done");
	}

}
