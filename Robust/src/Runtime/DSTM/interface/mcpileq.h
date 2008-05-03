#ifndef _MCPILEQ_H_
#define _MCPILEQ_H_

#include<pthread.h>
#include<stdio.h>
#include<stdlib.h>
#include<string.h>

//Structure to make machine groups when prefetching
typedef struct objpile { 
  unsigned int oid;
  short numoffset;
  short *offset;
  struct objpile *next;
} objpile_t;

//Structure for prefetching tuples generated by the compiler
typedef struct prefetchpile {
  unsigned int mid;
  objpile_t *objpiles;
  struct prefetchpile *next;
} prefetchpile_t;

typedef struct mcpileq {
  prefetchpile_t *front, *rear;
  pthread_mutex_t qlock;
  pthread_mutexattr_t qlockattr;
  pthread_cond_t qcond;
} mcpileq_t;

void mcpileqInit(void);
void mcpileenqueue(prefetchpile_t *, prefetchpile_t *);
prefetchpile_t *mcpiledequeue(void);
void mcpiledisplay();
void mcdealloc(prefetchpile_t *);

#endif
