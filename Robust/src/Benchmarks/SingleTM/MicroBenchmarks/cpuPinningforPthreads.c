/*******************************************************************
 * This program has the sole purpose of showing some kernel API 
 * for CPU affinity used in pthreads. Consider this merely a demo...

 * Uses pthread_setaffinity_np
 * adash@uci.edu
 ********************************************************************/

#include <stdlib.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/sysinfo.h>
#include <sys/mman.h>
#include <unistd.h>
#define  __USE_GNU
#include <sched.h>
#include <ctype.h>
#include <string.h>
#include <pthread.h>

/* Create us some pretty boolean types and definitions */
typedef int bool;   
#define TRUE  1
#define FALSE 0 
#define ITERATIONS 1000000
pthread_barrier_t barr;


/* Method Declarations */
void usage();                              /* Simple generic usage function */
bool do_cpu_stress(int numthreads);    /* Entry point into CPU thrash */
static __inline__ unsigned long long rdtsc(void);
void *thd(void *threadid);


int main(
    int argc, 
    char **argv ) 
{
    int return_code = FALSE;

  /* Determine the actual number of processors */
  int NUM_PROCS = sysconf(_SC_NPROCESSORS_CONF);
  printf("System has %i processor(s).\n", NUM_PROCS);

  /* These need sane defaults, because the values will be used unless overriden */
  int num_cpus_to_spin = NUM_PROCS; 

  // Barrier initialization
  if(pthread_barrier_init(&barr, NULL, NUM_PROCS))
  {
    printf("Could not create a barrier\n");
    return -1;
  }

  /* Check for user specified parameters */
  int option = 0; 
  while ((option = getopt(argc, argv, "m:c:l?ahd")) != -1)
  {
    switch (option)
    {
      case 'c': /* SPECIFY NUM CPUS TO MAKE BUSY */
        num_cpus_to_spin = atoi(optarg);
        if (num_cpus_to_spin < 1)
        {
          printf("WARNING: Must utilize at least 1 cpu. Spinning "
              " all %i cpu(s) instead...\n", NUM_PROCS);
          num_cpus_to_spin = 1;
        }
        else if (num_cpus_to_spin > NUM_PROCS)
        {
          printf("WARNING: %i cpu(s), are not "
              "available on this system, spinning all %i cpu(s) "
              "instead...\n", num_cpus_to_spin, NUM_PROCS);
          num_cpus_to_spin = NUM_PROCS;
        }
        else
        { 
          printf("Maxing computation on %i cpu(s)...\n",
              num_cpus_to_spin);
        }
        break;


      case '?':
        if (isprint (optopt))
        {
          fprintf (stderr, 
              "Unknown option `-%c'.\n", optopt);
        }
        else
        {
          fprintf (stderr,
              "Unknown option character `\\x%x'.\n",
              optopt);
        }
        break;

      default:
        usage(argv[0]);
        exit(0);
    }
  }

  /* Kick off the actual work of spawning threads and computing */
  do_cpu_stress(num_cpus_to_spin); 
  return return_code;
}

/* This method simply prints the usage information for this program */
void usage()
{
  printf("[-c  NUM_CPUS_TO_STRESS]\n");
  printf("If no parameters are specified all cpu's will be made busy.\n");
  return;
}

/* This method creates the threads and sets the affinity. */
bool do_cpu_stress(int numthreads)
{
  int ret = TRUE;
  int created_thread = 0;

  pthread_t threads[numthreads];
  int rc;
  unsigned long t;
  for(t=1; t<numthreads+1; t++){
    rc = pthread_create(&threads[t-1], NULL, thd, (void *)t);
    if (rc){
      printf("ERROR; return code from pthread_create() is %d\n", rc);
      exit(-1);
    }
  }

  for(t=1; t<numthreads+1; t++){
    pthread_join(threads[t-1], NULL);
  }

  return ret;
}

void *thd(void *threadid)
{
  unsigned long mask;
  mask = (unsigned long)threadid;
  printf("Hello World! It's me, thread #%ld!\n", mask);

  /* bind process to processor threadid */
  if (pthread_setaffinity_np(pthread_self(), sizeof(mask), &mask) <0) {
    perror("pthread_setaffinity_np");
  }

  /* calculate the clock skew for each core */
  int count = 0;
  unsigned long long tottime, time;
  while(count<ITERATIONS) {
    // Wait till we may fire away
    int rc=pthread_barrier_wait(&barr);
    if(rc != 0 && rc != PTHREAD_BARRIER_SERIAL_THREAD)
    {
      printf("Could not wait on barrier\n");
      exit(-1);
    }
    time = rdtsc();
    tottime += time;
    count++;
  }
  printf("time= %lld clock ticks, cpu id= %ld\n", tottime/ITERATIONS, mask);
}

static __inline__ unsigned long long rdtsc(void)
{
  unsigned hi, lo;
  __asm__ __volatile__ ("rdtsc" : "=a"(lo), "=d"(hi));
  return ( (unsigned long long)lo)|( ((unsigned long long)hi)<<32 );
}
