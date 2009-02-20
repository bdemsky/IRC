#ifndef _GCOLLECT_H
#define _GCOLLECT_H

#include "dstm.h"

/***********************************
 ****** Global constants **********
 **********************************/

#define STALE_MINTHRESHOLD 30

#define STALE_MAXTHRESHOLD 40 //ugly hack..if you make this too small things
// will fail in odd subtle ways

#define PREFETCH_FLUSH_THRESHOLD 20
#define STALL_THRESHOLD 30



/*********************************
 ********* Global variables ******
 ********************************/
typedef struct prefetchNodeInfo {
  objstr_t *oldptr;
  objstr_t *newptr;
  int os_count;

  objstr_t *oldstale;
  objstr_t *newstale;
  int stale_count;
  int stall;
  
} prefetchNodeInfo_t;

/********************************
 ******** Functions ************
 *******************************/
void *prefetchobjstrAlloc(unsigned int size);
void initializePCache();
void clearBlock(objstr_t *);
objstr_t *allocateNew(unsigned int size);
objstr_t * getObjStr(unsigned int size);
#endif
