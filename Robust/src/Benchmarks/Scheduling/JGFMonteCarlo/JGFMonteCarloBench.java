/** Banboo Version  **/

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
    
    int datasize = 1000;  //should be times of 2
    int nruns = 62 * 7 * 2;  //16 * 16;
    int group = 62 * 2; // 16;
    
    AppDemo ad = new AppDemo(datasize, nruns, group){merge};
    
    ad.initSerial();
    
    for(int i = 0; i < group; i++) {
	AppDemoRunner adr = new AppDemoRunner(i, 
		                              nruns, 
		                              group, 
		                              ad){run};
    }
    
    taskexit(s{!initialstate});
}

task t2(AppDemoRunner adr{run}) {
    //System.printString("task t2\n");
    
    //  Now do the computation.
    adr.run();
    
    taskexit(adr{!run, turnin});
}

task t3(AppDemo ad{merge}, AppDemoRunner adr{turnin}) {
    //System.printString("task t3\n");
    boolean isFinish = ad.processResults(adr.results);
    
    if(isFinish) {
	taskexit(ad{!merge, validate}, adr{!turnin});
    }
    taskexit(adr{!turnin});
}

task t4(AppDemo ad{validate}) {
    //System.printString("task t4\n");
    float refval = (float)(-0.0333976656762814);
    float dev = Math.abs(ad.JGFavgExpectedReturnRateMC - refval);
    if (dev > 1.0e-12 ){
      //System.printString("Validation failed");
      //System.printString(" expectedReturnRate= " + (int)(ad.JGFavgExpectedReturnRateMC*10000) + "  " + (int)(dev*10000) + "\n");
    }
    taskexit(ad{!validate});
}

