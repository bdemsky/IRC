public class Master {

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
	this.mworkerStates = new int[m];
	this.rworkerStates = new int[r];
	this.interoutputs = new Vector[r];
	this.splitter = splitter;
	this.outputfile = new String("/scratch/mapreduce_java/output.dat");
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

    /*public void split() {
	splitter.split();
    }*/

    public MapWorker[] assignMap() {
	String[] contentsplits = splitter.split();//splitter.getSlices();
	MapWorker[] mworkers = new MapWorker[contentsplits.length];
	for(int i = 0; i < contentsplits.length; ++i) {
	    //System.printString("*************************\n");
	    //System.printString(contentsplits[i] + "\n");
	    //System.printString("*************************\n");
	    MapWorker mworker = new MapWorker(splitter.getFilename(), contentsplits[i], r, i);
	    mworkerStates[i] = 1;
	    mworkers[i] = mworker;
	}
	this.splitter = null;
	return mworkers;
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

    public ReduceWorker[] assignReduce() {
	ReduceWorker[] rworkers = new ReduceWorker[interoutputs.length];
	for(int i = 0; i < interoutputs.length; ++i) {
	    ReduceWorker rworker = new ReduceWorker(interoutputs[i], i);
	    rworkerStates[i] = 1;
	    rworkers[i] = rworker;
	    this.interoutputs[i] = null;
	}
	this.interoutputs.clear();
	return rworkers;
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
