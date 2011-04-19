/** Bamboo Version  
 * Ported by: Jin Zhou  07/23/10
 * 
 * This is ported from the NoFib, originally written in Haskell
 * **/

task t1(StartupObject s{initialstate}) {
  //System.printString("task t1\n");

  int threadnum = 62; // 56;
  int[] args = new int[6];
  args[0] = 1;
  args[1] = 2;
  args[2] = 160; //0; // 1000;
  args[3] = 80; //0; // 500;
  args[4] = 81; //51; // 501;
  args[5] = 240; //0; // 1500;
  for(int i = 0; i < threadnum; ++i) {
    TestRunner tr = newflag TestRunner(args){run};
  }

  taskexit(s{!initialstate});
}

task t2(TestRunner tr{run}) {
  //System.printString("task t2\n");
  tr.run();
  taskexit(tr{!run});
}
