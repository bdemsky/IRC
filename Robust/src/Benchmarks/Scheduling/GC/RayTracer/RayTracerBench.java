task t1(StartupObject s{initialstate}) {
  //System.printString("task t1\n");

  int threadnum = 56; //62;
  int size = threadnum * 25;
  Composer comp = new Composer(threadnum, size){compose};
  RayTracer rt = new RayTracer();
  Scene scene = rt.createScene();
  for(int i = 0; i < threadnum; ++i) {
    TestRunner tr = new TestRunner(i, threadnum, size, scene){run};
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
    long r = comp.result;
    taskexit(comp{!compose}, tr{!compose});
  } else {
    taskexit(tr{!compose});
  }
}
