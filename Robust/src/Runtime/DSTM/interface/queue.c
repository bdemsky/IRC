#include "queue.h"

volatile int headoffset, tailoffset;
char * memory;
pthread_mutex_t qlock;
pthread_mutexattr_t qlockattr;
pthread_cond_t qcond;

#define QSIZE 2048 //2 KB

void queueInit(void) {
  /* Intitialize primary queue */
  headoffset=0;
  tailoffset=0;
  memory=malloc(QSIZE+sizeof(int)); //leave space for -1
  pthread_mutexattr_init(&qlockattr);
  pthread_mutexattr_settype(&qlockattr, PTHREAD_MUTEX_RECURSIVE_NP);
  pthread_mutex_init(&qlock, &qlockattr);
  pthread_cond_init(&qcond, NULL);
}

void * getmemory(int size) {
  int tmpoffset=headoffset+size+sizeof(int);
  if (tmpoffset>QSIZE) {
    //Wait for tail to go past end
    tmpoffset=size+sizeof(int);
    if (headoffset<tailoffset) {
      pthread_cond_signal(&qcond); //wake the other thread up
      return NULL;
    }
    //Wait for tail to go past new start
    if (tailoffset<=tmpoffset) {
      pthread_cond_signal(&qcond); //wake the other thread up
      return NULL;
    }
    *((int *)(memory+headoffset))=-1; //safe because we left space
    *((int*)memory)=size+sizeof(int);
    return memory+sizeof(int);
  } else {
    if (headoffset<tailoffset&&tailoffset<=tmpoffset) {
      pthread_cond_signal(&qcond); //wake the other thread up
      return NULL;
    }
    *((int*)(memory+headoffset))=size+sizeof(int);
    return memory+headoffset+sizeof(int);
  }
}

void movehead(int size) {
  int tmpoffset=headoffset+size+sizeof(int);
  if (tmpoffset>QSIZE) {
    headoffset=size+sizeof(int);
  } else
    headoffset=tmpoffset;
  pthread_cond_signal(&qcond); //wake the other thread up
}

void * gettail() {
  while(tailoffset==headoffset) {
    //Sleep
    //    pthread_mutex_lock(&qlock);
    //    if (tailoffset==headoffset)
    //      pthread_cond_wait(&qcond, &qlock);
    //    pthread_mutex_unlock(&qlock);
  }
  if (*((int *)(memory+tailoffset))==-1) {
    tailoffset=0; //do loop
  }

  return memory+tailoffset+sizeof(int);
}

void inctail() {
  int tmpoffset=tailoffset+*((int *)(memory+tailoffset));
  if (tmpoffset>QSIZE)
    tailoffset=0;
  else
    tailoffset=tmpoffset;
}

void predealloc() {
  free(memory);
}

