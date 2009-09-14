public class TransSim {
  public static void main(String[] args) {
    int numThreads=32;
    int numTrans=100;
    int deltaTrans=0;
    int numObjects=500;
    int numAccesses=20;
    int deltaAccesses=5;
    int readPercent=30;
    //time for operation
    int delay=20;
    int deltaDelay=4;
    //time between transactions
    int nonTrans=20;
    int deltaNonTrans=4;

    long tlazy=0, tcommit=0, tattack=0, tpolite=0, tkarma=0;
    for(int i=0;i<100;i++) {
      Executor e=new Executor(numThreads, numTrans, deltaTrans, numObjects, numAccesses, deltaAccesses, readPercent, delay, deltaDelay, nonTrans, deltaNonTrans);
      System.out.println(e.maxTime());
      FlexScheduler ls=new FlexScheduler(e, FlexScheduler.LAZY);
      ls.dosim();
      System.out.println("Lazy Time="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      int besttime=ls.getTime();
      tlazy+=ls.getTime();

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