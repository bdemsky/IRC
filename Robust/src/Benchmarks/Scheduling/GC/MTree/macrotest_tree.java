task t1(StartupObject s{initialstate}) {
	//System.printString("task t1\n");
	
	int threadnum = 62;
    int size = 20000;
    int nodenum = size*10;
	for(int i = 0; i < threadnum; ++i) {
		TestRunner tr = new TestRunner(i, size, nodenum){run};
	}

	taskexit(s{!initialstate});
}

task t2(TestRunner tr{run}) {
	//System.printString("task t2\n");
	tr.run();
	taskexit(tr{!run});
}
