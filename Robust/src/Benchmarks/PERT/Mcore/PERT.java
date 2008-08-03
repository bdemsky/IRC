task t1(StartupObject s{initialstate}) {
    //System.printString("task t1\n");
    int stages = 2;
    Estimator estimator = new Estimator(stages){estimate};
    for(int i = 0; i < stages; ++i) {
	Stage stage = new Stage(i){sampling};
    }

    taskexit(s{!initialstate});
}

task t2(Stage s{sampling}) {
    //System.printString("task t2\n");

    s.sampling();

    taskexit(s{!sampling, estimate});
}

task t3(Stage s{estimate}) {
    //System.printString("task t3\n");

    s.estimate();

    taskexit(s{!estimate, merge});
}

task t4(Estimator e{estimate}, Stage s{merge}) {
    //System.printString("task t4\n");

    boolean fake = false;
    boolean finish = e.estimate(s.getAntTime(), s.getAntVariance2());

    if(finish) {
	//System.printI(0xff40);
	taskexit(e{!estimate, prob}, s{!merge});
    } else {
	//System.printI(0xff41);
	taskexit(s{!merge});
    }
}

task t5(Estimator e{prob}) {
    //System.printString("task t5\n");

    int x = 10;
    int y = 20;
    //System.printString("x: " + x + "; y: " + y + "\n");
    //System.printString("The anticipate days need to finish this project is: " + e.getTime() + "\n");
    //System.printString("And the anticipate variance is: " + (int)(e.getVariance()*100) + "(/100)\n");
    float prob = e.getProbability(x, y);

    //System.printString("The probability of this project to be finished in " + x + " to " + y + " days is: " + (int)(prob*100) + "%\n");
    //System.printI((int)(prob*100));
    taskexit(e{!prob});
}
