//package mapreduce;

public class JobClient{
    
    public JobClient() {}
    
    public static void runJob(Configuration conf) {
	Splitter splitter = new Splitter(conf.getInputfile(), conf.getM(), conf.getSeperator());
	Master master = new Master(conf.getM(), conf.getR(), splitter);
	
	// split input file
	System.printString("Split\n");
	master.split();
	
	// do 'map'
	System.printString("Map\n");
	MapWorker[] mworkers = master.assignMap();
	for(int i = 0; i < mworkers.length; ++i) {
	    MapWorker mworker = mworkers[i];
	    mworker.setMapreducer(conf.getMapReduce());
	    mworker.map();
	    mworker.partition();
	}
	
	// register intermediate output from map workers to master
	System.printString("Mapoutput\n");
	for(int i = 0; i < mworkers.length; ++i) {
	    for(int j = 0; j < conf.getR(); ++j) {
		String temp = mworkers[i].outputFile(j);
		if(temp != null) {
		    master.addInterOutput(temp);
		}
	    }
	    master.setMapFinish(mworkers[i].getID());
	}
	//assert(master.isMapFinish());
	
	// do 'reduce'
	System.printString("Reduce\n");
	ReduceWorker[] rworkers = master.assignReduce();
	for(int i = 0; i < rworkers.length; ++i) {
	    ReduceWorker rworker = rworkers[i];
	    rworker.setMapreducer(conf.getMapReduce());
	    rworker.sortgroup();
	    rworker.reduce();
	}
	
	// merge all the intermediate output from reduce workers to master
	System.printString("Merge\n");
	for(int i = 0; i < rworkers.length; ++i) {
	    master.collectROutput(rworkers[i].getOutputFile());
	    master.setReduceFinish(rworkers[i].getID());
	}
	//assert(master.isReduceFinish());
	
	System./*out.println*/printString("Finish! Results are in the output file: " + master.getOutputFile() + "\n");
    }
    
}
