public class TransSim {
  public static void main(String[] args) {
    int numThreads=4;
    int numTrans=8;
    int deltaTrans=0;
    int numObjects=10;
    int numAccesses=4;
    int deltaAccesses=2;
    int readPercent=50;
    //time for operation
    int delay=20;
    int deltaDelay=4;
    //time between transactions
    int nonTrans=20;
    int deltaNonTrans=4;
    Executor e=new Executor(numThreads, numTrans, deltaTrans, numObjects, numAccesses, deltaAccesses, readPercent, delay, deltaDelay, nonTrans, deltaNonTrans);
    System.out.println(e.maxTime());
    FlexScheduler ls=new FlexScheduler(e, FlexScheduler.LAZY);
    ls.dosim();
    System.out.println("Lazy Time="+ls.getTime());
    System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
    int besttime=ls.getTime();

    //Kill others at commit
    ls=new FlexScheduler(e, FlexScheduler.COMMIT);
    ls.dosim();
    System.out.println("Fast Abort="+ls.getTime());
    System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
    if (ls.getTime()<besttime)
      besttime=ls.getTime();

    //Eager attack
    ls=new FlexScheduler(e, FlexScheduler.ATTACK);
    ls.dosim();
    System.out.println("Attack Abort="+ls.getTime());
    System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
    if (ls.getTime()<besttime)
      besttime=ls.getTime();

    //Eager polite
    ls=new FlexScheduler(e, FlexScheduler.POLITE);
    ls.dosim();
    System.out.println("Polite Abort="+ls.getTime());
    System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
    if (ls.getTime()<besttime)
      besttime=ls.getTime();
    
    //Karma
    ls=new FlexScheduler(e, FlexScheduler.KARMA);
    ls.dosim();
    System.out.println("Karma Abort="+ls.getTime());
    System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
    if (ls.getTime()<besttime)
      besttime=ls.getTime();

    Scheduler s=new Scheduler(e, besttime);
    s.dosim();
    System.out.println("Optimal Time="+s.getTime());
  }
}