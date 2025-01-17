#include <stdlib.h>
#include <stdio.h>

#include <pthread.h>

#include "memPool.h"


static const int POOLSIZE = 100;

static MemPool* barPool;
static MemPool* bazPool;
static MemPool* fooPool;


struct bar {
  int x;
  char takeSpace[1000];
};

struct baz {
  int y;
  char takeSpace[1000];
};

struct foo {
  struct bar* br;
  struct baz* bz;
  int z;
  char takeSpace[1000];
};


void* workerMain( void* arg ) {

  struct foo* f = (struct foo*)arg;

  f->z = f->br->x + f->bz->y;

  poolfree( barPool, f->br );
  poolfree( bazPool, f->bz );
  
  pthread_exit( arg );
}


int main() {

  int i;

  struct bar* br;
  struct baz* bz;
  struct foo* f;

  int            numThreads = 10000;
  pthread_t      threads[numThreads];
  pthread_attr_t attr;

  int total = 0;

  barPool = poolcreate( sizeof( struct bar ), POOLSIZE );
  bazPool = poolcreate( sizeof( struct baz ), POOLSIZE );
  fooPool = poolcreate( sizeof( struct foo ), POOLSIZE );

  pthread_attr_init( &attr );
  pthread_attr_setdetachstate( &attr, 
                               PTHREAD_CREATE_JOINABLE );


  for( i = 0; i < numThreads; ++i ) {

    br = poolalloc( barPool );
    bz = poolalloc( bazPool );
    f  = poolalloc( fooPool );

    br->x = i;
    bz->y = -4321;
    
    f->br = br;
    f->bz = bz;

    pthread_create( &(threads[i]),
                    &attr,
                    workerMain,
                    (void*)f );

    if( i % 1000 == 0 ) {
      printf( "." );
    }

    if( i % 100 == 0 ) {
      sched_yield();
    }
  }
  
  printf( "\n" );

  for( i = 0; i < numThreads; ++i ) {

    f = NULL;
    pthread_join( threads[i],
                  (void**)&f );

    total += f->z;
    poolfree( fooPool, f );

    if( i % 1000 == 0 ) {
      printf( "+" );
    }
  }
  
  printf( "\nTotal=%d\n", total );

  return 0;
}
