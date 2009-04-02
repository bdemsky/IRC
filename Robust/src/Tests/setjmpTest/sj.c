#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <setjmp.h>

#include "Queue.h"

struct SESEstruct {
  int     id;
  jmp_buf buf;
  void*   parent;
  Q*      childQ;

  // "local" variables for use in macros
  void* child;
};
typedef struct SESEstruct SESE;
  

SESE* createSESE( SESE* parent, int id ) {
  SESE* sese   = (SESE*) malloc( sizeof( SESE ) );
  sese->id     = id;
  sese->parent = (void*) parent;
  sese->childQ = createQueue();
}

void freeSESE( SESE* sese ) {
  freeQueue( sese->childQ );
  free( sese );
  sese = NULL;
}

// macros set this return value for inspection
int mlpRet;

// currently running SESE
SESE* current;


int mlpTime = 0;
void mlpLog( char* point ) {
  printf( "  time = %d, id = %d, point = %s\n", mlpTime, current->id, point );
  mlpTime++;
}


#define mlpInit( id ) \
  current = createSESE( NULL, id );


void mlpBlock( int id ) {
}


#define mlpEnqueue( childid )					\
  current->child = (void*) createSESE( current, childid );	\
  addNewItem( current->childQ, current->child );		\
  if( setjmp( ((SESE*)current->child)->buf ) ) {		\
    mlpRet = 1;							\
  } else {							\
    mlpRet = 0;							\
  }						


#define mlpNotifyExit( id )					\
  while( !isEmpty( current->childQ ) ) {			\
    current->child = getItem( current->childQ );		\
    current = (SESE*) current->child;				\
    if( setjmp( ((SESE*)current->parent)->buf ) ) {		\
    } else {							\
      longjmp( current->buf, 1 );				\
    }								\
  }								\
  if( current->parent != NULL ) {				\
    current = (SESE*) current->parent;				\
    longjmp( current->buf, 1 );					\
  }


int main() {
  int i;
  char lname[10];

  printf( "Beginning setjump/longjump test.\n" );

  mlpInit( 0 );
  mlpLog( "a" );

  mlpEnqueue( 1 );
  if( mlpRet ) {

    mlpLog( "w" );

    for( i = 0; i < 2; ++i ) {

      mlpEnqueue( 10 + i );
      if( mlpRet ) {
	
	sprintf( lname, "Ls%d", 10 + i );
	mlpLog( lname );
	mlpNotifyExit( 10 + i );

      } else {
	mlpLog( "i" );
      }
    }

    mlpLog( "x" );
    mlpNotifyExit( 1 );

  } else {
    mlpLog( "W" );
    mlpNotifyExit( 0 );
  }

  printf( "End test.\n" );

  return 0;
}
