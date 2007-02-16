#ifndef THREAD_H
#define THREAD_H
#include "methodheaders.h"
#include "pthread.h"

extern pthread_mutex_t threadtable;
extern int threadcount;
extern pthread_mutex_t gclock;
extern pthread_mutex_t gclistlock;
extern pthread_cond_t gccond;
void initthread(struct ___Thread___ * ___this___);
#endif
