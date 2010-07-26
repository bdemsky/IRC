/** Bamboo Version  
 * Ported by: Jin Zhou  07/25/10
 * 
 * This is ported from the NoFib, originally written in Haskell
 * **/

task t1(StartupObject s{initialstate}) {
  //System.printString("task t1\n");

  int threadnum = 62; // 56;
  int npoints = 32000; //30000;
  for(int i = 0; i < threadnum; ++i) {
    TestRunner tr = new TestRunner(npoints){run};
  }

  taskexit(s{!initialstate});
}

task t2(TestRunner tr{run}) {
  //System.printString("task t2\n");
  tr.run();
  taskexit(tr{!run});
}