/*
"gene.h"
"random.h"
"segments.h"
"sequencer.h"
"thread.h"
"timer.h"
"tm.h"
"vector.h"
"bitmap.h"

*/

public class Genome extends Thread {
  long geneLength;
  long segmentLength;
  long minNumSegment;
  long numThread;
  
  int threadid;
  
  // add segments, random, etc to member variables
  // include in constructor
  // allows for passing in thread run function
  Random randomPtr;
  Gene genePtr;
  Segments segmentsPtr;
  Sequencer sequencerPtr;
  
  Genome(String x[]) {
    parseCmdLine(x);
    if(numThread == 0) {
      numThread = 1;
    }
    
    randomPtr = new Random();
    randomPtr.random_alloc(randomPtr);
    randomPtr.random_seed(randomPtr, 0);
        
    genePtr = new Gene(geneLength);
    genePtr.create(randomPtr);

    segmentsPtr = new Segments(segmentLength, minNumSegment);
    segmentsPtr.create(genePtr, randomPtr);

    sequencerPtr = new Sequencer(geneLength, segmentLength, segmentsPtr);

  }
  
  Genome(int myThreadid, long myGeneLength, long mySegLength, long myMinNumSegs, long myNumThread, Random myRandomPtr, Gene myGenePtr, Segments mySegmentsPtr, Sequencer mySequencerPtr) {
//    System.out.println("New thread! My id is " + myThreadid + "!");
    threadid = myThreadid;
    geneLength = myGeneLength;
    segmentLength = mySegLength;
    minNumSegment = myMinNumSegs;
    numThread = myNumThread;
    
    randomPtr = myRandomPtr;
    genePtr = myGenePtr;
    segmentsPtr = mySegmentsPtr;
    sequencerPtr = mySequencerPtr;
  }
  
    public void parseCmdLine(String args[]) {
    int i = 0;
    String arg;
    while (i < args.length && args[i].startsWith("-")) {
      arg = args[i++];
      //check options
      if(arg.equals("-g")) {
        if(i < args.length) {
          this.geneLength = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-s")) {
        if(i < args.length) {
          this.segmentLength = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-n")) {
        if(i < args.length) {
          this.minNumSegment = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-t")) {
        if(i < args.length) {
          this.numThread = new Integer(args[i++]).intValue();
        }
      } 
    }

  }

  public void run() {
      Barrier.enterBarrier();
//      System.out.println("*** Thread " + threadid + " about to run ***");
      Sequencer.run(threadid, numThread, randomPtr, sequencerPtr); 
      Barrier.enterBarrier();
  }
  
  public static void main(String x[]){
    
/*    TIMER_T start; */
/*    TIMER_T stop; */

/*    GOTO_REAL(); */

    /* Initialization */
//    parseArgs(); 
/*    SIM_GET_NUM_CPU(global_params[PARAM_THREAD]); */

    System.out.print("Creating gene and segments... ");
    Genome g = new Genome(x);

    System.out.println("done.");
    System.out.println("Gene length     = " + g.genePtr.length);
    System.out.println("Segment length  = " + g.segmentsPtr.length);
    System.out.println("Number segments = " + g.segmentsPtr.contentsPtr.size());
    System.out.println("Number threads  = " + g.numThread);


    Barrier.setBarrier((int)g.numThread);

/*    TM_STARTUP(numThread); */
/*    P_MEMORY_STARTUP(numThread); */
/*    thread_startup(numThread); */

/* Create and Start Threads */

    ByteArray gene = g.genePtr.contents;

    Genome[] gn = new Genome[g.numThread];

    
    for(int i = 1; i<g.numThread; i++) {
      gn[i] = new Genome(i, g.geneLength, g.segmentLength, g.minNumSegment, g.numThread, g.randomPtr, g.genePtr, g.segmentsPtr, g.sequencerPtr);
    }
    
    System.out.print("Sequencing gene... ");    
    
//    g.start();

    for(int i = 1; i<g.numThread; i++) {
      gn[i].start();
    }
    
    Barrier.enterBarrier();
//    System.out.println("*** Thread " + g.threadid + " about to run ***");
    Sequencer.run(0, g.numThread, g.randomPtr, g.sequencerPtr); 
    Barrier.enterBarrier();

    

//    fflush(stdout);

    /* Benchmark */

//    fflush(stdout);
//    TIMER_READ(start);
//    GOTO_SIM();

/*
#ifdef OTM
#pragma omp parallel
    {
        sequencer_run(sequencerPtr);
    }
#else
    thread_start(sequencer_run, (void*)sequencerPtr);
#endif
//    GOTO_REAL();
//    TIMER_READ(stop);
*/
    
    //sequencer_run(threadId);
    
    System.out.println("done.");

    /* Check result */
    {
        ByteArray sequence = g.sequencerPtr.sequence;
//        System.out.print("sequence: ");sequence.print();
        boolean result = (gene.compareTo(sequence) == 0) ? true:false;
        System.out.println("Sequence matches gene: " + (result ? "yes" : "no"));
        if (result) {
//            System.out.println("gene     = " + gene);
//            System.out.println("sequence = " + sequence);
        }
//        fflush(stdout);
//        assert(strlen(sequence) >= strlen(gene));
    }

    /* Clean up */

//    TM_SHUTDOWN();
//    P_MEMORY_SHUTDOWN();

//    GOTO_SIM();
//    thread_shutdown();

//    MAIN_RETURN(0);
  }
  
/*  static int byteCompareTo(byte a[], byte b[]) {
    int i = 0;
    while(a[i] != null) {
      if(b[i] == null) {
        return 1;
      } else if(a[i] < b[i]) {
        return -1;
      } else {
        return 1;
      }
    }
      
    return 0;
  }
*/
}
