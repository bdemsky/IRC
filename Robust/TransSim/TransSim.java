public class TransSim {
  public static void main(String[] args) {
    int numThreads=20;
    int numTrans=40;
    int deltaTrans=0;
    int numObjects=400;
    int numAccesses=20;
    int deltaAccesses=3;
    int readPercent=80; //80 percent read
    //time for operation
    int delay=20;
    int deltaDelay=4;
    //time between transactions
    int nonTrans=20;
    int deltaNonTrans=4;
    //split objects
    int splitobjects=100;//100 percent normal objects
    int splitaccesses=100;//100 percent access to normal objects
    int readPercentSecond=80;//20 percent of accesses are reads
    int abortThreshold=0; //need 4 aborts to declare risky
    int abortRatio=0;//need 40% aborts vs commits to declare risky
    int deadlockdepth=10;

    Plot p=new Plot("plot");
    Plot pa=new Plot("plotabort");
    Plot ps=new Plot("plotstall");
    Plot pb=new Plot("plotbackoff");
    Plot pat=new Plot("plotaborttime");

    int[] policies=new int[]{FlexScheduler.LAZY, FlexScheduler.COMMIT, FlexScheduler.ATTACK, FlexScheduler.SUICIDE, FlexScheduler.TIMESTAMP, FlexScheduler.LOCK, FlexScheduler.LOCKCOMMIT, FlexScheduler.RANDOM, FlexScheduler.KARMA, FlexScheduler.POLITE, FlexScheduler.ERUPTION, FlexScheduler.THREAD, FlexScheduler.ATTACKTIME, FlexScheduler.ATTACKTHREAD};

    for(int i=1;i<40;i++) {
      System.out.println("i="+i);
      numThreads=i;
      Executor e=new Executor(numThreads, numTrans, deltaTrans, numObjects, numAccesses, deltaAccesses, readPercent, delay, deltaDelay, nonTrans, deltaNonTrans, splitobjects, splitaccesses, readPercentSecond);
      System.out.println(e.maxTime());

      for(int j=0;j<policies.length;j++) {
	int policy=policies[j];
	String policyname=FlexScheduler.getName(policy);
	FlexScheduler ls=new FlexScheduler(e, policy, null);
	ls.dosim();
	System.out.println("Deadlock count="+ls.getDeadLockCount());
	System.out.println(policyname+" Time="+ls.getTime());
	System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
	System.out.println("Stalltime="+ls.getStallTime()+" Backofftime="+ls.getBackoffTime());
	System.out.println("Aborttime="+ls.getAbortedTime());
	
	p.getSeries(policyname).addPoint(i, ls.getTime());
	pa.getSeries(policyname).addPoint(i, 100.0*((double)ls.getAborts())/((double)(ls.getAborts()+ls.getCommits())));
	ps.getSeries(policyname).addPoint(i, ls.getStallTime());
	pb.getSeries(policyname).addPoint(i, ls.getBackoffTime());
	pat.getSeries(policyname).addPoint(i, ls.getAbortedTime());
      }
    }
    p.close();
    pa.close();
  }
}