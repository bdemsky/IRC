#include <stdlib.h>
#include <stdio.h>

#include <pthread.h>
#include "memPool.h"

#define numThreads          24
#define numCyclesPerThread  500000
#define extraBytesInRecords 1000


struct bar {
  int x;
  char takeSpace[extraBytesInRecords];
};

struct baz {
  int y;
  char takeSpace[extraBytesInRecords];
};

struct foo {
  struct bar* br;
  struct baz* bz;
  int z;
  char takeSpace[extraBytesInRecords];
};


void* workerMain( void* arg ) {

  INTPTR i = (INTPTR)arg;
  int j;
  struct bar* br;
  struct baz* bz;
  struct foo* foos = malloc( numCyclesPerThread*sizeof( struct foo ) );

  for( j = 0; j < numCyclesPerThread; ++j ) {
    br = malloc( sizeof( struct bar ) );
    bz = malloc( sizeof( struct baz ) );

    br->x = i + j;
    bz->y = -4321;
    
    foos[j].br = br;
    foos[j].bz = bz;
    foos[j].z = foos[j].br->x + foos[j].bz->y;

    free( foos[j].br );
    free( foos[j].bz );
  }
  
  pthread_exit( foos );
}


int main() {

  INTPTR i;
  int j;

  struct foo* foos;

  pthread_t      threads[numThreads];
  pthread_attr_t attr;
  
  int total = 0;

  pthread_attr_init( &attr );
  pthread_attr_setdetachstate( &attr, 
                               PTHREAD_CREATE_JOINABLE );

  for( i = 0; i < numThreads; ++i ) {

    pthread_create( &(threads[i]),
                    &attr,
                    workerMain,
                    (void*)i );
    printf( "." );
  }
  
  printf( "\n" );

  for( i = 0; i < numThreads; ++i ) {

    foos = NULL;
    pthread_join( threads[i],
                  (void**)&foos );

    for( j = 0; j < numCyclesPerThread; ++j ) {
      total += foos[j].z;
    }
    free( foos );
    
    printf( "+" );
  }
  
  printf( "\nTotal=%d\n", total );

  return 0;
}
