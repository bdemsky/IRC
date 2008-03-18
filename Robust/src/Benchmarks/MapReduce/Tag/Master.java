public class Master {
    flag split;
    flag assignMap;
    flag mapoutput;
    flag mapfinished;
    flag assignReduce;
    flag reduceoutput;
    flag reducefinished;
    flag output;

    int m;
    int r;
    int[] mworkerStates; // array of map worker's state
    // 0: idle  1: process  2: finished 3: fail
    int[] rworkerStates; // array of reduce worker's state
    Vector[] interoutputs; // array of string vector containing
    // paths of intermediate outputs from
    // map worker

    Splitter splitter;

    String outputfile;  // path of final output file

    boolean partial;

    public Master(int m, int r, Splitter splitter) {
	this.m = m;
	this.r = r;

	mworkerStates = new int[m];
	rworkerStates = new int[r];
	for(int i = 0; i < m; ++i) {
	    mworkerStates[i] = 0;
	}
	for(int i = 0; i < r; ++i) {
	    rworkerStates[i] = 0;
	}

	interoutputs = new Vector[r];
	for(int i = 0; i < r; ++i) {
	    interoutputs[i] = null;
	}

	this.splitter = splitter;
	this.outputfile = new String("/scratch/mapreduce_opt/output.dat");

	this.partial = false;
    }

    public int getR() {
	return this.r;
    }

    public String getOutputFile() {
	return this.outputfile;
    }

    public boolean isPartial() {
	return this.partial;
    }

    public void setPartial(boolean partial) {
	this.partial = partial || this.partial;
    }

    public void split() {
	splitter.split();
    }

    public void assignMap() {
	String[] contentsplits = splitter.getSlices();
	for(int i = 0; i < contentsplits.length; ++i) {
	    //System.printString("*************************\n");
	    //System.printString(contentsplits[i] + "\n");
	    //System.printString("*************************\n");
	    MapWorker mworker = new MapWorker(splitter.getFilename(), contentsplits[i], r, i){map};
	    mworkerStates[i] = 1;
	}
    }

    public void setMapFinish(int i) {
	mworkerStates[i] = 2;
    }

    public void setMapFail(int i) {
	mworkerStates[i] = 3;
    }

    public boolean isMapFinish() {
	for(int i = 0; i < mworkerStates.length; ++i) {
	    if(mworkerStates[i] == 1) {
		return false;
	    }
	}

	return true;
    }

    public void addInterOutput(String interoutput) {
	int start = interoutput.lastindexOf('_');
	int end = interoutput.indexOf('.');
	int index = Integer.parseInt(interoutput.subString(start + 1, end));
	//System.printString(interoutput.subString(start + 1, end) + "\n");
	if(interoutputs[index] == null) {
	    interoutputs[index] = new Vector();
	}
	interoutputs[index].addElement(interoutput);
    }

    public void assignReduce() {
	for(int i = 0; i < interoutputs.length; ++i) {
	    ReduceWorker rworker = new ReduceWorker(interoutputs[i], i){sortgroup};
	    rworkerStates[i] = 1;
	}
    }

    public void setReduceFinish(int i) {
	rworkerStates[i] = 2;
    }

    public void setReduceFail(int i) {
	rworkerStates[i] = 3;
    }

    public boolean isReduceFinish() {
	for(int i = 0; i < rworkerStates.length; ++i) {
	    if(rworkerStates[i] == 1) {
		return false;
	    }
	}

	return true;
    }

    public void collectROutput(String file) {
	FileInputStream iStream = new FileInputStream(file);
	FileOutputStream oStream = new FileOutputStream(outputfile, true);
	byte[] b = new byte[1024 * 10];
	int length = iStream.read(b);
	if(length < 0) {
	    System.printString("Error! Can not read from intermediate output file from reduce worker: " + file + "\n");
	    System.exit(-1);
	}
	//System.printString(new String(b, 0, length) + "\n");
	oStream.write(b, 0, length);
	iStream.close();
	oStream.close();
    }
}
