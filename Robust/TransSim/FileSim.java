public class FileSim {
  public static void p1(Executor e) throws Exception {
    FlexScheduler ls=new FlexScheduler(e, FlexScheduler.LAZY, null);
    ls.start();

    //Kill others at commit
    FlexScheduler ls4=new FlexScheduler(e, FlexScheduler.COMMIT, null);
    ls4.start();

    FlexScheduler ls5=new FlexScheduler(e, FlexScheduler.ATTACK, null);
    ls5.start();


    ls.join();
    System.out.println("Lazy Time="+ls.getTime());
    System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
    ls=null;

    ls4.join();
    System.out.println("Fast Abort="+ls4.getTime());
    System.out.println("Aborts="+ls4.getAborts()+" Commit="+ls4.getCommits());
    ls4=null;

    ls5.join();
    System.out.println("Attack Abort="+ls5.getTime());
    System.out.println("Aborts="+ls5.getAborts()+" Commit="+ls5.getCommits());
    ls5=null;
  }

  public static void p2(Executor e) throws Exception {
    FlexScheduler ls6=new FlexScheduler(e, FlexScheduler.SUICIDE, null);
    ls6.start();
    
    FlexScheduler ls7=new FlexScheduler(e, FlexScheduler.TIMESTAMP, null);
    ls7.start();

    FlexScheduler ls8=new FlexScheduler(e, FlexScheduler.RANDOM, null);
    ls8.start();


    ls6.join();
    System.out.println("Suicide Abort="+ls6.getTime());
    System.out.println("Aborts="+ls6.getAborts()+" Commit="+ls6.getCommits());
    ls6=null;

    ls7.join();
    System.out.println("Timestamp Abort="+ls7.getTime());
    System.out.println("Aborts="+ls7.getAborts()+" Commit="+ls7.getCommits());
    ls7=null;

    ls8.join();
    System.out.println("Random Abort="+ls8.getTime());
    System.out.println("Aborts="+ls8.getAborts()+" Commit="+ls8.getCommits());
    ls8=null;
  }

  public static void p3(Executor e) throws Exception {
    FlexScheduler ls9=new FlexScheduler(e, FlexScheduler.KARMA, null);
    ls9.start();

    FlexScheduler ls10=new FlexScheduler(e, FlexScheduler.POLITE, null);
    ls10.start();

    FlexScheduler ls11=new FlexScheduler(e, FlexScheduler.ERUPTION, null);
    ls11.start();



    ls9.join();
    System.out.println("Karma Abort="+ls9.getTime());
    System.out.println("Aborts="+ls9.getAborts()+" Commit="+ls9.getCommits());
    ls9=null;

    ls10.join();
    System.out.println("Polite Abort="+ls10.getTime());
    System.out.println("Aborts="+ls10.getAborts()+" Commit="+ls10.getCommits());
    ls10=null;

    ls11.join();
    System.out.println("Eruption Abort="+ls11.getTime());
    System.out.println("Aborts="+ls11.getAborts()+" Commit="+ls11.getCommits());
  }

  public static void p4(Executor e) throws Exception {
    FlexScheduler ls9=new FlexScheduler(e, FlexScheduler.THREAD, null);
    ls9.start();

    ls9.join();
    System.out.println("Karma Abort="+ls9.getTime());
    System.out.println("Aborts="+ls9.getAborts()+" Commit="+ls9.getCommits());
    ls9=null;
  }

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
    if (args.length==1||args[1].equals("1"))
      p1(e);
    if (args.length==1||args[1].equals("2"))
      p2(e);
    if (args.length==1||args[1].equals("3"))
      p3(e);
    if (args.length==1||args[1].equals("4"))
      p4(e);
  }
}