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

public class Genome {
  long geneLength;
  long segmentLength;
  long minNumSegment;
  long numThread;
  
  Genome(String x[]) {
    parseCmdLine(x);
  }
  
  public static void main(String x[]){
    
/*    TIMER_T start; */
/*    TIMER_T stop; */

/*    GOTO_REAL(); */

    /* Initialization */
/*    parseArgs(argc, (char** const)argv); */
/*    SIM_GET_NUM_CPU(global_params[PARAM_THREAD]); */

    System.out.print("Creating gene and segments... ");
    Genome g = new Genome(x);


/*    TM_STARTUP(numThread); */
/*    P_MEMORY_STARTUP(numThread); */
/*    thread_startup(numThread); */

    Random randomPtr = new Random();
    random_alloc(randomPtr);
    random_seed(randomPtr, 0);

    Gene genePtr = new Gene(geneLength);
    genePtr.create(randomPtr);
    String gene = genePtr.contents;

    Segments segmentsPtr = new Segments(segmentLength, minNumSegment);
    segmentsPtr.create(genePtr, randomPtr);
    sequencer_t* sequencerPtr = sequencer_alloc(geneLength, segmentLength, segmentsPtr);
    assert(sequencerPtr != NULL);

    puts("done.");
    printf("Gene length     = %li\n", genePtr->length);
    printf("Segment length  = %li\n", segmentsPtr->length);
    printf("Number segments = %li\n", vector_getSize(segmentsPtr->contentsPtr));
    fflush(stdout);

    /* Benchmark */
    printf("Sequencing gene... ");
    fflush(stdout);
    TIMER_READ(start);
    GOTO_SIM();
#ifdef OTM
#pragma omp parallel
    {
        sequencer_run(sequencerPtr);
    }
#else
    thread_start(sequencer_run, (void*)sequencerPtr);
#endif
    GOTO_REAL();
    TIMER_READ(stop);
    puts("done.");
    printf("Time = %lf\n", TIMER_DIFF_SECONDS(start, stop));
    fflush(stdout);

    /* Check result */
    {
        char* sequence = sequencerPtr->sequence;
        int result = strcmp(gene, sequence);
        printf("Sequence matches gene: %s\n", (result ? "no" : "yes"));
        if (result) {
            printf("gene     = %s\n", gene);
            printf("sequence = %s\n", sequence);
        }
        fflush(stdout);
        assert(strlen(sequence) >= strlen(gene));
    }

    /* Clean up */
    printf("Deallocating memory... ");
    fflush(stdout);
    sequencer_free(sequencerPtr);
    segments_free(segmentsPtr);
    gene_free(genePtr);
    random_free(randomPtr);
    puts("done.");
    fflush(stdout);

    TM_SHUTDOWN();
    P_MEMORY_SHUTDOWN();

    GOTO_SIM();

    thread_shutdown();

    MAIN_RETURN(0);
  }
  
  public static void parseCmdLine(String args[]) {

    int i = 0;
    String arg;
    while (i < args.length && args[i].startsWith("-")) {
      arg = args[i++];
      //check options
      if(arg.equals("-g")) {
        if(i < args.length) {
          geneLength = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-s")) {
        if(i < args.length) {
          segmentLength = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-n")) {
        if(i < args.length) {
          minNumSegment = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-t")) {
        if(i < args.length) {
          numThread = new Integer(args[i++]).intValue();
        }
      } 
    }
  }
}

public enum param_types {
    PARAM_GENE    /*= (unsigned char)'g'*/,
    PARAM_NUMBER  /*= (unsigned char)'n'*/,
    PARAM_SEGMENT /*= (unsigned char)'s'*/,
    PARAM_THREAD  /*= (unsigned char)'t',*/
}
