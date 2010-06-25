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

    for(int i=1;i<40;i++) {
      System.out.println("i="+i);
      numThreads=i;
      Executor e=new Executor(numThreads, numTrans, deltaTrans, numObjects, numAccesses, deltaAccesses, readPercent, delay, deltaDelay, nonTrans, deltaNonTrans, splitobjects, splitaccesses, readPercentSecond);
      System.out.println(e.maxTime());
      FlexScheduler ls=new FlexScheduler(e, FlexScheduler.LAZY, null);
      ls.dosim();
      System.out.println("Lazy Time="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("LAZY").addPoint(i, ls.getTime());
      pa.getSeries("LAZY").addPoint(i, 100.0*((double)ls.getAborts())/((double)(ls.getAborts()+ls.getCommits())));

      //Lock object accesses
      ls=new FlexScheduler(e, FlexScheduler.LOCK, abortThreshold, abortRatio, deadlockdepth, null);
      ls.dosim();
      System.out.println("Deadlock count="+ls.getDeadLockCount());
      System.out.println("Lock Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("LOCK").addPoint(i, ls.getTime());
      pa.getSeries("LOCK").addPoint(i, 100.0*((double)ls.getAborts())/((double)(ls.getAborts()+ls.getCommits())));

      //Lock Commit object accesses
      ls=new FlexScheduler(e, FlexScheduler.LOCKCOMMIT, abortThreshold, abortRatio, deadlockdepth, null);
      ls.dosim();
      System.out.println("Deadlock count="+ls.getDeadLockCount());
      System.out.println("LockCommit Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("LOCKCOMMIT").addPoint(i, ls.getTime());
      pa.getSeries("LOCKCOMMIT").addPoint(i, 100.0*((double)ls.getAborts())/((double)(ls.getAborts()+ls.getCommits())));

      //Kill others at commit
      ls=new FlexScheduler(e, FlexScheduler.COMMIT, null);
      ls.dosim();
      System.out.println("Fast Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("COMMIT").addPoint(i, ls.getTime());
      pa.getSeries("COMMIT").addPoint(i, 100.0*((double)ls.getAborts())/((double)(ls.getAborts()+ls.getCommits())));      

      //Eager attack
      ls=new FlexScheduler(e, FlexScheduler.ATTACK, null);
      ls.dosim();
      System.out.println("Attack Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("ATTACK").addPoint(i, ls.getTime());
      pa.getSeries("ATTACK").addPoint(i, 100.0*((double)ls.getAborts())/((double)(ls.getAborts()+ls.getCommits())));

      //Eager polite
      ls=new FlexScheduler(e, FlexScheduler.SUICIDE, null);
      ls.dosim();
      System.out.println("Suicide Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("SUICIDE").addPoint(i, ls.getTime());
      pa.getSeries("SUICIDE").addPoint(i, 100.0*((double)ls.getAborts())/((double)(ls.getAborts()+ls.getCommits())));

      //Karma
      ls=new FlexScheduler(e, FlexScheduler.TIMESTAMP, null);
      ls.dosim();
      System.out.println("Timestamp Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("TIMESTAMP").addPoint(i, ls.getTime());
      pa.getSeries("TIMESTAMP").addPoint(i, 100.0*((double)ls.getAborts())/((double)(ls.getAborts()+ls.getCommits())));

      //Karma
      ls=new FlexScheduler(e, FlexScheduler.RANDOM, null);
      ls.dosim();
      System.out.println("Random Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("RANDOM").addPoint(i, ls.getTime());
      pa.getSeries("RANDOM").addPoint(i, 100.0*((double)ls.getAborts())/((double)(ls.getAborts()+ls.getCommits())));

      //Karma
      ls=new FlexScheduler(e, FlexScheduler.KARMA, null);
      ls.dosim();
      System.out.println("Karma Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("KARMA").addPoint(i, ls.getTime());
      pa.getSeries("KARMA").addPoint(i, 100.0*((double)ls.getAborts())/((double)(ls.getAborts()+ls.getCommits())));

      //Karma
      ls=new FlexScheduler(e, FlexScheduler.POLITE, null);
      ls.dosim();
      System.out.println("Polit Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("POLITE").addPoint(i, ls.getTime());
      pa.getSeries("POLITE").addPoint(i, 100.0*((double)ls.getAborts())/((double)(ls.getAborts()+ls.getCommits())));

      //Karma
      ls=new FlexScheduler(e, FlexScheduler.ERUPTION, null);
      ls.dosim();
      System.out.println("Eruption Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("ERUPTION").addPoint(i, ls.getTime());
      pa.getSeries("ERUPTION").addPoint(i, 100.0*((double)ls.getAborts())/((double)(ls.getAborts()+ls.getCommits())));


      //Karma
      ls=new FlexScheduler(e, FlexScheduler.THREAD, null);
      ls.dosim();
      System.out.println("ThreadPriority Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("THPRIORITY").addPoint(i, ls.getTime());
      pa.getSeries("THPRIORITY").addPoint(i, 100.0*((double)ls.getAborts())/((double)(ls.getAborts()+ls.getCommits())));


      //attack time
      ls=new FlexScheduler(e, FlexScheduler.ATTACKTIME, null);
      ls.dosim();
      System.out.println("ThreadPriority Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("ATTACKTIME").addPoint(i, ls.getTime());
      pa.getSeries("ATTACKTIME").addPoint(i, 100.0*((double)ls.getAborts())/((double)(ls.getAborts()+ls.getCommits())));


      //attack thread
      ls=new FlexScheduler(e, FlexScheduler.ATTACKTHREAD, null);
      ls.dosim();
      System.out.println("ThreadPriority Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("ATTACKTHREAD").addPoint(i, ls.getTime());
      pa.getSeries("ATTACKTHREAD").addPoint(i, 100.0*((double)ls.getAborts())/((double)(ls.getAborts()+ls.getCommits())));

      //    Scheduler s=new Scheduler(e, besttime);
      //s.dosim();
      //System.out.println("Optimal Time="+s.getTime());
    }
    p.close();
    pa.close();
  }
}