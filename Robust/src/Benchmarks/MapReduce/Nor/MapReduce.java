task startup(StartupObject s{initialstate}) {
    // read in configuration parameters
    // System.printString("Top of task startup\n");
    String path = new String("/scratch/mapreduce_nor/conf.txt");
    FileInputStream iStream = new FileInputStream(path);
    byte[] b = new byte[1024];
    int length = iStream.read(b);
    if(length < 0 ) {
	System.printString("Error! Can not read from configure file: " + path + "\n");
	System.exit(-1);
    }
    String content = new String(b, 0, length);
    //System.printString(content + "\n");
    int index = content.indexOf('\n');
    String inputfile = content.subString(0, index);
    content = content.subString(index + 1);
    index = content.indexOf('\n');
    int m = Integer.parseInt(content.subString(0, index));
    content = content.subString(index + 1);
    index = content.indexOf('\n');
    int r = Integer.parseInt(content.subString(0, index));
    content = content.subString(index + 1);
    index = content.indexOf('\n');
    String temp = content.subString(0, index);
    char seperator = temp.charAt(0);
    //System.printString(inputfile + "; " + String.valueOf(m) + "; " + String.valueOf(r) + "\n");
    Splitter splitter = new Splitter(inputfile, m, seperator);
    Master master = new Master(m, r, splitter){split};

    taskexit(s{!initialstate});
}

//Split the input file into M pieces
task split(Master master{split}) {
    System.printString("Top of task split\n");
    master.split();

    taskexit(master{!split, assignMap});
}

//Select a map worker to handle one of the pieces of input file
task assignMap(Master master{assignMap}) {
    System.printString("Top of task assignMap\n");
    master.assignMap();

    taskexit(master{!assignMap, mapoutput});
}

//MapWorker do 'map' function on a input file piece
task map(MapWorker mworker{map}) {
    System.printString("Top of task map\n");
    mworker.map();

    taskexit(mworker{!map, partition});
}

//Partition the intermediate key/value pair generated
//into R intermediate local files
task partition(MapWorker mworker{partition}) {
    System.printString("Top of task partition\n");
    mworker.partition();

    taskexit(mworker{!partition, mapoutput});
}

//Register the intermediate ouput from map worker to master
task mapOutput(Master master{mapoutput}, /*optional*/ MapWorker mworker{mapoutput}) {
    System.printString("Top of task mapOutput\n");
    //if(isavailable(mworker)) {
	int total = master.getR();
	for(int i = 0; i < total; ++i) {
	    String temp = mworker.outputFile(i);
	    if(temp != null) {
		master.addInterOutput(temp); 
	    }
	}
	master.setMapFinish(mworker.getID());
    /*} else {
	master.setMapFail(mworker.getID());
	master.setPartial(true);
    }*/
    if(master.isMapFinish()) {
	taskexit(master{!mapoutput, mapfinished, assignReduce}, mworker{!mapoutput});
    }

    taskexit(mworker{!mapoutput});
}

//Assign the list of intermediate output associated to one key to
//a reduce worker 
task assignReduce(Master master{assignReduce}) {
    System.printString("Top of task assignReduce\n");
    master.assignReduce();

    taskexit(master{!assignReduce, reduceoutput});
}

//First do sort and group on the intermediate key/value pairs assigned
//to reduce worker
task sortgroup(ReduceWorker rworker{sortgroup}) {
    System.printString("Top of task sortgroup\n");
    rworker.sortgroup();

    taskexit(rworker{!sortgroup, reduce});
}

//Do 'reduce' function
task reduce(ReduceWorker rworker{reduce}) {
    System.printString("Top of task reduce\n");
    rworker.reduce();

    taskexit(rworker{!reduce, reduceoutput});
}

//Collect the output into master
task reduceOutput(Master master{reduceoutput}, /*optional*/ ReduceWorker rworker{reduceoutput}) {
    System.printString("Top of task reduceOutput\n");
    //if(isavailable(rworker)) {
	master.collectROutput(rworker.getOutputFile());
	master.setReduceFinish(rworker.getID());
   /* } else {
	master.setReduceFail(rworker.getID());
	master.setPartial(true);
    }*/
    if(master.isReduceFinish()) {
	//System.printString("reduce finish\n");
	taskexit(master{!reduceoutput, reducefinished, output}, rworker{!reduceoutput});
    }

    taskexit(rworker{!reduceoutput});
}

task output(Master master{output}) {
    System.printString("Top of task output\n");
    if(master.isPartial()) {
	System.printString("Partial! The result may not be right due to some failure!\n");
    }
    System.printString("Finish! Results are in the output file: " + master.getOutputFile() + "\n");
    taskexit(master{!output});
}
