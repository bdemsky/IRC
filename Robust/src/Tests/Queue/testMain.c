#include <stdlib.h>
#include <stdio.h>

#include "Queue.h"


void check( struct Queue* q ) {
  if( assertQueue( q ) ) {
    printf( "Queue valid\n" );
  } else { 
    printf( "QUEUE INVALID\n" ); 
  }
}


int main() {

  struct Queue* q = createQueue();

  struct QueueItem* i1;
  struct QueueItem* i2;
  struct QueueItem* i3;
  
  char m1[] = "1";
  char m2[] = "2";
  char m3[] = "3";
  char m4[] = "4";
  char* mo;

  addNewItem( q, (void*)m1 );
  check( q );

  addNewItem( q, (void*)m2 );
  check( q );

  addNewItemBack( q, (void*)m3 );
  check( q );

  mo = (char*) getItem( q );
  check( q );

  addNewItemBack( q, (void*)m4 );
  check( q );

  mo = (char*) getItemBack( q );
  check( q );

  mo = (char*) getItem( q );
  check( q );

  mo = (char*) getItemBack( q );
  check( q );

  i1 = addNewItemBack( q, (void*)m3 );
  check( q );

  i2 = addNewItem( q, (void*)m1 );
  check( q );

  i3 = addNewItem( q, (void*)m2 );
  check( q );

  removeItem( q, i2 );
  check( q );

  removeItem( q, i3 );
  check( q );

  removeItem( q, i1 );
  check( q );

  return 0;
}
