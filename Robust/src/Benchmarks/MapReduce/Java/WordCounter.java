
//import java.io.FileInputStream;
//import java.util.Vector;

/*import mapreduce.Configuration;
import mapreduce.Configured;
import mapreduce.JobClient;
import mapreduce.MapReduceBase;
import mapreduce.OutputCollector;
import mapreduce.Tool;
import mapreduce.ToolRunner;*/

/**
     * Counts the words in each line.
     * For each line of input, break the line into words and emit them as
     * (<b>word</b>, <b>1</b>).
     */
    public class MapReduceClass extends MapReduceBase {
	
	public MapReduceClass() {}

	public void map(String key, String value, OutputCollector output) {
	    int n = value.length();
	    for (int i = 0; i < n; ) {
		// Skip past leading whitespace
		while ((i < n) && isspace(value.charAt(i))) {
		    ++i;
		}

		// Find word end
		int start = i;
		while ((i < n) && !isspace(value.charAt(i))) {
		    i++;
		}

		if (start < i) {
		    output.emit(value.substring(start, i), "1");
		}
	    }
	}

	public void reduce(String key, Vector values, OutputCollector output) {
	    // Iterate over all entries with the
	    // // same key and add the values
	    int value = 0;
	    for(int i = 0; i < values.size(); ++i) {
		value += Integer.parseInt((String)values.elementAt(i));
	    }

	    // Emit sum for input->key()
	    output.emit(key, String.valueOf(value));
	}

	boolean isspace(char c) {
	    if((c == ' ') || 
		    (c == '.') ||
		    (c == '!') ||
		    (c == '?') ||
		    (c == '"') ||
		    (c == '\n')) {
		return true;
	    }
	    return false;
	}
    }

public class WordCounter /*implements*/extends Tool {

	public WordCounter() {}

    static int printUsage() {
	System./*out.println*/printString("<conffile>\n");
	return -1;
    }

    /**
     * The main driver for word count map/reduce program.
     * Invoke this method to submit the map/reduce job.
     * @throws IOException When there is communication problems with the 
     *                     job tracker.
     */
    public int run(String[] args) {
	//try {
	    MapReduceClass mapreducer = new MapReduceClass();       

	    FileInputStream iStream = new FileInputStream(args[0]);
	    byte[] b = new byte[1024];
	    int length = iStream.read(b);
	    if(length < 0 ) {
		System./*out.println*/printString("Error! Can not read from configure file: " + args[0] + "\n");
		System.exit(-1);
	    }
	    String content = new String(b, 0, length);
	    //System.out.println(content);
	    int index = content.indexOf('\n');
	    String inputfile = content.substring(0, index);
	    content = content.substring(index + 1);
	    index = content.indexOf('\n');
	    int m = Integer.parseInt(content.substring(0, index));
	    content = content.substring(index + 1);
	    index = content.indexOf('\n');
	    int r = Integer.parseInt(content.substring(0, index));
	    content = content.substring(index + 1);
	    index = content.indexOf('\n');
	    String temp = content.substring(0, index);
	    char seperator = temp.charAt(0);
	    //System.out.println(inputfile + "; " + String.valueOf(m) + "; " + String.valueOf(r));
	    
	    Configuration conf = new Configuration();
	    conf.setMapReduceClass(mapreducer);
	    conf.setInputfile(inputfile);
	    conf.setM(m);
	    conf.setR(r);
	    conf.setSeperator(seperator);

	    JobClient.runJob(conf);
	/*} catch (Exception e) {
	    e.printStackTrace();
	    System.exit(-1);
	}*/
	
	return 0;
    }


    public static void main(String[] args) /*throws Exception*/ {
	int res = ToolRunner.run(new WordCounter(), args);
	System.exit(res);
    }

}
