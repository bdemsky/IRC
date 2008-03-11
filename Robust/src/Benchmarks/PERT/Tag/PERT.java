task startup(StartupObject s{initialstate}) {

    // read in configuration parameters
    //System.printString("Top of task startup\n");
    String path = new String("/home/jzhou/pert/conf.txt");
    FileInputStream iStream = new FileInputStream(path);
    byte[] b = new byte[1024];
    int length = iStream.read(b);
    if(length < 0) {
	System.printString("Error! Can not read from configure file: " + path + "\n");
	System.exit(-1);
    }
    iStream.close();
    String content = new String(b, 0, length);
    int index = content.indexOf('\n');
    int stages = Integer.parseInt(content.subString(0, index));
    Estimator estimator = new Estimator(stages){estimate};
    for(int i = 0; i < stages; ++i) {
	Stage stage = new Stage(i){sampling};
    }

    taskexit(s{!initialstate});
}

task sampling(Stage s{sampling}) {
    //System.printString("Top of task sampling\n");

    s.sampling();

    taskexit(s{!sampling, estimate});
}

task estimateStage(Stage s{estimate}) {
    //System.printString("Top of task estimateStage\n");

    s.estimate();

    taskexit(s{!estimate, merge});
}

task estimate(Estimator e{estimate}, optional Stage s{merge}) {
    //System.printString("Top of task estimate\n");

    boolean fake = false;
    if(!isavailable(s)) {
	fake = true;
    }
    boolean finish = e.estimate(s.getAntTime(), s.getAntVariance2(), fake);

    if(finish) {
	taskexit(e{!estimate, prob}, s{!merge});
    } else {
	taskexit(s{!merge});
    }
}

task prob(Estimator e{prob}) {
    //System.printString("Top of task prob\n");

    if(e.isPartial()) {
	System.printString("There are some sampling data unavailable. The anticipate probability may be greater than it should be!\n");
    }

    String path = new String("/home/jzhou/pert/prob.txt");
    FileInputStream iStream = new FileInputStream(path);
    byte b[] = new byte[1024];
    int length = iStream.read(b);
    if(length < 0) {
	System.printString("Error! Can not read from input file: " + path + "\n");
	System.exit(-1);
    }
    iStream.close();
    String content = new String(b, 0, length);
    int index = content.indexOf('\n');
    int x = Integer.parseInt(content.subString(0, index));
    content = content.subString(index + 1);
    index = content.indexOf('\n');
    int y = Integer.parseInt(content.subString(0, index));
    //System.printString("x: " + x + "; y: " + y + "\n");
    System.printString("The anticipate days need to finish this project is: " + e.getTime() + "\n");
    System.printString("And the anticipate variance is: " + (int)(e.getVariance()*100) + "(/100)\n");
    double prob = e.getProbability(x, y);

    System.printString("The probability of this project to be finished in " + x + " to " + y + " days is: " + (int)(prob*100) + "%\n");
    taskexit(e{!prob});
}
