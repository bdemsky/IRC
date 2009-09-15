public class TransSim {
  public static void main(String[] args) {
    int numThreads=1;
    int numTrans=4;
    int deltaTrans=0;
    int numObjects=400;
    int numAccesses=10;
    int deltaAccesses=0;
    int readPercent=0;
    //time for operation
    int delay=20;
    int deltaDelay=4;
    //time between transactions
    int nonTrans=20;
    int deltaNonTrans=4;
    //split objects
    int splitobjects=100;//10 percent of objects special
    int splitaccesses=100;//40 percent of accesses to special objects
    int readPercentSecond=30;//20 percent of accesses are reads
    int abortThreshold=0; //need 4 aborts to declare risky
    int abortRatio=0;//need 40% aborts vs commits to declare risky
    int deadlockdepth=10;

    long tlazy=0, tcommit=0, tattack=0, tpolite=0, tkarma=0;

    for(int i=1;i<100;i++) {
      System.out.println("i="+i);
      Executor e=new Executor(i, numTrans, deltaTrans, numObjects, numAccesses, deltaAccesses, readPercent, delay, deltaDelay, nonTrans, deltaNonTrans, splitobjects, splitaccesses, readPercentSecond);
      System.out.println(e.maxTime());
      FlexScheduler ls=new FlexScheduler(e, FlexScheduler.LAZY);
      ls.dosim();
      System.out.println("Lazy Time="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      int besttime=ls.getTime();
      tlazy+=ls.getTime();

      //Lock object accesses
      ls=new FlexScheduler(e, FlexScheduler.LOCK, abortThreshold, abortRatio, deadlockdepth);
      ls.dosim();
      System.out.println("Deadlock count="+ls.getDeadLockCount());
      System.out.println("Lock Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      if (ls.getTime()<besttime)
	besttime=ls.getTime();
      tcommit+=ls.getTime();

      //Lock Commit object accesses
      ls=new FlexScheduler(e, FlexScheduler.LOCKCOMMIT, abortThreshold, abortRatio, deadlockdepth);
      ls.dosim();
      System.out.println("Deadlock count="+ls.getDeadLockCount());
      System.out.println("LockCommit Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      if (ls.getTime()<besttime)
	besttime=ls.getTime();
      tcommit+=ls.getTime();

      //Kill others at commit
      ls=new FlexScheduler(e, FlexScheduler.COMMIT);
      ls.dosim();
      System.out.println("Fast Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      if (ls.getTime()<besttime)
	besttime=ls.getTime();
      tcommit+=ls.getTime();
      
      //Eager attack
      ls=new FlexScheduler(e, FlexScheduler.ATTACK);
      ls.dosim();
      System.out.println("Attack Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      if (ls.getTime()<besttime)
	besttime=ls.getTime();
      tattack+=ls.getTime();      

      //Eager polite
      ls=new FlexScheduler(e, FlexScheduler.POLITE);
      ls.dosim();
      System.out.println("Polite Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      if (ls.getTime()<besttime)
	besttime=ls.getTime();
      tpolite+=ls.getTime();      

      //Karma
      ls=new FlexScheduler(e, FlexScheduler.KARMA);
      ls.dosim();
      System.out.println("Karma Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      if (ls.getTime()<besttime)
	besttime=ls.getTime();
      tkarma+=ls.getTime();
      //    Scheduler s=new Scheduler(e, besttime);
      //s.dosim();
      //System.out.println("Optimal Time="+s.getTime());
    }
    System.out.println("lazy="+tlazy);
    System.out.println("commit="+tcommit);
    System.out.println("attack="+tattack);
    System.out.println("polite="+tpolite);
    System.out.println("karma="+tkarma);
  }
}