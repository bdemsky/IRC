task t1(StartupObject s{initialstate}) {
    System.printString("task t1\n");
    
    String inputfile = "Manila International Airport Authority spokesman Octavio Lina said there were no injuries, but some of the 345 passengers vomited after disembarking, AP reported. Video of the incident shows passengers applauding as the plane landed safely.";
    int m = 6;
    int r = 3;
    char seperator = '\n';
    Splitter splitter = new Splitter(inputfile, m, seperator);
    Master master = new Master(m, r, splitter){split};

    taskexit(s{!initialstate});
}

//Split the input file into M pieces
task t2(Master master{split}) {
    System.printString("task t2\n");
    
    master.split();

    taskexit(master{!split, assignMap});
}

//Select a map worker to handle one of the pieces of input file
task t3(Master master{assignMap}) {
    System.printString("task t3\n");
    
    //master.assignMap();
    Splitter splitter = master.getSplitter();
    String[] contentsplits = splitter.getSlices();
    for(int i = 0; i < contentsplits.length; ++i) {
	MapWorker mworker = new MapWorker(splitter.getFilename(), contentsplits[i], master.getR(), i){map};
	master.setMapInit(i);
    }

    taskexit(master{!assignMap, mapoutput});
}

//MapWorker do 'map' function on a input file piece
task t4(MapWorker mworker{map}) {
    System.printString("task t4\n");
    
    mworker.map();

    taskexit(mworker{!map, partition});
}

//Partition the intermediate key/value pair generated
//into R intermediate local files
task t5(MapWorker mworker{partition}) {
    System.printString("task t5\n");
    
    mworker.partition();

    taskexit(mworker{!partition, mapoutput});
}

//Register the intermediate ouput from map worker to master
task t6(Master master{mapoutput}, MapWorker mworker{mapoutput}) {
    System.printString("task t6\n");
    
    int total = master.getR();
    for(int i = 0; i < total; ++i) {
	OutputCollector temp = mworker.outputFile(i);
	if(temp != null) {
	    master.addInterOutput(temp, i); 
	}
    }
    master.setMapFinish(mworker.getID());

    if(master.isMapFinish()) {
	taskexit(master{!mapoutput, mapfinished, assignReduce}, mworker{!mapoutput});
    }

    taskexit(mworker{!mapoutput});
}

//Assign the list of intermediate output associated to one key to
//a reduce worker 
task t7(Master master{assignReduce}) {
    System.printString("task t7\n");
    
    //master.assignReduce();
    Vector[] interoutputs = master.getInteroutputs();
    for(int i = 0; i < interoutputs.length; ++i) {
	ReduceWorker rworker = new ReduceWorker(interoutputs[i], i){sortgroup};
	master.setReduceInit(i);
    }

    taskexit(master{!assignReduce, reduceoutput});
}

//First do sort and group on the intermediate key/value pairs assigned
//to reduce worker
task t8(ReduceWorker rworker{sortgroup}) {
    System.printString("task t8\n");
    
    rworker.sortgroup();

    taskexit(rworker{!sortgroup, reduce});
}

//Do 'reduce' function
task t9(ReduceWorker rworker{reduce}) {
    System.printString("task t9\n");
    
    rworker.reduce();

    taskexit(rworker{!reduce, reduceoutput});
}

//Collect the output into master
task t10(Master master{reduceoutput}, ReduceWorker rworker{reduceoutput}) {
    System.printString("task t10\n");
    
    master.collectROutput(rworker.getOutput());
    master.setReduceFinish(rworker.getID());

    if(master.isReduceFinish()) {
	taskexit(master{!reduceoutput, reducefinished, output}, rworker{!reduceoutput});
    }

    taskexit(rworker{!reduceoutput});
}

task t11(Master master{output}) {
    System.printString("task t11\n");
    
    /*if(master.isPartial()) {
	System.printString("Partial! The result may not be right due to some failure!\n");
    }*/
    System.printString("Finish!\n");// Results are in the output file: " + master.getOutputFile() + "\n");
    System.printI(0xdddd);
    taskexit(master{!output});
}
