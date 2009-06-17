#ifndef ___PSEMAPHORE_H__
#define ___PSEMAPHORE_H__

#include <pthread.h>


typedef struct psemaphore_t {
  pthread_mutex_t lock;
  pthread_cond_t  cond;
  int             signaled;
} psemaphore;


int psem_init( psemaphore* sem );
int psem_take( psemaphore* sem );
int psem_give( psemaphore* sem );


#endif // ___PSEMAPHORE_H__
