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

#define INLINE    inline __attribute__((always_inline))


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
perMcPrefetchList_t* processLocal(char *ptr);
perMcPrefetchList_t *processRemote(unsigned int oid, short * offsetarray, int sd, short numoffset);
void insertPrefetch(int, unsigned int, short, short*, perMcPrefetchList_t **);

/******** Sending and Receiving Prefetches *******/
void sendRangePrefetchReq(perMcPrefetchList_t *, int sd, unsigned int mid);
int rangePrefetchReq(int acceptfd);
int processOidFound(objheader_t *, short *, int, int, int);
int getRangePrefetchResponse(int sd);
INLINE objheader_t *searchObj(unsigned int);


/*********** Functions for computation at the participant end **********/
unsigned int getNextOid(objheader_t * header, short * offsetarray, unsigned int *dfsList, int top);
int sendOidFound(objheader_t *, unsigned int, int, char *buffer, int *bufoffset);
int sendOidNotFound(unsigned int oid, int sd);
void flushResponses(int sd, char * buffer, int * bufoffset);

#endif
