class AppDemoRunner {
    flag run;
    flag turnin;
    
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
    ToInitAllTasks toinitalltasks;
    public Vector results;

    public AppDemoRunner(int id,int nRunsMC, int group, ToInitAllTasks initalltask) {
        this.id = id;
        this.nRunsMC=nRunsMC;
        this.group = group;
        this.results = new Vector();
        
        this.header = initalltask.get_header();
        this.name = initalltask.get_name();
        this.startDate = initalltask.get_startDate();
        this.endDate = initalltask.get_endDate();
        this.dTime = initalltask.get_dTime();
        this.returnDefinition = initalltask.get_returnDefinition();
        this.expectedReturnRate = initalltask.get_expectedReturnRate();
        this.volatility = initalltask.get_volatility();
        this.nTimeSteps = initalltask.get_nTimeSteps();
        this.pathStartValue = initalltask.get_pathStartValue();
    }

    public void run() {
        // Now do the computation.
        int ilow, iupper, slice;

        slice = (nRunsMC + this.group-1)/this.group;

        ilow = id*slice;
        iupper = (id+1)*slice;
        if (id==this.group-1) {
            iupper=nRunsMC;
        }
        //System.printI(0xba0);

        for(int iRun=ilow; iRun < iupper; iRun++ ) {
            //System.printI(0xba1);
            String header="MC run "+String.valueOf(iRun);
            PriceStock ps = new PriceStock();
            //System.printI(0xba2);
            ps.setInitAllTasks(this);
            ps.setTask(header, (long)iRun*11);
            //System.printI(0xba3);
            ps.run();
            //System.printI(0xba4);
            results.addElement(ps.getResult());
            //System.printI(0xba5);
        }
    }
}