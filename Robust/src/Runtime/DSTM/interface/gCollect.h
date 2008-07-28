#ifndef _GCOLLECT_H
#define _GCOLLECT_H

#include "dstm.h"
//#include "prelookup.h"

/***********************************
 ****** Global constants **********
 **********************************/
#define PREFETCH_FLUSH_COUNT_THRESHOLD 20

/*********************************
 ********* Global variables ******
 ********************************/
typedef struct prefetchNodeInfo {
  void *oldptr;
  void *newptr;
  int num_old_objstr;
  int maxsize;
} prefetchNodeInfo_t;

/********************************
 ******** Functions ************
 *******************************/
void *prefetchobjstrAlloc(unsigned int size);
void *normalPrefetchAlloc(objstr_t *, unsigned int);
void initializePCache();
void *lookUpFreeSpace(int);
void *clearNBlocks(void *, void *, unsigned int);
void clearPLookUpTable(void *, void *);
void updatePtrs();
#endif
