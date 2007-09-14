#ifndef THREAD_H
#define THREAD_H
#include "methodheaders.h"
#include <pthread.h>

extern int threadcount;
extern pthread_mutex_t gclock;
extern pthread_mutex_t gclistlock;
extern pthread_cond_t gccond;
extern pthread_mutex_t objlock;
extern pthread_cond_t objcond;
extern pthread_key_t threadlocks;
#ifdef THREADS
void initthread(struct ___Thread___ * ___this___);
#endif
#ifdef DSTM
void initDSMthread(int *ptr);
void startDSMthread(int oid, int objType);
extern void * virtualtable[];
#endif

struct locklist {
  struct locklist * next;
  struct locklist * prev;
  struct ___Object___ * object;
};
#endif
