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
    
    int datasize = 16;
    for(int i = 0; i < datasize; ++i) {
	SeriesRunner sr = new SeriesRunner(i){!finish};
    }
    
    taskexit(s{!initialstate});
}

task t2(SeriesRunner sr{!finish}) {
    //System.printString("task t2\n");
    sr.run();
    taskexit(sr{finish});
}
