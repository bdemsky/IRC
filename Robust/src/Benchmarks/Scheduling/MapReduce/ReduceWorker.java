public class ReduceWorker {
    flag sortgroup;
    flag reduce;
    flag reduceoutput;

    int ID;
    Vector interoutputs;  // string vector containing paths
    // of intermediate outputs from map worker
    Vector keys;
    HashMap values; // hashmap map key to vector of string vector
    int[] sorts; // array record the sort of keys
    OutputCollector output;
    //String outputfile;  // path of the intermediate output file

    public ReduceWorker(Vector interoutputs, int id) {
	this.ID = id;
	this.interoutputs = interoutputs;

	this.keys = new Vector();
	this.values = new HashMap();
	//this.sorts = null;

	this.output = new OutputCollector();
	//this.outputfile = "/scratch/mapreduce_nor/output-intermediate-reduce-" + String.valueOf(id) + ".dat";
    }

    public void sortgroup() {
	// group values associated to the same key
	//System.printString("================================\n");
	if(interoutputs == null) {
	    return;
	}
	for(int i = 0; i < interoutputs.size(); ++i) {
	    OutputCollector tmpout = (OutputCollector)interoutputs.elementAt(i);
	    int size = tmpout.size();
	    for(int j= 0; j < size; ++j) {
		String key = tmpout.getKey(j);
		String value = tmpout.getValue(j);
		if(!this.values.containsKey(key)) {
		    this.values.put(key, new Vector());
		    this.keys.addElement(key);
		}
		((Vector)this.values.get(key)).addElement(value);
	    }
	}
	//System.printString("================================\n");

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
    }

    public void reduce() {
	if(this.interoutputs != null) {
	    for(int i = 0; i < this.sorts.length; ++i) {
		String key = (String)this.keys.elementAt(this.sorts[i]);
		Vector values = (Vector)this.values.get(key);
		MapReduceBase.reduce(key, values, this.output);
	    }
	}

	// output all the result into some local file
	/*int size = this.output.size();
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
	oStream.close();*/
    }

    /*public String getOutputFile() {
	return this.outputfile;
    }*/
    
    public OutputCollector getOutput() {
	return this.output;
    }

    public int getID() {
	return this.ID;
    }
}
