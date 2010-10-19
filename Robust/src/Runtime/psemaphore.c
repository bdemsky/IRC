#include <stdlib.h>
#include <errno.h>
#include "psemaphore.h"


void psem_init( psemaphore* sem ) {
  pthread_mutex_init( &(sem->lock), NULL );
  pthread_cond_init ( &(sem->cond), NULL );
  sem->signaled = 0;
}


void psem_take( psemaphore* sem, struct garbagelist* gl ) {
  pthread_mutex_lock( &(sem->lock) );
  if( !sem->signaled ) {
    stopforgc( gl );
    do {
      pthread_cond_wait( &(sem->cond), &(sem->lock) );
    } while( !sem->signaled );
    restartaftergc();
  }
  pthread_mutex_unlock( &(sem->lock) );
}


void psem_give( psemaphore* sem ) {
  pthread_mutex_lock  ( &(sem->lock) );
  sem->signaled = 1;
  pthread_cond_signal ( &(sem->cond) );
  pthread_mutex_unlock( &(sem->lock) );
}


void psem_reset( psemaphore* sem ) {
  // this should NEVER BE CALLED if it is possible
  // the semaphore is still in use, NEVER
  if( pthread_mutex_trylock( &(sem->lock) ) == EBUSY ) {
    exit( -1 );
  }
  pthread_mutex_unlock( &(sem->lock) );
  sem->signaled = 0;
}
