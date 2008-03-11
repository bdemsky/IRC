import java.io.FileInputStream;

public class PERT {

    int stageNum;
    Stage[] stages;
    Estimator estimator;


    public PERT() {
	this.stageNum = -1;
	//this.stages = null;
	//this.estimator = null;
    }

    public Estimator getEstimator() {
	return estimator;
    }

    public void setEstimator(Estimator estimator) {
	this.estimator = estimator;
    }

    public void setStageNum(int stageNum) {
        this.stageNum = stageNum;
    }

    public void createStages() {
	this.stages = new Stage[this.stageNum];
	for(int i = 0; i < stageNum; ++i) {
	    this.stages[i] = new Stage(i);
	}
    }

    public void sampling() {
	for(int i = 0; i < this.stageNum; ++i) {
	    this.stages[i].sampling();
	}
    }

    public void estimate() {
	for(int i = 0; i < this.stageNum; ++i) {
	    this.stages[i].estimate();
	}
    }

    public void merge() {
	for(int i = 0; i < this.stageNum; ++i) {
	    Stage tmp = this.stages[i];
	    this.estimator.estimate(tmp.getAntTime(), tmp.getAntVariance2(), false);
	}
    }

    public static void main(String args[]) {
//	try{
	    PERT pert = new PERT();

	    String path = new String("/home/jzhou/pert/conf.txt");
	    FileInputStream iStream = new FileInputStream(path);
	    byte[] b = new byte[1024];
	    int length = iStream.read(b);
	    if(length < 0) {
		System./*out.println*/printString("Error! Can not read from configure file: " + path + "\n");
		System.exit(-1);
	    }
	    iStream.close();
	    String content = new String(b, 0, length);
	    int index = content.indexOf('\n');
	    int stage = Integer.parseInt(content.substring(0, index));
	    Estimator estimator = new Estimator(stage);
	    pert.setStageNum(stage);
	    pert.setEstimator(estimator);
	    pert.createStages();
	    pert.sampling();
	    pert.estimate();
	    pert.merge();
	    path = new String("/home/jzhou/pert/prob.txt");
	    iStream = new FileInputStream(path);
	    byte c[] = new byte[1024];
	    length = iStream.read(c);
	    if(length < 0) {
		System./*out.println*/printString("Error! Can not read from input file: " + path + "\n");
		System.exit(-1);
	    }
	    iStream.close();
	    content = new String(c, 0, length);
	    index = content.indexOf('\n');
	    int x = Integer.parseInt(content.substring(0, index));
	    content = content.substring(index + 1);
	    index = content.indexOf('\n');
	    int y = Integer.parseInt(content.substring(0, index));
	    //System.out.println("x: " + x + "; y: " + y);
	    System./*out.println*/printString("The anticipate days need to finish this project is: " + pert.getEstimator().getTime() + "\n");
	    System./*out.println*/printString("And the anticipate variance is: " + (int)(pert.getEstimator().getVariance()*100) + "(/100)\n");
	    double prob = pert.getEstimator().getProbability(x, y);

	    System./*out.println*/printString("The probability of this project to be finished in " + x + " to " + y + " days is: " + (int)(prob*100) + "(/100)\n");
/*	} catch(Exception e) {
	    e.printStackTrace();
	    System.exit(-1);
	}*/
    }

}
