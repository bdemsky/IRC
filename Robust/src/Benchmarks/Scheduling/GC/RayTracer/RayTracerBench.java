task t1(StartupObject s{initialstate}) {
  //System.printString("task t1\n");

  int threadnum = 62;
  int size = threadnum * 20;
  Composer comp = new Composer(threadnum, size){compose};
  for(int i = 0; i < threadnum; ++i) {
    TestRunner tr = new TestRunner(i, threadnum, size){run};
  }

  taskexit(s{!initialstate});
}

task t2(TestRunner tr{run}) {
  //System.printString("task t2\n");
  tr.run();
  taskexit(tr{!run, compose});
}

task t3(Composer comp{compose}, TestRunner tr{compose}) {
  //System.printString("task t3\n");
  if(comp.compose(tr)) {
    taskexit(comp{!compose}, tr{!compose});
  } else {
    taskexit(tr{!compose});
  }
}
