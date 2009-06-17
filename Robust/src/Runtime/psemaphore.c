#include "psemaphore.h"


int psem_init( psemaphore* sem ) {
  if( pthread_mutex_init( &(sem->lock), NULL ) == -1 ) { return -1; }
  if( pthread_cond_init ( &(sem->cond), NULL ) == -1 ) { return -1; }
  sem->signaled = 0;
  return 0;
}


int psem_take( psemaphore* sem ) {
  if( pthread_mutex_lock  ( &(sem->lock) ) == -1 ) { return -1; }
  while( !sem->signaled ) {
    if( pthread_cond_wait ( &(sem->cond), &(sem->lock) ) == -1 ) { return -1; }
  }
  if( pthread_mutex_unlock( &(sem->lock) ) == -1 ) { return -1; }
  return 0;
}


int psem_give( psemaphore* sem ) {
  if( pthread_mutex_lock  ( &(sem->lock) ) == -1 ) { return -1; }
  sem->signaled = 1;
  if( pthread_cond_signal ( &(sem->cond) ) == -1 ) { return -1; }
  if( pthread_mutex_unlock( &(sem->lock) ) == -1 ) { return -1; }

}


