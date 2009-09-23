#ifndef _GCOLLECT_H
#define _GCOLLECT_H

#include "dstm.h"

/***********************************
 ****** Global constants **********
 **********************************/

#define STALE_MINTHRESHOLD 10 //minimum size

#define STALE_MAXTHRESHOLD 30 //ugly hack..if you make this too small things
// will fail in odd subtle ways

#define DEFAULT_OBJ_STORE_SIZE (4194304-16) //just a little less the 4MB
#define PREFETCH_FLUSH_THRESHOLD 20 //MINIMUM SIZE BEFORE FLUSHING
#define STALL_THRESHOLD 15 //number of prefetches stores before we can start freeing old ones



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
