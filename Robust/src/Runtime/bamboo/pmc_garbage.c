#include "multicoregc.h"
#include "multicoreruntime.h"
#include "multicoregcprofile.h"
#include "pmc_garbage.h"
#include "runtime_arch.h"
#include "pmc_mark.h"
#include "pmc_forward.h"
#include "pmc_refupdate.h"
#ifdef PERFCOUNT
#include "bme_perf_counter.h"
#endif

struct pmc_heap * pmc_heapptr;
struct pmc_queue * pmc_localqueue;
volatile bool gcflag;

void incrementthreads() {
  tmc_spin_mutex_lock(&pmc_heapptr->lock);
  pmc_heapptr->numthreads++;
  tmc_spin_mutex_unlock(&pmc_heapptr->lock);
}

void decrementthreads() {
  tmc_spin_mutex_lock(&pmc_heapptr->lock);
  pmc_heapptr->numthreads--;
  tmc_spin_mutex_unlock(&pmc_heapptr->lock);
}

void * pmc_unitend(unsigned int index) {
  return gcbaseva+(index+1)*UNITSIZE;
}

void pmc_onceInit() {
  pmc_localqueue=&pmc_heapptr->regions[BAMBOO_NUM_OF_CORE].markqueue;
  pmc_queueinit(pmc_localqueue);
  if (BAMBOO_NUM_OF_CORE==STARTUPCORE) {
    tmc_spin_barrier_init(&pmc_heapptr->barrier, NUMCORES4GC);
    for(int i=0;i<NUMPMCUNITS;i++) {
      pmc_heapptr->units[i].endptr=pmc_unitend(i);
      //tprintf("Ch5: %u-> %x\n", i, pmc_heapptr->units[i].endptr);
    }
    
    for(int i=0;i<NUMCORES4GC;i+=2) {
      if (i==0) {
	pmc_heapptr->regions[i].lastptr=gcbaseva;
      } else
	pmc_heapptr->regions[i].lastptr=pmc_heapptr->units[i*4-1].endptr;
      pmc_heapptr->regions[i].lowunit=4*i;
      pmc_heapptr->regions[i].highunit=4*(i+1);
      if ((i+1)<NUMCORES4GC) {
	pmc_heapptr->regions[i+1].lastptr=pmc_heapptr->units[(i+1)*4+3].endptr;
	pmc_heapptr->regions[i+1].lowunit=4*(i+1);
	pmc_heapptr->regions[i+1].highunit=4*(i+2);
      }
    }
    //for(int i=0;i<NUMCORES4GC;i++) {
      //tprintf("%u lastptr=%x\n", i, pmc_heapptr->regions[i].lastptr);
    //}
  }
}

void pmc_init() {
  if (BAMBOO_NUM_OF_CORE==STARTUPCORE) {
    pmc_heapptr->numthreads=NUMCORES4GC;
    for(int i=0;i<NUMCORES4GC;i+=2) {
      void *startptr=pmc_heapptr->regions[i].lastptr;
      void *finishptr=(i+1)<NUMCORES4GC?pmc_heapptr->regions[i+1].lastptr:pmc_heapptr->regions[i].endptr;
      struct pmc_region *region=&pmc_heapptr->regions[i];
      unsigned int startindex=region->lowunit;
      unsigned int endindex=(i+1)<NUMCORES4GC?pmc_heapptr->regions[i+1].highunit:pmc_heapptr->regions[i].highunit;
      //tprintf("Free space in partition %u from %x to %x\n", i, startptr, finishptr);
      for(unsigned int index=startindex;index<endindex;index++) {
	void *ptr=pmc_heapptr->units[index].endptr;
	if ((ptr>startptr)&&(ptr<=finishptr)) {
	  padspace(startptr, (unsigned int)(ptr-startptr));
	  startptr=ptr;
	}
	if (ptr>finishptr) {
	  padspace(startptr, (unsigned int) (finishptr-startptr));
	  break;
	}
      }
    }
  }
  if (bamboo_smem_size) {
    //tprintf("Left over alloc space from %x to %x\n", bamboo_cur_msp, bamboo_cur_msp+bamboo_smem_size);
    padspace(bamboo_cur_msp, bamboo_smem_size);  
  }
  tmc_spin_barrier_wait(&pmc_heapptr->barrier);
}

void gc(struct garbagelist *gl) {
#ifdef PERFCOUNT
  profile_start(GC_REGION);
#endif
  if (BAMBOO_NUM_OF_CORE==STARTUPCORE) {
    tprintf("start GC\n");
    GCPROFILE_START_MASTER();
  }
  pmc_init();
  if (BAMBOO_NUM_OF_CORE==STARTUPCORE) {
    GCPROFILE_ITEM_MASTER();
  }
  //mark live objects
  //tprintf("mark\n");
  pmc_mark(gl);
  //count live objects per unit
  tmc_spin_barrier_wait(&pmc_heapptr->barrier);
  if (BAMBOO_NUM_OF_CORE==STARTUPCORE) {
    GCPROFILE_ITEM_MASTER();
  }
  //tprintf("count\n");
  pmc_count();
  tmc_spin_barrier_wait(&pmc_heapptr->barrier);
  if (BAMBOO_NUM_OF_CORE==STARTUPCORE) {
    GCPROFILE_ITEM_MASTER();
  }
  //divide up work
  //tprintf("divide\n");
  if (BAMBOO_NUM_OF_CORE==STARTUPCORE) {
    pmc_processunits();
    GCPROFILE_ITEM_MASTER();
  }
  tmc_spin_barrier_wait(&pmc_heapptr->barrier);
  //set up forwarding pointers
  //tprintf("forward\n");
  pmc_doforward();
  tmc_spin_barrier_wait(&pmc_heapptr->barrier);
  if (BAMBOO_NUM_OF_CORE==STARTUPCORE) {
    GCPROFILE_ITEM_MASTER();
  }
  //update pointers
  //tprintf("updaterefs\n");
  pmc_doreferenceupdate(gl);
  tmc_spin_barrier_wait(&pmc_heapptr->barrier);
  if (BAMBOO_NUM_OF_CORE==STARTUPCORE) {
    GCPROFILE_ITEM_MASTER();
  }
  //compact data
  //tprintf("compact\n");
  pmc_docompact();
  //reset memory allocation
  bamboo_cur_msp=NULL;
  bamboo_smem_size=0;
  //tprintf("done\n");

  //if (BAMBOO_NUM_OF_CORE==STARTUPCORE) {
  //      for(int i=0;i<NUMCORES4GC;i+=2) {
  //        void *startptr=pmc_heapptr->regions[i].lastptr;
  //        void *finishptr=pmc_heapptr->regions[i+1].lastptr;
  //        tprintf("Partition %u from %x to %x\n", i, startptr, finishptr);
  //        tprintf("%x %x %x %x\n", pmc_heapptr->regions[i].startptr, pmc_heapptr->regions[i].endptr, pmc_heapptr->regions[i+1].startptr, pmc_heapptr->regions[i+1].endptr);
  //      }
  //  }

  gcflag=false;
  tmc_spin_barrier_wait(&pmc_heapptr->barrier);
  if (BAMBOO_NUM_OF_CORE==STARTUPCORE) {
    GCPROFILE_RECORD_SPACE_MASTER();
    GCPROFILE_END_MASTER();
  }

#ifdef PERFCOUNT
  profile_start(APP_REGION);
#endif
  //tprintf("exit GC\n");
}

void padspace(void *ptr, unsigned int length) {
  //zero small blocks
  if (length<sizeof(struct ArrayObject)) {
    BAMBOO_MEMSET_WH(ptr,0,length);
  } else {
    //generate fake arrays for big blocks
    struct ArrayObject *ao=(struct ArrayObject *)ptr;
    ao->type=BYTEARRAYTYPE;
    unsigned arraylength=length-sizeof(struct ArrayObject);
    ao->___length___=arraylength;
    ao->marked=0;
  }
}

void gettype_size(void * ptr, unsigned int * ttype, unsigned int * tsize) {
  int type = ((int *)ptr)[0];
  //  if ((type>TOTALNUMCLASSANDARRAY)||(type<0)) {
  //    tprintf("ptr=%x type=%u\n", ptr, type);
  //    BAMBOO_EXIT();
  //  }

  if(type < NUMCLASSES) {
    // a normal object
    *tsize = classsize[type];
    *ttype = type;
  } else {
    // an array
    struct ArrayObject *ao=(struct ArrayObject *)ptr;
    unsigned int elementsize=classsize[type];
    unsigned int length=ao->___length___;
    *tsize = sizeof(struct ArrayObject)+length*elementsize;
    *ttype = type;
  } 
}
