public class MapWorker {

    int ID;
    MapReduceBase mapreducer;
    int r;
    String key;
    String value;
    OutputCollector output;
    String locationPrefix;
    boolean[] outputsexit;

    public MapWorker(String key, String value, int r, int id) {
	this.ID = id;
	this.mapreducer = null;

	this.r = r;
	this.key = key;
	this.value = value;
	this.output = new OutputCollector();
	this.locationPrefix = "/scratch/mapreduce_java/output-intermediate-map-";
	
	this.outputsexit = new boolean[r];
    }

    public MapReduceBase getMapreducer() {
	return mapreducer;
    }

    public void setMapreducer(MapReduceBase mapreducer) {
	this.mapreducer = mapreducer;
    }

    public void map() {
	this.mapreducer.map(key, value, output);
	this.key = null;
	this.value = null;
    }

    public void partition() {
	FileOutputStream[] outputs = new FileOutputStream[r];
	for(int i = 0; i < r; ++i) {
	    outputs[i] = null;
	}
	
	int size = this.output.size();
	for(int i = 0; i < size; ++i) {
	    String key = this.output.getKey(i);
	    String value = this.output.getValue(i);
	    // use the hashcode of key to decide which intermediate output
	    // this pair should be in
	    //int hash = key.hashCode();
	    int index = (int)Math.abs(key.hashCode()) % this.r;
	    FileOutputStream oStream = outputs[index];
	    if(oStream == null) {
		// open the file
		String filepath = this.locationPrefix + this.ID + "-of-" + this.r + "_" + index + ".dat";
		oStream = new FileOutputStream(filepath, true); // append
		outputs[index] = oStream;
		this.outputsexit[index] = true;
	    }
	    // format: key value\n
	    oStream.write(key.getBytes());
	    oStream.write(' ');
	    oStream.write(value.getBytes());
	    oStream.write('\n');
	    oStream.flush();
	}

	// close the output files
	for(int i = 0; i < outputs.length; ++i) {
	    FileOutputStream temp = outputs[i];
	    if(temp != null) {
		temp.close();
		outputs[i] = null;
	    }
	}
	
	this.output = null;
    }

    public String outputFile(int i) {
	if(outputsexit[i]) {
	    StringBuffer temp = new StringBuffer(this.locationPrefix);
	    temp.append(String.valueOf(ID));
	    temp.append("-of-");
	    temp.append(String.valueOf(r));
	    temp.append("_");
	    temp.append(String.valueOf(i));
	    temp.append(".dat");
	    return new String(temp);
	} else {
	    return null;
	}
    }

    public int getID() {
	return this.ID;
    }

    public int getR() {
	return this.r;
    }

}
