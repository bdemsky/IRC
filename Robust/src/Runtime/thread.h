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
extern pthread_mutex_t atomiclock;
#ifdef MAC
extern pthread_key_t litemkey;
extern pthread_key_t macthreadid;
extern pthread_key_t memorybasekey;
extern pthread_key_t memorytopkey;
#endif

#ifdef PRECISE_GC
#define ATOMICLOCK if (pthread_mutex_trylock(&atomiclock)!=0) { \
    stopforgc((struct garbagelist *) &___locals___);            \
    pthread_mutex_lock(&atomiclock);                            \
    restartaftergc();                                           \
}

#define ATOMICUNLOCK pthread_mutex_unlock(&atomiclock)
#else
#define ATOMICLOCK pthread_mutex_lock(&atomiclock)
#define ATOMICUNLOCK pthread_mutex_unlock(&atomiclock)
#endif

#if defined(THREADS)||defined(STM)
void initthread(struct ___Thread___ * ___this___);
#endif
#ifdef DSTM
void initDSMthread(int *ptr);
void startDSMthread(int oid, int objType);
extern void * virtualtable[];
#endif
#endif
