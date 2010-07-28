/** Bamboo Version  
 * Ported by: Jin Zhou  07/27/10
 * 
 * This is ported from the JOlden
 * **/

task t1(StartupObject s{initialstate}) {
  //System.printString("task t1\n");

  int threadnum = 62; // 56;
  int ncities = 4080;
  for(int i = 0; i < threadnum; ++i) {
    TestRunner tr = new TestRunner(ncities){run};
  }

  taskexit(s{!initialstate});
}

task t2(TestRunner tr{run}) {
  //System.printString("task t2\n");
  tr.run();
  taskexit(tr{!run});
}
