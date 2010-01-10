public class TransSim {
  public static void main(String[] args) {
    int numThreads=20;
    int numTrans=40;
    int deltaTrans=0;
    int numObjects=200;
    int numAccesses=20;
    int deltaAccesses=0;
    int readPercent=0;
    //time for operation
    int delay=20;
    int deltaDelay=4;
    //time between transactions
    int nonTrans=20;
    int deltaNonTrans=4;
    //split objects
    int splitobjects=200;//10 percent of objects special
    int splitaccesses=100;//40 percent of accesses to special objects
    int readPercentSecond=30;//20 percent of accesses are reads
    int abortThreshold=0; //need 4 aborts to declare risky
    int abortRatio=0;//need 40% aborts vs commits to declare risky
    int deadlockdepth=10;

    Plot p=new Plot("plot");

    for(int i=1;i<30;i++) {
      System.out.println("i="+i);
      numThreads=i;
      Executor e=new Executor(numThreads, numTrans, deltaTrans, numObjects, numAccesses, deltaAccesses, readPercent, delay, deltaDelay, nonTrans, deltaNonTrans, splitobjects, splitaccesses, readPercentSecond);
      System.out.println(e.maxTime());
      FlexScheduler ls=new FlexScheduler(e, FlexScheduler.LAZY, null);
      ls.dosim();
      System.out.println("Lazy Time="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("LAZY").addPoint(i, ls.getTime());


      //Lock object accesses
      ls=new FlexScheduler(e, FlexScheduler.LOCK, abortThreshold, abortRatio, deadlockdepth, null);
      ls.dosim();
      System.out.println("Deadlock count="+ls.getDeadLockCount());
      System.out.println("Lock Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("LOCK").addPoint(i, ls.getTime());

      //Lock Commit object accesses
      ls=new FlexScheduler(e, FlexScheduler.LOCKCOMMIT, abortThreshold, abortRatio, deadlockdepth, null);
      ls.dosim();
      System.out.println("Deadlock count="+ls.getDeadLockCount());
      System.out.println("LockCommit Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("LOCKCOMMIT").addPoint(i, ls.getTime());

      //Kill others at commit
      ls=new FlexScheduler(e, FlexScheduler.COMMIT, null);
      ls.dosim();
      System.out.println("Fast Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("COMMIT").addPoint(i, ls.getTime());
      
      //Eager attack
      ls=new FlexScheduler(e, FlexScheduler.ATTACK, null);
      ls.dosim();
      System.out.println("Attack Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("ATTACK").addPoint(i, ls.getTime());

      //Eager polite
      ls=new FlexScheduler(e, FlexScheduler.SUICIDE, null);
      ls.dosim();
      System.out.println("Suicide Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("SUICIDE").addPoint(i, ls.getTime());

      //Karma
      ls=new FlexScheduler(e, FlexScheduler.TIMESTAMP, null);
      ls.dosim();
      System.out.println("Timestamp Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("TIMESTAMP").addPoint(i, ls.getTime());

      //Karma
      ls=new FlexScheduler(e, FlexScheduler.RANDOM, null);
      ls.dosim();
      System.out.println("Random Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("RANDOM").addPoint(i, ls.getTime());

      //Karma
      ls=new FlexScheduler(e, FlexScheduler.KARMA, null);
      ls.dosim();
      System.out.println("Karma Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("KARMA").addPoint(i, ls.getTime());

      //Karma
      ls=new FlexScheduler(e, FlexScheduler.POLITE, null);
      ls.dosim();
      System.out.println("Polit Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("POLITE").addPoint(i, ls.getTime());

      //Karma
      ls=new FlexScheduler(e, FlexScheduler.ERUPTION, null);
      ls.dosim();
      System.out.println("Eruption Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      p.getSeries("ERUPTION").addPoint(i, ls.getTime());

      //    Scheduler s=new Scheduler(e, besttime);
      //s.dosim();
      //System.out.println("Optimal Time="+s.getTime());
    }
    p.close();
  }
}