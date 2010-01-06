public class FileSim {
  public static void main(String[] args) throws Exception {
    //time between transactions
    //split objects
    int splitobjects=100;//10 percent of objects special
    int splitaccesses=100;//40 percent of accesses to special objects
    int abortThreshold=0; //need 4 aborts to declare risky
    int abortRatio=0;//need 40% aborts vs commits to declare risky
    int deadlockdepth=10;

    String filename=args[0];
    Executor e=new Executor(filename);
    System.out.println(e.maxTime());
    FlexScheduler ls=new FlexScheduler(e, FlexScheduler.LAZY, null);
    ls.start();

    
    
    //Kill others at commit
    FlexScheduler ls4=new FlexScheduler(e, FlexScheduler.COMMIT, null);
    ls4.start();

    
    //Eager attack
    FlexScheduler ls5=new FlexScheduler(e, FlexScheduler.ATTACK, null);
    ls5.start();

    
    //Eager polite
    FlexScheduler ls6=new FlexScheduler(e, FlexScheduler.POLITE, null);
    ls6.start();

    
    //Karma
    FlexScheduler ls7=new FlexScheduler(e, FlexScheduler.KARMA, null);
    ls7.start();


    ls.join();
    System.out.println("Lazy Time="+ls.getTime());
    System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());

    ls4.join();
    System.out.println("Fast Abort="+ls4.getTime());
    System.out.println("Aborts="+ls4.getAborts()+" Commit="+ls4.getCommits());

    ls5.join();
    System.out.println("Attack Abort="+ls5.getTime());
    System.out.println("Aborts="+ls5.getAborts()+" Commit="+ls5.getCommits());

    ls6.join();
    System.out.println("Polite Abort="+ls6.getTime());
    System.out.println("Aborts="+ls6.getAborts()+" Commit="+ls6.getCommits());

    ls7.join();
    System.out.println("Karma Abort="+ls7.getTime());
    System.out.println("Aborts="+ls7.getAborts()+" Commit="+ls7.getCommits());
    ls=null;ls4=null;ls5=null;ls6=null;ls7=null;
    
    {
    //Lock object accesses
      FlexScheduler ls2=new FlexScheduler(e, FlexScheduler.LOCK, abortThreshold, abortRatio, deadlockdepth, null);
      ls2.start();
      ls2.join();
      System.out.println("Deadlock count="+ls2.getDeadLockCount());
      System.out.println("Lock Abort="+ls2.getTime());
      System.out.println("Aborts="+ls2.getAborts()+" Commit="+ls2.getCommits());
      ls2=null;
    }
    {
      //Lock Commit object accesses
      FlexScheduler ls3=new FlexScheduler(e, FlexScheduler.LOCKCOMMIT, abortThreshold, abortRatio, deadlockdepth, null);
      ls3.start();
      ls3.join();
      System.out.println("Deadlock count="+ls3.getDeadLockCount());
      System.out.println("LockCommit Abort="+ls3.getTime());
      System.out.println("Aborts="+ls3.getAborts()+" Commit="+ls3.getCommits());
    }
  }
}