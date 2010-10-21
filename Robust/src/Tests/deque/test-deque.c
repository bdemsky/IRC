#include <stdlib.h>
#include <stdio.h>

#include <pthread.h>
#include "deque.h"


#define numThreads 24
#define numCycles  1000
#define retries    100000


// global array of work-stealing deques
// each thread knows its own index
deque* deques;



// for artificially keeping threads busy
int spin( int n ) {
  long x = 0;
  int i, j;
  //for( i = 0; i < n; ++i ) {
  //  for( j = 0; j < numCycles; ++j ) {
  //    x = (x * x) + (x + x) - (x * 1 * x * 2) + i;
  //  }
  // }
  return x;
}



void* workerMain( void* arg ) {
  INTPTR i = (INTPTR)arg;
  
  INTPTR w = 0;
  int r = retries;

  deque* dq = &(deques[i]);
  
  srand( i * 1777 );

  int j;
  for( j = 0; j < i; ++j ) {
    int* one = malloc( sizeof( int ) );
    *one = 1;
    dqPushBottom( dq, one );
    spin( i );
  }

  while( r > 0 ) {
    void* num = dqPopBottom( dq );

    if( num == DQ_POP_ABORT ) {
      // another op is in progress, try again
      continue;

    } else if( num == DQ_POP_EMPTY ) {
      
      // IF YOU INSERT THIS (NEVER STEAL) THE AMOUNT
      // OF WORK COMES OUT RIGHT?!?!?!
      //pthread_exit( (void*)w );

      // no work here, steal!
      int v = rand() % numThreads;
      num = dqPopTop( &(deques[v]) );
      
      if( num == DQ_POP_ABORT ) {
        // another op in progress, try again later
        continue;

      } else if( num == DQ_POP_EMPTY ) {
        // lose a retry
        r--;
        continue;

      } else {
        // STOLE WORK!
        w += *((int*)num);
        spin( w / (i+1) );
        continue;
      }

    } else {
      // grabbed work
      w += *((int*)num);
      spin( w / (i+1) );
      continue;
    }
  }
  printf( "I'm %d and I did %d many.\n", i, w );
  pthread_exit( (void*)w );
}


int main() {
  INTPTR i;
  int    j;

  pthread_t      threads[numThreads];
  pthread_attr_t attr;

  long total = 0;


  pthread_attr_init( &attr );
  pthread_attr_setdetachstate( &attr, 
                               PTHREAD_CREATE_JOINABLE );

  deques = malloc( sizeof( deque )*numThreads );

  for( i = 0; i < numThreads; ++i ) {
    dqInit( &(deques[i]) );
  }

  for( i = 0; i < numThreads; ++i ) {
    pthread_create( &(threads[i]),
                    &attr,
                    workerMain,
                    (void*)i );
    printf( "." );
  }
  
  printf( "\n" );

  for( i = 0; i < numThreads; ++i ) {
    long x;
    pthread_join( threads[i],
                  (void*)&x );
    total += x;
    printf( "+" );
  }

  printf( "\nTotal (expect 300)=%d\n", total+24 );
  return 0;
}
