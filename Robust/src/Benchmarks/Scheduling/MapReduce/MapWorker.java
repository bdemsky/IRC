public class MapWorker {
    flag map;
    flag partition;
    flag mapoutput;

    int ID;

    int r;
    String key;
    String value;
    OutputCollector output;

    //String[] locations;
    OutputCollector[] outputs;

    public MapWorker(String key, String value, int r, int id) {
	this.ID = id;
	this.r = r;

	this.key = key;
	this.value = value;
	this.output = new OutputCollector();

	/*locations = new String[r];
	for(int i = 0; i < r; ++i) {
	    StringBuffer temp = new StringBuffer("/scratch/mapreduce_nor/output-intermediate-map-");
	    temp.append(String.valueOf(ID));
	    temp.append("-of-");
	    temp.append(String.valueOf(r));
	    temp.append("_");
	    temp.append(String.valueOf(i));
	    temp.append(".dat");
	    locations[i] = new String(temp);
	}*/

	outputs = new OutputCollector[r];
	for(int i = 0; i < r; ++i) {
	    outputs[i] = null;
	}
    }

    public void map() {
	MapReduceBase.map(key, value, output);
    }

    public void partition() {
	int size = this.output.size();
	for(int i = 0; i < size; ++i) {
	    String key = this.output.getKey(i);
	    String value = this.output.getValue(i);
	    // use the hashcode of key to decide which intermediate output
	    // this pair should be in
	    int index = (int)Math.abs(key.hashCode()) % this.r;
	    OutputCollector oStream = outputs[index];
	    if(oStream == null) {
		// open the file
		oStream = new OutputCollector(); // append
		outputs[index] = oStream;
	    }
	    oStream.emit(key, "1");
	}
    }

    public OutputCollector outputFile(int i) {
	return outputs[i];
    }

    public int getID() {
	return this.ID;
    }

    public int getR() {
	return this.r;
    }

}
