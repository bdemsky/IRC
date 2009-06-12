#include <stdlib.h>
#include <stdio.h>
#include <sys/time.h>

#include "workschedule.h"


static int workLoad = 0;


typedef struct wUnit_ {
  int     n;
  double* answer;
} wUnit;


double findSqrt( int x ) {
  double a, b, p;
  double e = 0.00001;
  double k = (double) x;
  int i;

  // just do it a bunch of times
  // to generate more work
  for( i = 0; i < 100000; ++i ) {

    a = k;
    p = a*a;
    while( p - k >= e ) {
      b = (a + (k/a)) / 2.0;
      a = b;
      p = a*a;
    }
  }

  return a;
}


void processWorkUnit( void* workUnit ) {
  
  wUnit* w = (wUnit*) workUnit;
  wUnit* x = NULL;

  // submit more work
  if( w->n > 0 ) {
    x         = malloc( sizeof( wUnit ) );
    x->n      = w->n      - 1;
    x->answer = w->answer - 1;
    workScheduleSubmit( (void*)x );
  }

  // then start computing 
  // and store answer
  *(w->answer) = findSqrt( w->n );

  // workunit no longer needed
  free( w );
}



void usage() {
  printf( "usage:\na.out <int workers> <int workLoad> <str singlethread/workschedule>\n" );
}


int main( int argc, char** argv ) {

  struct timeval tstart;
  struct timeval tfinish;
  double         timediff;
  double*        answers;
  double         answerSummary;
  int            i;

  if( argc != 4 ) {
    usage();
    exit( 0 );
  }

  // initialize solution array outside of timing
  workLoad = atoi( argv[2] );
  answers = malloc( sizeof( double ) * workLoad );
  for( i = 0; i < workLoad; ++i ) {
    answers[i] = 0.0;
  }


  gettimeofday( &tstart, NULL );


  if( strcmp( argv[3], "singlethread" ) == 0 ) {

    for( i = 0; i < workLoad; ++i ) {
      answers[i] = findSqrt( i );
    }

  } else if( strcmp( argv[3], "workschedule" ) == 0 ) {
    // initialize the system
    workScheduleInit( atoi( argv[1] ),   // num processors
		      processWorkUnit ); // func for processing

    // add a preliminary work unit
    wUnit* w  = malloc( sizeof( wUnit ) );
    w->n      = workLoad-1;
    w->answer = &(answers[workLoad-1]);
    workScheduleSubmit( (void*)w );

    // start work schedule, some work will
    // generate more work, when its all done
    // the system will return to this point
    workScheduleBegin();

  } else {
    usage();
    exit( 0 );
  }


  gettimeofday( &tfinish, NULL );


  // summarize answers outside of timing
  answerSummary = 0.0;
  for( i = 0; i < workLoad; ++i ) {
    answerSummary += answers[i];
  }


  timediff = (double)(((tfinish.tv_sec  - tstart.tv_sec )*1000000)+
		      ((tfinish.tv_usec - tstart.tv_usec)*1      ))
    / 1000000.0;   

  printf( "\n\nComputed summary %f in %f seconds.\n",
	  answerSummary,
	  timediff );

  return 0;
}
