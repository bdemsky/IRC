#ifndef _PREFETCH_H_
#define _PREFETCH_H_
#include "queue.h"
#include "dstm.h"

#define GET_STRIDE(x) ((x & 0x7000) >> 12)
#define GET_RANGE(x) (x & 0x0fff)
#define GET_STRIDEINC(x) ((x & 0x8000) >> 15)
#define GET_OID(x) ((int *) (x))
#define GET_NUM_OFFSETS(x) ((short *) (x + sizeof(unsigned int)))
#define GET_OFFSETS(x) ((short *) (x + sizeof(unsigned int) + sizeof(short)))

/****** Global structure **********/
typedef struct objOffsetPile {
  unsigned int oid;
  short numoffset;
  short *offsets;
  struct objOffsetPile *next;
} objOffsetPile_t;

typedef struct perMcPrefetchList {
  unsigned int mid;
  objOffsetPile_t *list;
  struct perMcPrefetchList *next;
} perMcPrefetchList_t;

typedef struct proPrefetchQ {
  perMcPrefetchList_t *front, *rear;
  pthread_mutex_t qlock;
  pthread_mutexattr_t qlockattr;
  pthread_cond_t qcond;
} proPrefetchQ_t;

typedef struct oidAtDepth {
  int depth; //TODO Remove may not need since depth is never read
  unsigned int oid;
} oidAtDepth_t;

// Global Prefetch Processing Queue
proPrefetchQ_t prefetchQ;

/**** Prefetch Queue to be processed functions ******/
void proPrefetchQDealloc(perMcPrefetchList_t *);

/******** Process Queue Element functions ***********/
void rangePrefetch(unsigned int, short, short *);
void *transPrefetchNew();
perMcPrefetchList_t* checkIfLocal(char *ptr);
int lookForObjs(int*, short *, int *, int *, int *, int *);
void insertPrefetch(int, unsigned int, short, short*, perMcPrefetchList_t **);

/******** Sending and Receiving Prefetches *******/
void sendRangePrefetchReq(perMcPrefetchList_t *, int sd);
int rangePrefetchReq(int acceptfd);
int processOidFound(objheader_t *, short *, int, int, int);
int processArrayOids(short *, objheader_t *, int *, int);
int findOidinStride(short *,  struct ArrayObject *, int, int, int, int, int, int);
int processLinkedListOids(short *, objheader_t *, int *, int);
int getRangePrefetchResponse(int sd);
objheader_t *searchObj(unsigned int);
void forwardRequest(unsigned int *, int*, int*, int*, short*);

/*********** Functions for computation at the participant end **********/
int getNextOid(short *, unsigned int*, int*, int*, oidAtDepth_t *, unsigned int);
unsigned int getNextArrayOid(short *, unsigned int *, int *, int*);
unsigned int getNextPointerOid(short *, unsigned int *, int *, int*);
int sendOidFound(unsigned int, int);
int sendOidNotFound(unsigned int oid, int sd);

/************* Internal functions *******************/
int getsize(short *ptr, int n);

#endif
