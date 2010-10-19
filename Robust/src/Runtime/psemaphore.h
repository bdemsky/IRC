#ifndef ___PSEMAPHORE_H__
#define ___PSEMAPHORE_H__

#include <pthread.h>
#include "garbage.h"


typedef struct psemaphore_t {
  pthread_mutex_t lock;
  pthread_cond_t  cond;
  int             signaled;
} psemaphore;


void psem_init ( psemaphore* sem );
void psem_take ( psemaphore* sem, struct garbagelist* gl );
void psem_give ( psemaphore* sem );
void psem_reset( psemaphore* sem );


#endif // ___PSEMAPHORE_H__
