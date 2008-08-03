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
    int finishmworker;
    int[] rworkerStates; // array of reduce worker's state
    int finishrworker;
    Vector[] interoutputs; // array of OutputCollector vector containing
    // paths of intermediate outputs from
    // map worker

    Splitter splitter;

    //String outputfile;  // path of final output file
    OutputCollector output;

    //boolean partial;

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
	//this.outputfile = new String("/scratch/mapreduce_nor/output.dat");
	this.output = new OutputCollector();

	//this.partial = false;
	this.finishmworker = 0;
	this.finishrworker = 0;
    }

    public int getR() {
	return this.r;
    }

    /*public String getOutputFile() {
	return this.outputfile;
    }*/

    /*public boolean isPartial() {
	return this.partial;
    }

    public void setPartial(boolean partial) {
	this.partial = partial || this.partial;
    }*/

    public void split() {
	splitter.split();
    }

    /*public void assignMap() {
	String[] contentsplits = splitter.getSlices();
	for(int i = 0; i < contentsplits.length; ++i) {
	    //System.printString("*************************\n");
	    //System.printString(contentsplits[i] + "\n");
	    //System.printString("*************************\n");
	    MapWorker mworker = new MapWorker(splitter.getFilename(), contentsplits[i], r, i){map};
	    mworkerStates[i] = 1;
	}
    }*/
    
    public void setMapInit(int i) {
	mworkerStates[i] = 1;
    }

    public void setMapFinish(int i) {
	finishmworker++;
	mworkerStates[i] = 2;
    }

    public void setMapFail(int i) {
	mworkerStates[i] = 3;
    }

    public boolean isMapFinish() {
	/*
	//System.printString("check map finish\n");
	for(int i = 0; i < mworkerStates.length; ++i) {
	    if(mworkerStates[i] == 1) {
		return false;
	    }
	}

	return true;*/
	return this.finishmworker == this.m;
    }

    public void addInterOutput(OutputCollector interoutput, int index) {
	if(interoutputs[index] == null) {
	    interoutputs[index] = new Vector();
	}
	interoutputs[index].addElement(interoutput);
    }

    /*public void assignReduce() {
	for(int i = 0; i < interoutputs.length; ++i) {
	    ReduceWorker rworker = new ReduceWorker(interoutputs[i], i){sortgroup};
	    rworkerStates[i] = 1;
	}
    }*/
    
    public void setReduceInit(int i) {
	rworkerStates[i] = 1;
    }

    public void setReduceFinish(int i) {
	finishrworker++;
	rworkerStates[i] = 2;
    }

    public void setReduceFail(int i) {
	rworkerStates[i] = 3;
    }

    public boolean isReduceFinish() {
	//System.printI(0xa0);
	/*
	for(int i = 0; i < rworkerStates.length; ++i) {
	    if(rworkerStates[i] == 1) {
		//System.printI(0);
		return false;
	    }
	}

	//System.printI(1);
	return true;*/
	return this.finishrworker == this.r;
    }

    public void collectROutput(OutputCollector file) {
	int size = file.size();
	for(int i = 0; i < size; ++i) {
	    String key = file.getKey(i);
	    String value = file.getValue(i);
	    this.output.emit(key, value);
	}
    }
    
    public Vector[] getInteroutputs() {
	return this.interoutputs;
    }
    
    public Splitter getSplitter() {
	return this.splitter;
    }
}
