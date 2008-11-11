/** Banboo Version */

/**************************************************************************
*                                                                         *
*         Java Grande Forum Benchmark Suite - Thread Version 1.0          *
*                                                                         *
*                            produced by                                  *
*                                                                         *
*                  Java Grande Benchmarking Project                       *
*                                                                         *
*                                at                                       *
*                                                                         *
*                Edinburgh Parallel Computing Centre                      *
*                                                                         * 
*                email: epcc-javagrande@epcc.ed.ac.uk                     *
*                                                                         *
*                                                                         *
*      This version copyright (c) The University of Edinburgh, 2001.      *
*                         All rights reserved.                            *
*                                                                         *
**************************************************************************/

task t1(StartupObject s{initialstate}) {
    //System.printString("task t1\n");
    
    int datasize = 2;
    int group = 16;
    
    MD md = new MD(datasize, group){initialise};
    
    taskexit(s{!initialstate});
}

task t2(MD md{initialise}) {
    //System.printString("task t2\n");
    md.initialise();
    taskexit(md{!initialise, move});
}

task t3(MD md{move}) {
    //System.printString("task t3\n");
    
    md.domove();
    md.init();
    //System.printI(0xd0);
    for(int i = 0; i < md.group; ++i) {
	MDRunner runner = new MDRunner(i, md){run};
	runner.init();
	//System.printI(0xd1);
    }
    //System.printI(0xd2);
    taskexit(md{!move,update});
}

/*task t4(MD md{fire}, MDRunner runner{wait}) {
    System.printString("task t4\n");
    
    runner.init();
    md.counter++;
    //System.printString("counter: " + md.counter + "\n");
    if(md.counter == md.group) {
	//System.printString("Fire finished: " + md.move + "\n");
	taskexit(md{!fire, update}, runner{!wait, run});
    } else {
	taskexit(md{fire}, runner{!wait, run});
    }
}*/

task t5(MDRunner runner{run}) {
    //System.printString("task t5\n");
    
    runner.run();
    
    taskexit(runner{update, !run});
}

task t6(MD md{update}, MDRunner runner{update}) {
    //System.printString("task t6\n");
    
    md.update(runner);
    md.counter++;
    if(md.counter == md.group) {
	md.sum();
	md.scale();
	if(md.finish()) {
	    taskexit(md{!update, validate}, runner{!update/*, scale*/});
	} else {
	    taskexit(md{!update, move}, runner{!update/*, scale*/});
	}
    } else {
	taskexit(md{update}, runner{!update/*, scale*/});
    }
}

/*task t7(MD md{scale}, MDRunner runner{scale}) {
    //System.printString("task t7\n");
    
    md.counter--;
    
    if(md.counter == 0) {
	if(md.finish()) {
	    // finished
	    taskexit(md{!scale, validate}, runner{!scale, !wait});
	}
	taskexit(md{!scale, move}, runner{!scale, !wait});
    } else {
	if(md.finish()) {
	    taskexit(md{scale}, runner{!scale, !wait});
	}
	taskexit(md{scale}, runner{!scale, !wait});
    }
}*/

task t8(MD md{validate}) {
    //System.printString("task t8\n");
    
    md.validate();
    
    taskexit(md{!validate, finish});
}
 
