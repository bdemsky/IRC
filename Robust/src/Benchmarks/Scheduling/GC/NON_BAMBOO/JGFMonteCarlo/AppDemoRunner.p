package JGFMonteCarlo;

public class AppDemoRunner extends Thread {

  public String header;
  public String name;
  public int startDate;
  public int endDate;
  public float dTime;
  public int returnDefinition;
  public float expectedReturnRate;
  public float volatility;
  public int nTimeSteps;
  public float pathStartValue;

  int id, nRunsMC, group;
  //ToInitAllTasks toinitalltasks;
  public Vector results;

  public AppDemoRunner(int id,
      int nRunsMC, 
      int group, 
      AppDemo ad
      /*ToInitAllTasks initalltask*/) {
    this.id = id;
    this.nRunsMC=nRunsMC;
    this.group = group;
    this.results = new Vector();

    //this.header = initalltask.header;
    this.name = ad.name;
    this.startDate = ad.startDate;
    this.endDate = ad.endDate;
    this.dTime = ad.dTime;
    this.returnDefinition = ad.returnDefinition;
    this.expectedReturnRate = ad.expectedReturnRate;
    this.volatility = ad.volatility;
    this.nTimeSteps = ad.nTimeStepsMC;
    this.pathStartValue = ad.pathStartValue;
  }

  public void run() {
    // Now do the computation.
    int ilow, iupper, slice;
    int gp = this.group;
    int index = this.id;
    int nruns = this.nRunsMC;

    slice = (nruns + gp-1)/gp;

    ilow = index*slice;
    iupper = (index+1)*slice;
    if (index==gp-1) {
      iupper=nruns;
    }

    for(int iRun=ilow; iRun < iupper; iRun++ ) {
      //String header="MC run "+String.valueOf(iRun);
      PriceStock ps = new PriceStock();
      ps.setInitAllTasks(this);
      ps.setTask(/*header, */(long)iRun*11);
      ps.run();
      results.addElement(ps.getResult());
    }
  }

  public static void main(String[] args) {
    int datasize = 10000;  //should be times of 2
    //int nruns = 62 * 62;  //16 * 16;
    int group = THREADNUM; // 16;
    int nruns = group * 62;

    System.setgcprofileflag();
    AppDemo ad = new AppDemo(datasize, nruns, group);
    ad.initSerial();

    AppDemoRunner adrarray[]=new AppDemoRunner[group];
    for(int i = 1; i < group; ++i) {
      AppDemoRunner adr = new AppDemoRunner(i, nruns, group, ad);
      adr.start();
      trarray[i]=adr;
    }
    AppDemoRunner adr0 = new AppDemoRunner(i, nruns, group, ad);
    adr0.start();
    for(int i = 1; i < group; ++i) {
      adrarray[i].join();
    }
  }
}
