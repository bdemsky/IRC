//package mapreduce;

/*import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Vector;*/

import mapreduce.MapReduceBase;

public class ReduceWorker {

    int ID;
    MapReduceBase mapreducer;
 
    Vector interoutputs;  // string vector containing paths
                          // of intermediate outputs from map worker
    Vector keys;
    HashMap values; // hashmap map key to vector of string vector
    int[] sorts; // array record the sort of keys
    OutputCollector output;
    String outputfile;  // path of the intermediate output file

    public ReduceWorker(Vector interoutputs, int id) {
	this.ID = id;
	this.mapreducer = null;
	
	this.interoutputs = interoutputs;

	this.keys = new Vector();
	this.values = new HashMap();
	//this.sorts = null;

	this.output = new OutputCollector();
	this.outputfile = "/home/jzhou/mapreduce/output-intermediate-reduce-" + String.valueOf(id) + ".dat";
    }

    public MapReduceBase getMapreducer() {
        return mapreducer;
    }

    public void setMapreducer(MapReduceBase mapreducer) {
        this.mapreducer = mapreducer;
    }

    public void sortgroup() {
	/*if(ID % 2 == 1) {
	    int a[] = new int[1];
	    int temp = a[1];
	}*/
	
	// group values associated to the same key
	//System.printString("================================\n");
	if(interoutputs == null) {
	    return;
	}
	//try{
	    for(int i = 0; i < interoutputs.size(); ++i) {
		FileInputStream iStream = new FileInputStream((String)interoutputs.elementAt(i));
		byte[] b = new byte[1024 * 100];
		int length = iStream.read(b);
		if(length < 0) {
		    System./*out.println*/printString("Error! Can not read from intermediate ouput file of map worker: " + (String)interoutputs.elementAt(i) + "\n");
		    System.exit(-1);
		}
		String content = new String(b, 0, length);
		//System.printString(content + "\n");
		int index = content.indexOf('\n');
		while(index != -1) {
		    String line = content.substring(0, index);
		    content = content.substring(index + 1);
		    //System.printString(line + "\n");
		    int tmpindex = line.indexOf(' ');
		    String key = line.substring(0, tmpindex);
		    String value = line.substring(tmpindex + 1);
		    //System.printString(key + "; " + value + "\n");
		    if(!this.values.containsKey(key)) {
			this.values.put(key, new Vector());
			this.keys.addElement(key);
		    }
		    ((Vector)this.values.get(key)).addElement(value);
		    index = content.indexOf('\n');
		}
		iStream.close();
	    }
	    //System.printString("================================\n");

	    /*for(int i = 0; i < this.keys.size(); ++i) {
			System.printString((String)this.keys.elementAt(i) + ", " + ((String)this.keys.elementAt(i)).hashCode() + "; ");
		}
		System.printString("\n");*/

	    // sort all the keys inside interoutputs
	    this.sorts = new int[this.keys.size()];
	    // insert sorting
	    this.sorts[0] = 0;
	    int tosort = 1;
	    for(; tosort < this.keys.size(); ++tosort) {
		int tosortkey = ((String)this.keys.elementAt(tosort)).hashCode();
		int index = tosort;
		for(int i = tosort; i > 0; --i) {
		    if(((String)this.keys.elementAt(this.sorts[i - 1])).hashCode() > tosortkey) {
			this.sorts[i] = this.sorts[i-1];
			index = i - 1;
		    } else {
			//System.printString(i + "; " + tosort + "\n");
			index = i;
			i = 0;
		    }
		}
		this.sorts[index] = tosort;
	    }
	    /*for(int i = 0; i < this.sorts.length; ++i) {
			System.printString(this.sorts[i] + "; ");
		}
		System.printString("\n");*/
	/*} catch(IOException e) {
	    e.printStackTrace();
	    System.exit(-1);
	}*/
    }

    public void reduce() {
	/*if(ID % 2 == 1) {
	    int a[] = new int[1];
	    int temp = a[1];
	}*/
	
	if(this.interoutputs == null) {
	    return;
	}
	for(int i = 0; i < this.sorts.length; ++i) {
	    String key = (String)this.keys.elementAt(this.sorts[i]);
	    Vector values = (Vector)this.values.get(key);
	    this.mapreducer.reduce(key, values, output);
	}

	//try{
	    // output all the result into some local file
	    int size = this.output.size();
	    FileOutputStream oStream = new FileOutputStream(outputfile, true); // append
	    for(int i = 0; i < size; ++i) {
		String key = this.output.getKey(i);
		String value = this.output.getValue(i);
		// format: key value\n
		oStream.write(key.getBytes());
		oStream.write(' ');
		oStream.write(value.getBytes());
		oStream.write('\n');
		oStream.flush();
	    }
	    oStream.close();
	/*} catch(Exception e) {
	    e.printStackTrace();
	    System.exit(-1);
	}*/
    }

    public String getOutputFile() {
	return this.outputfile;
    }

    public int getID() {
	return this.ID;
    }
}
