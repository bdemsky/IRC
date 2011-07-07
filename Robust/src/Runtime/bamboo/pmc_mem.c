#include <stdlib.h>
#include "multicoregc.h"
#include "multicoreruntime.h"
#include "pmc_garbage.h"
#include "pmc_mem.h"
#include "runtime_arch.h"
#include "multicoremsg.h"

void * pmc_alloc(unsigned int * numbytesallocated, unsigned int minimumbytes) {
  unsigned int memcheck=minimumbytes>PMC_MINALLOC?minimumbytes:PMC_MINALLOC;

  for(int i=0;i<NUMCORES4GC;i+=2) {
    void *startptr=pmc_heapptr->regions[i].lastptr;
    void *finishptr=pmc_heapptr->regions[i+1].lastptr;

    if ((finishptr-startptr)>memcheck) {
      struct pmc_region *region=&pmc_heapptr->regions[i];
      tmc_spin_mutex_lock(&region->lock);
      startptr=region->lastptr;
      do {
	//double check that we have the space
	if ((finishptr-startptr)<memcheck)
	  break;
	unsigned int startindex=region->lowunit;
	unsigned int endindex=pmc_heapptr->regions[i+1].highunit;
	void * newstartptr=startptr+memcheck;
	
	//update unit end points
	for(unsigned int index=startindex;index<endindex;index++) {
	  void *ptr=pmc_unitend(index);
	  if ((ptr>startptr)&&(ptr<newstartptr)) {
	    pmc_heapptr->units[index].endptr=newstartptr;
	  }
	  if (ptr>newstartptr)
	    break;
	}
	region->lastptr=newstartptr;

	*numbytesallocated=(unsigned int)(newstartptr-startptr);
	tmc_spin_mutex_unlock(&region->lock);
	return startptr;
      } while(0);
      tmc_spin_mutex_unlock(&region->lock);
    }
  }
  if (BAMBOO_NUM_OF_CORE==STARTUPCORE) {
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
    if (!gcflag) {
      gcflag = true;
      for(int i=0;i<NUMCORESACTIVE;i++) {
	if (i!=STARTUPCORE)
	  send_msg_1(i, GCSTARTPRE);
      }
    }
    BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  } else {
    send_msg_1_I(STARTUPCORE,GCINVOKE);
  }
  return NULL;
}
