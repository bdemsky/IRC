//package mapreduce;

//import java.io.FileOutputStream;

public class MapWorker {

    int ID;
    MapReduceBase mapreducer;

    int r;
    String key;
    String value;
    OutputCollector output;

    String[] locations;
    FileOutputStream[] outputs;

    public MapWorker(String key, String value, int r, int id) {
	this.ID = id;
	this.mapreducer = null;

	this.r = r;
	this.key = key;
	this.value = value;
	this.output = new OutputCollector();

	locations = new String[r];
	for(int i = 0; i < r; ++i) {
	    StringBuffer temp = new StringBuffer("/scratch/mapreduce_java/output-intermediate-map-");
	    temp.append(String.valueOf(ID));
	    temp.append("-of-");
	    temp.append(String.valueOf(r));
	    temp.append("_");
	    temp.append(String.valueOf(i));
	    temp.append(".dat");
	    locations[i] = new String(temp);
	}

	outputs = new FileOutputStream[r];
	for(int i = 0; i < r; ++i) {
	    outputs[i] = null;
	}
    }

    public MapReduceBase getMapreducer() {
	return mapreducer;
    }

    public void setMapreducer(MapReduceBase mapreducer) {
	this.mapreducer = mapreducer;
    }

    public void map() {
	/*if(ID % 2 == 1) {
	    String temp = locations[locations.length];
	}*/
	
	this.mapreducer.map(key, value, output);
    }

    public void partition() {
	/*if(ID % 2 == 1) {
	    String temp = locations[locations.length];
	}*/
	
	//try{
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
		    String filepath = locations[index];
		    oStream = new FileOutputStream(filepath, true); // append
		    outputs[index] = oStream;
		}
		// format: key value\n
		oStream.write(key.getBytes());
		oStream.write(' ');
		oStream.write(value.getBytes());
		oStream.write('\n');
		oStream.flush();
	    }

	    // close the output files
	    for(int i = 0; i < this.outputs.length; ++i) {
		FileOutputStream temp = this.outputs[i];
		if(temp != null) {
		    temp.close();
		}
	    }
	/*} catch(Exception e) {
	    e.printStackTrace();
	    System.exit(-1);
	}*/
    }

    public String outputFile(int i) {
	if(outputs[i] != null) {
	    return locations[i];
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
