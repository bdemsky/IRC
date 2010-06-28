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
    System.out.println("Stalltime="+ls.getStallTime()+" Backofftime="+ls.getBackoffTime());
    System.out.println("Abortedtime="+ls.getAbortedTime());
    ls=null;

    ls4.join();
    System.out.println("Fast Abort="+ls4.getTime());
    System.out.println("Aborts="+ls4.getAborts()+" Commit="+ls4.getCommits());
    System.out.println("Stalltime="+ls4.getStallTime()+" Backofftime="+ls4.getBackoffTime());
    System.out.println("Abortedtime="+ls4.getAbortedTime());
    ls4=null;

    ls5.join();
    System.out.println("Attack Abort="+ls5.getTime());
    System.out.println("Aborts="+ls5.getAborts()+" Commit="+ls5.getCommits());
    System.out.println("Stalltime="+ls5.getStallTime()+" Backofftime="+ls5.getBackoffTime());
    System.out.println("Abortedtime="+ls5.getAbortedTime());
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
    System.out.println("Stalltime="+ls6.getStallTime()+" Backofftime="+ls6.getBackoffTime());
    System.out.println("Abortedtime="+ls6.getAbortedTime());
    ls6=null;

    ls7.join();
    System.out.println("Timestamp Abort="+ls7.getTime());
    System.out.println("Aborts="+ls7.getAborts()+" Commit="+ls7.getCommits());
    System.out.println("Stalltime="+ls7.getStallTime()+" Backofftime="+ls7.getBackoffTime());
    System.out.println("Abortedtime="+ls7.getAbortedTime());
    ls7=null;

    ls8.join();
    System.out.println("Random Abort="+ls8.getTime());
    System.out.println("Aborts="+ls8.getAborts()+" Commit="+ls8.getCommits());
    System.out.println("Stalltime="+ls8.getStallTime()+" Backofftime="+ls8.getBackoffTime());
    System.out.println("Abortedtime="+ls8.getAbortedTime());
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
    System.out.println("Stalltime="+ls9.getStallTime()+" Backofftime="+ls9.getBackoffTime());
    System.out.println("Abortedtime="+ls9.getAbortedTime());
    ls9=null;

    ls10.join();
    System.out.println("Polite Abort="+ls10.getTime());
    System.out.println("Aborts="+ls10.getAborts()+" Commit="+ls10.getCommits());
    System.out.println("Stalltime="+ls10.getStallTime()+" Backofftime="+ls10.getBackoffTime());
    System.out.println("Abortedtime="+ls10.getAbortedTime());
    ls10=null;

    ls11.join();
    System.out.println("Eruption Abort="+ls11.getTime());
    System.out.println("Aborts="+ls11.getAborts()+" Commit="+ls11.getCommits());
    System.out.println("Stalltime="+ls11.getStallTime()+" Backofftime="+ls11.getBackoffTime());
    System.out.println("Abortedtime="+ls11.getAbortedTime());
  }

  public static void p4(Executor e) throws Exception {
    FlexScheduler ls12=new FlexScheduler(e, FlexScheduler.THREAD, null);
    ls12.start();
    FlexScheduler ls13=new FlexScheduler(e, FlexScheduler.ATTACKTIME, null);
    ls13.start();
    FlexScheduler ls14=new FlexScheduler(e, FlexScheduler.ATTACKTHREAD, null);
    ls14.start();

    ls12.join();
    System.out.println("ThreadPriority Abort="+ls12.getTime());
    System.out.println("Aborts="+ls12.getAborts()+" Commit="+ls12.getCommits());
    System.out.println("Stalltime="+ls12.getStallTime()+" Backofftime="+ls12.getBackoffTime());
    System.out.println("Abortedtime="+ls12.getAbortedTime());
    ls12=null;

    ls13.join();
    System.out.println("AttackTime Abort="+ls13.getTime());
    System.out.println("Aborts="+ls13.getAborts()+" Commit="+ls13.getCommits());
    System.out.println("Stalltime="+ls13.getStallTime()+" Backofftime="+ls13.getBackoffTime());
    System.out.println("Abortedtime="+ls13.getAbortedTime());
    ls13=null;

    ls14.join();
    System.out.println("AttackThread Abort="+ls14.getTime());
    System.out.println("Aborts="+ls14.getAborts()+" Commit="+ls14.getCommits());
    System.out.println("Stalltime="+ls14.getStallTime()+" Backofftime="+ls14.getBackoffTime());
    System.out.println("Abortedtime="+ls14.getAbortedTime());
    ls14=null;
  }

  int[] policies=new int[]{FlexScheduler.LAZY, FlexScheduler.COMMIT, FlexScheduler.ATTACK, FlexScheduler.SUICIDE, FlexScheduler.TIMESTAMP, FlexScheduler.LOCK, FlexScheduler.LOCKCOMMIT, FlexScheduler.RANDOM, FlexScheduler.KARMA, FlexScheduler.POLITE, FlexScheduler.ERUPTION, FlexScheduler.THREAD, FlexScheduler.ATTACKTIME, FlexScheduler.ATTACKTHREAD};

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

    FlexScheduler fsarray[]=new FlexScheduler[args.length-1];

    for(int i=1;i<args.length;i++) {
      fsarray[i-1]=new FlexScheduler(e, Integer.parseInt(args[i]), null);
      fsarray[i-1].start();
    }

    for(int i=0;i<fsarray.length;i++) {
      fsarray[i].join();
      FlexScheduler ls=fsarray[i];
      System.out.println(FlexScheduler.getName(ls.policy)+" Abort="+ls.getTime());
      System.out.println("Aborts="+ls.getAborts()+" Commit="+ls.getCommits());
      System.out.println("Stalltime="+ls.getStallTime()+" Backofftime="+ls.getBackoffTime());
      System.out.println("Abortedtime="+ls.getAbortedTime());      
      //free the memory
      fsarray[i]=null;
    }
  }
}