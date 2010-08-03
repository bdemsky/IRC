#include "garbage.h"
#include "runtime.h"
#include "structdefs.h"
#include "Queue.h"
#include "SimpleHash.h"
#include "chash.h"
#include "GenericHashtable.h"
#include <string.h>
#if defined(THREADS) || defined(DSTM) || defined(STM)
#include "thread.h"
#endif
#ifdef MLP
#include "workschedule.h"
extern struct QI * headqi;
extern struct QI * tailqi;
#endif

#ifdef DMALLOC
#include "dmalloc.h"
#endif
#ifdef DSTM
#ifdef RECOVERY
#include <DSTM/interface_recovery/dstm.h>
#else
#include <DSTM/interface/dstm.h>
#endif
#endif
#ifdef STM
#include "tm.h"
#endif
#ifdef DELAYCOMP
#include "delaycomp.h"
#endif


#define NUMPTRS 100

#define INITIALHEAPSIZE 8*1024*1024*1024L
#define GCPOINT(x) ((INTPTR)((x)*0.99))
/* This define takes in how full the heap is initially and returns a new heap size to use */
#define HEAPSIZE(x,y) ((INTPTR)(x+y))*2

#ifdef TASK
extern struct genhashtable * activetasks;
#ifndef MULTICORE
extern struct parameterwrapper * objectqueues[NUMCLASSES];
#endif
extern struct genhashtable * failedtasks;
extern struct taskparamdescriptor *currtpd;
extern struct ctable *forward;
extern struct ctable *reverse;
extern struct RuntimeHash *fdtoobject;
#endif

#ifdef GARBAGESTATS
#define MAXSTATS 500
long garbagearray[MAXSTATS];
#endif

#if defined(THREADS) || defined(DSTM) || defined(STM)||defined(MLP)
int needtocollect=0;
struct listitem * list=NULL;
int listcount=0;
#ifndef MAC
__thread struct listitem litem;
#endif
#endif

#ifdef MLP
__thread SESEcommon* seseCommon;
#endif

//Need to check if pointers are transaction pointers
//this also catches the special flag value of 1 for local copies
#ifdef DSTM
#define ENQUEUE(orig, dst) \
  if ((!(((unsigned int)orig)&0x1))) { \
    if (orig>=curr_heapbase&&orig<curr_heaptop) { \
      void *copy; \
      if (gc_createcopy(orig,&copy)) \
	enqueue(copy);\
      dst=copy; \
    } \
  }
#elif defined(STM)
#define ENQUEUE(orig, dst) \
  if (orig>=curr_heapbase&&orig<curr_heaptop) { \
    void *copy; \
    if (gc_createcopy(orig,&copy)) \
      enqueue(copy);\
    dst=copy; \
  }
#define SENQUEUE(orig, dst) \
  { \
    void *copy; \
    if (gc_createcopy(orig,&copy)) \
      enqueue(copy);\
    dst=copy; \
  }
#elif defined(FASTCHECK)
#define ENQUEUE(orig, dst) \
  if (((unsigned int)orig)!=1) { \
    void *copy; \
    if (gc_createcopy(orig,&copy)) \
      enqueue(copy);\
    dst=copy; }
#else
#define ENQUEUE(orig, dst) \
  if (orig>=curr_heapbase&&orig<curr_heaptop) {	\
     void *copy; \
     if (gc_createcopy(orig,&copy)) \
         enqueue(copy); \
     dst=copy; \
  }
#endif


struct pointerblock {
  void * ptrs[NUMPTRS];
  struct pointerblock *next;
};

void * curr_heapbase=0;
void * curr_heapptr=0;
void * curr_heapgcpoint=0;
void * curr_heaptop=0;

void * to_heapbase=0;
void * to_heapptr=0;
void * to_heaptop=0;
long lastgcsize=0;


struct pointerblock *head=NULL;
int headindex=0;
struct pointerblock *tail=NULL;
int tailindex=0;
struct pointerblock *spare=NULL;

void enqueue(void *ptr) {
  if (headindex==NUMPTRS) {
    struct pointerblock * tmp;
    if (spare!=NULL) {
      tmp=spare;
      spare=NULL;
    } else      tmp=malloc(sizeof(struct pointerblock));
    head->next=tmp;
    head=tmp;
    headindex=0;
  }
  head->ptrs[headindex++]=ptr;
}

void * dequeue() {
  if (tailindex==NUMPTRS) {
    struct pointerblock *tmp=tail;
    tail=tail->next;
    tailindex=0;
    if (spare!=NULL)
      free(tmp);
    else
      spare=tmp;
  }
  return tail->ptrs[tailindex++];
}

#ifdef STM
void fixobjlist(struct objlist * ptr) {
  while(ptr!=NULL) {
    int i;
    for(i=0;i<ptr->offset;i++) {
      SENQUEUE(ptr->objs[i], ptr->objs[i]);
    }
    ptr=ptr->next;
  }
}

void fixtable(chashlistnode_t ** tc_table, chashlistnode_t **tc_list, cliststruct_t **cstr, unsigned int tc_size) {
  unsigned int mask=(tc_size<<4)-1;
  chashlistnode_t *node=calloc(tc_size, sizeof(chashlistnode_t));
  chashlistnode_t *ptr=*tc_table;
  chashlistnode_t *curr;
  unsigned int i;
  unsigned int index;
  int isfirst;
  chashlistnode_t *newlist=NULL;
  for(i=0;i<tc_size;i++) {
    curr=&ptr[i];
    isfirst=1;
    do {                      //Inner loop to go through linked lists
      void * key;
      chashlistnode_t *tmp,*next;
      
      if ((key=(void *)curr->key) == 0) {             //Exit inner loop if there the first element is 0
	break;                  //key = val =0 for element if not present within the hash table
      }
      SENQUEUE(key, key);
      if (curr->val>=curr_heapbase&&curr->val<curr_heaptop) {
	SENQUEUE(curr->val, curr->val);
      } else {
	//rewrite transaction cache entry
	void *vptr=curr->val;
	int type=((int *)vptr)[0];
	unsigned INTPTR *pointer=pointerarray[type];
	if (pointer==0) {
	  //array of primitives - do nothing
	  struct ArrayObject *ao=(struct ArrayObject *) vptr;
	  SENQUEUE((void *)ao->___objlocation___, *((void **)&ao->___objlocation___));
	} else if (((INTPTR)pointer)==1) {
	  //array of pointers
	  struct ArrayObject *ao=(struct ArrayObject *) vptr;
	  int length=ao->___length___;
	  int i;
	  SENQUEUE((void *)ao->___objlocation___, *((void **)&ao->___objlocation___));
#ifdef STMARRAY
	  int lowindex=ao->lowindex;
	  int highindex=ao->highindex;
	  int j;
	  for(j=lowindex; j<=highindex; j++) {
	    unsigned int lockval;
	    GETLOCKVAL(lockval, ao, j);
	    if (lockval!=STMNONE) {
	      int lowi=(j<<INDEXSHIFT)/sizeof(void *);
	      int highi=lowi+(INDEXLENGTH/sizeof(void *));
	      for(i=lowi; i<highi;i++) {
#else
	      for(i=0; i<length; i++) {
#endif
		void *objptr=((void **)(((char *)&ao->___length___)+sizeof(int)))[i];
		SENQUEUE(objptr, ((void **)(((char *)&ao->___length___)+sizeof(int)))[i]);
	      }
#ifdef STMARRAY
	    }
	  }
#endif
	} else {
	  INTPTR size=pointer[0];
	  int i;
	  for(i=1; i<=size; i++) {
	    unsigned int offset=pointer[i];
	    void * objptr=*((void **)(((char *)vptr)+offset));
	    SENQUEUE(objptr, *((void **)(((char *)vptr)+offset)));
	  }
	}
      }

      next = curr->next;
      index = (((unsigned INTPTR)key) & mask) >>4;

      curr->key=key;
      tmp=&node[index];
      // Insert into the new table
      if(tmp->key == 0) {
	tmp->key = curr->key;
	tmp->val = curr->val;
	tmp->lnext=newlist;
	newlist=tmp;
      } else if (isfirst) {
	chashlistnode_t *newnode;
	if ((*cstr)->num<NUMCLIST) {
	  newnode=&(*cstr)->array[(*cstr)->num];
	  (*cstr)->num++;
	} else {
	  //get new list
	  cliststruct_t *tcl=calloc(1,sizeof(cliststruct_t));
	  tcl->next=*cstr;
	  *cstr=tcl;
	  newnode=&tcl->array[0];
	  tcl->num=1;
	}
	newnode->key = curr->key;
	newnode->val = curr->val;
	newnode->next = tmp->next;
	newnode->lnext=newlist;
	newlist=newnode;
	tmp->next=newnode;
      } else {
	curr->lnext=newlist;
	newlist=curr;
	curr->next=tmp->next;
	tmp->next=curr;
      }
      isfirst = 0;
      curr = next;
    } while(curr!=NULL);
  }
  free(ptr);
  (*tc_table)=node;
  (*tc_list)=newlist;
}
#endif

int moreItems() {
  if ((head==tail)&&(tailindex==headindex))
    return 0;
  return 1;
}

#ifdef TASK
struct pointerblock *taghead=NULL;
int tagindex=0;

void enqueuetag(struct ___TagDescriptor___ *ptr) {
  if (tagindex==NUMPTRS) {
    struct pointerblock * tmp=malloc(sizeof(struct pointerblock));
    tmp->next=taghead;
    taghead=tmp;
    tagindex=0;
  }
  taghead->ptrs[tagindex++]=ptr;
}
#endif

#if defined(STM)||defined(THREADS)||defined(MLP)
__thread char * memorybase=NULL;
__thread char * memorytop=NULL;
#endif


void collect(struct garbagelist * stackptr) {
#if defined(THREADS)||defined(DSTM)||defined(STM)||defined(MLP)
  needtocollect=1;
  pthread_mutex_lock(&gclistlock);
  while(1) {
    if ((listcount+1)==threadcount) {
      break; /* Have all other threads stopped */
    }
    pthread_cond_wait(&gccond, &gclistlock);
  }
#endif
#ifdef DELAYCOMP
  ptrstack.prev=stackptr;
  stackptr=(struct garbagelist *) &ptrstack;
#if defined(STMARRAY)&&!defined(DUALVIEW)
  arraystack.prev=stackptr;
  stackptr=(struct garbagelist *) &arraystack;
#endif
#endif

#ifdef GARBAGESTATS
  {
    int i;
    for(i=0;i<MAXSTATS;i++)
      garbagearray[i]=0;
  }
#endif

  if (head==NULL) {
    headindex=0;
    tailindex=0;
    head=tail=malloc(sizeof(struct pointerblock));
  }

#ifdef TASK
  if (taghead==NULL) {
    tagindex=0;
    taghead=malloc(sizeof(struct pointerblock));
    taghead->next=NULL;
  }
#endif

#ifdef STM
  if (c_table!=NULL) {
    fixtable(&c_table, &c_list, &c_structs, c_size);
    fixobjlist(newobjs);
#ifdef STMSTATS
    fixobjlist(lockedobjs);
#endif
  }
#endif
#if defined(STM)||defined(THREADS)||defined(MLP)
  memorybase=NULL;
#endif
 
  /* Check current stack */
#if defined(THREADS)||defined(DSTM)||defined(STM)||defined(MLP)
  {
    struct listitem *listptr=list;
    while(1) {
#endif
      
  while(stackptr!=NULL) {
    int i;
    for(i=0; i<stackptr->size; i++) {
      void * orig=stackptr->array[i];
      ENQUEUE(orig, stackptr->array[i]);
    }
    stackptr=stackptr->next;
  }
#if defined(THREADS)||defined(DSTM)||defined(STM)||defined(MLP)
  /* Go to next thread */
#ifndef MAC
  //skip over us
  if (listptr==&litem) {
#ifdef MLP
    // update forward list & memory queue for the current SESE
    updateForwardList(((SESEcommon*)listptr->seseCommon)->forwardList,FALSE);
    updateMemoryQueue((SESEcommon*)(listptr->seseCommon));
#endif
    listptr=listptr->next;
  }
#else
 {
  struct listitem *litem=pthread_getspecific(litemkey);
  if (listptr==litem) {
    listptr=listptr->next;
  }
 }
#endif
 
  if (listptr!=NULL) {
#ifdef THREADS
    void * orig=listptr->locklist;
    ENQUEUE(orig, listptr->locklist);
#endif
#ifdef STM
    if ((*listptr->tc_table)!=NULL) {
      fixtable(listptr->tc_table, listptr->tc_list, listptr->tc_structs, listptr->tc_size);
      fixobjlist(listptr->objlist);
#ifdef STMSTATS
      fixobjlist(listptr->lockedlist);
#endif
    }
#endif
#if defined(STM)||defined(THREADS)||defined(MLP)
    *(listptr->base)=NULL;
#endif
#ifdef MLP
    // update forward list & memory queue for all running SESEs.
    updateForwardList(((SESEcommon*)listptr->seseCommon)->forwardList,FALSE);
    updateMemoryQueue((SESEcommon*)(listptr->seseCommon));
#endif
    stackptr=listptr->stackptr;
    listptr=listptr->next;
  } else
    break;
}
}
#endif

#ifdef FASTCHECK
  ENQUEUE(___fcrevert___, ___fcrevert___);
#endif

#ifdef TASK
  {
    /* Update objectsets */
    int i;
    for(i=0; i<NUMCLASSES; i++) {
#if !defined(MULTICORE)
      struct parameterwrapper * p=objectqueues[i];
      while(p!=NULL) {
	struct ObjectHash * set=p->objectset;
	struct ObjectNode * ptr=set->listhead;
	while(ptr!=NULL) {
	  void *orig=(void *)ptr->key;
	  ENQUEUE(orig, *((void **)(&ptr->key)));
	  ptr=ptr->lnext;
	}
	ObjectHashrehash(set); /* Rehash the table */
	p=p->next;
      }
#endif
    }
  }

#ifndef FASTCHECK
  if (forward!=NULL) {
    struct cnode * ptr=forward->listhead;
    while(ptr!=NULL) {
      void * orig=(void *)ptr->key;
      ENQUEUE(orig, *((void **)(&ptr->key)));
      ptr=ptr->lnext;
    }
    crehash(forward); /* Rehash the table */
  }

  if (reverse!=NULL) {
    struct cnode * ptr=reverse->listhead;
    while(ptr!=NULL) {
      void *orig=(void *)ptr->val;
      ENQUEUE(orig, *((void**)(&ptr->val)));
      ptr=ptr->lnext;
    }
  }
#endif

  {
    struct RuntimeNode * ptr=fdtoobject->listhead;
    while(ptr!=NULL) {
      void *orig=(void *)ptr->data;
      ENQUEUE(orig, *((void**)(&ptr->data)));
      ptr=ptr->lnext;
    }
  }

  {
    /* Update current task descriptor */
    int i;
    for(i=0; i<currtpd->numParameters; i++) {
      void *orig=currtpd->parameterArray[i];
      ENQUEUE(orig, currtpd->parameterArray[i]);
    }

  }

  /* Update active tasks */
  {
    struct genpointerlist * ptr=activetasks->list;
    while(ptr!=NULL) {
      struct taskparamdescriptor *tpd=ptr->src;
      int i;
      for(i=0; i<tpd->numParameters; i++) {
	void * orig=tpd->parameterArray[i];
	ENQUEUE(orig, tpd->parameterArray[i]);
      }
      ptr=ptr->inext;
    }
    genrehash(activetasks);
  }

  /* Update failed tasks */
  {
    struct genpointerlist * ptr=failedtasks->list;
    while(ptr!=NULL) {
      struct taskparamdescriptor *tpd=ptr->src;
      int i;
      for(i=0; i<tpd->numParameters; i++) {
	void * orig=tpd->parameterArray[i];
	ENQUEUE(orig, tpd->parameterArray[i]);
      }
      ptr=ptr->inext;
    }
    genrehash(failedtasks);
  }
#endif

#ifdef MLP
  {
    // goes over ready-to-run SESEs
    struct QI * qitem = headqi;
    while(qitem!=NULL){
      SESEcommon* common=(SESEcommon*)qitem->value;
      if(common==seseCommon){
	// skip the current running SESE
      	qitem=qitem->next;
      	continue;
      }
      struct garbagelist * gl=(struct garbagelist *)&(((SESEcommon*)(qitem->value))[1]);
      struct garbagelist * glroot=gl;
      // update its ascendant SESEs 
      updateAscendantSESE(gl);	
  
      while(gl!=NULL) {
	int i;
	for(i=0; i<gl->size; i++) {
	  void * orig=gl->array[i];
	  ENQUEUE(orig, gl->array[i]);
	}
	gl=gl->next;
      } 
      qitem=qitem->next;
    }
  }    
#endif


  while(moreItems()) {
    void * ptr=dequeue();
    void *cpy=ptr;
    int type=((int *)cpy)[0];
    unsigned INTPTR * pointer;
#ifdef TASK
    if(type==TAGTYPE) {
      /* Enqueue Tag */
      /* Nothing is inside */
      enqueuetag(ptr);
      continue;
    }
#endif
    pointer=pointerarray[type];
    if (pointer==0) {
      /* Array of primitives */
      /* Do nothing */
#if defined(DSTM)||defined(FASTCHECK)
      struct ArrayObject *ao=(struct ArrayObject *) ptr;
      struct ArrayObject *ao_cpy=(struct ArrayObject *) cpy;
      ENQUEUE((void *)ao->___nextobject___, *((void **)&ao_cpy->___nextobject___));
      ENQUEUE((void *)ao->___localcopy___, *((void **)&ao_cpy->___localcopy___));
#endif
#if defined(STM)
      struct ArrayObject *ao=(struct ArrayObject *) ptr;
      struct ArrayObject *ao_cpy=(struct ArrayObject *) cpy;
      SENQUEUE((void *)ao->___objlocation___, *((void **)&ao_cpy->___objlocation___));
#endif
    } else if (((INTPTR)pointer)==1) {
      /* Array of pointers */
      struct ArrayObject *ao=(struct ArrayObject *) ptr;
      struct ArrayObject *ao_cpy=(struct ArrayObject *) cpy;
#if (defined(DSTM)||defined(FASTCHECK))
      ENQUEUE((void *)ao->___nextobject___, *((void **)&ao_cpy->___nextobject___));
      ENQUEUE((void *)ao->___localcopy___, *((void **)&ao_cpy->___localcopy___));
#endif
#if defined(STM)
      SENQUEUE((void *)ao->___objlocation___, *((void **)&ao_cpy->___objlocation___));
#endif
      int length=ao->___length___;
      int i;
      for(i=0; i<length; i++) {
	void *objptr=((void **)(((char *)&ao->___length___)+sizeof(int)))[i];
	ENQUEUE(objptr, ((void **)(((char *)&ao_cpy->___length___)+sizeof(int)))[i]);
      }
    } else {
      INTPTR size=pointer[0];
      int i;
      for(i=1; i<=size; i++) {
	unsigned int offset=pointer[i];
	void * objptr=*((void **)(((char *)ptr)+offset));
	ENQUEUE(objptr, *((void **)(((char *)cpy)+offset)));
      }
    }
  }
#ifdef TASK
  fixtags();
#endif

#ifdef MLP
  {
    //rehash memory queues of current running SESEs
    struct listitem *listptr=list;
    while(listptr!=NULL){
      rehashMemoryQueue((SESEcommon*)(listptr->seseCommon));
      listptr=listptr->next;
    }
  }
#endif

#if defined(THREADS)||defined(DSTM)||defined(STM)||defined(MLP)
  needtocollect=0;
  pthread_mutex_unlock(&gclistlock);
#endif
}

#ifdef TASK
/* Fix up the references from tags.  This can't be done earlier,
   because we don't want tags to keep objects alive */
void fixtags() {
  while(taghead!=NULL) {
    int i;
    struct pointerblock *tmp=taghead->next;
    for(i=0; i<tagindex; i++) {
      struct ___TagDescriptor___ *tagd=taghead->ptrs[i];
      struct ___Object___ *obj=tagd->flagptr;
      struct ___TagDescriptor___ *copy=((struct ___TagDescriptor___**)tagd)[1];
      if (obj==NULL) {
	/* Zero object case */
      } else if (obj->type==-1) {
	/* Single object case */
	copy->flagptr=((struct ___Object___**)obj)[1];
      } else if (obj->type==OBJECTARRAYTYPE) {
	/* Array case */
	struct ArrayObject *ao=(struct ArrayObject *) obj;
	int livecount=0;
	int j;
	int k=0;
	struct ArrayObject *aonew;

	/* Count live objects */
	for(j=0; j<ao->___cachedCode___; j++) {
	  struct ___Object___ * tobj=ARRAYGET(ao, struct ___Object___ *, j);
	  if (tobj->type==-1)
	    livecount++;
	}

	livecount=((livecount-1)/OBJECTARRAYINTERVAL+1)*OBJECTARRAYINTERVAL;
	aonew=(struct ArrayObject *) tomalloc(sizeof(struct ArrayObject)+sizeof(struct ___Object___*)*livecount);
	memcpy(aonew, ao, sizeof(struct ArrayObject));
	aonew->type=OBJECTARRAYTYPE;
	aonew->___length___=livecount;
	copy->flagptr=aonew;
	for(j=0; j<ao->___cachedCode___; j++) {
	  struct ___Object___ * tobj=ARRAYGET(ao, struct ___Object___ *, j);
	  if (tobj->type==-1) {
	    struct ___Object___ * tobjcpy=((struct ___Object___**)tobj)[1];
	    ARRAYSET(aonew, struct ___Object___*, k++,tobjcpy);
	  }
	}
	aonew->___cachedCode___=k;
	for(; k<livecount; k++) {
	  ARRAYSET(aonew, struct ___Object___*, k, NULL);
	}
      } else {
	/* No object live anymore */
	copy->flagptr=NULL;
      }
    }
    free(taghead);
    taghead=tmp;
    tagindex=NUMPTRS;
  }
}
#endif

void * tomalloc(int size) {
  void * ptr=to_heapptr;
  if ((size&7)!=0)
    size+=(8-(size%8));
  to_heapptr+=size;
  return ptr;
}

#if defined(THREADS)||defined(DSTM)||defined(STM)||defined(MLP)
void checkcollect(void * ptr) {
  stopforgc((struct garbagelist *)ptr);
  restartaftergc();
}

#ifdef DSTM
void checkcollect2(void * ptr) {
  int ptrarray[]={1, (int)ptr, (int) revertlist};
  stopforgc((struct garbagelist *)ptrarray);
  restartaftergc();
  revertlist=(struct ___Object___*)ptrarray[2];
}
#endif

void stopforgc(struct garbagelist * ptr) {
#ifdef DELAYCOMP
  //just append us to the list
  ptrstack.prev=ptr;
  ptr=(struct garbagelist *) &ptrstack;
#if defined(STMARRAY)&&!defined(DUALVIEW)
  arraystack.prev=ptr;
  ptr=(struct garbagelist *) &arraystack;
#endif
#endif
#ifndef MAC
  litem.stackptr=ptr;
#ifdef THREADS
  litem.locklist=pthread_getspecific(threadlocks);
#endif
#if defined(STM)||defined(THREADS)||defined(MLP)
  litem.base=&memorybase;
#endif
#ifdef STM
  litem.tc_size=c_size;
  litem.tc_table=&c_table;
  litem.tc_list=&c_list;
  litem.tc_structs=&c_structs;
  litem.objlist=newobjs;
#ifdef STMSTATS
  litem.lockedlist=lockedobjs;
#endif
#endif
#else
  //handle MAC
  struct listitem *litem=pthread_getspecific(litemkey);
  litem->stackptr=ptr;
#ifdef THREADS
  litem->locklist=pthread_getspecific(threadlocks);
#endif
#endif
  pthread_mutex_lock(&gclistlock);
  listcount++;
  if ((listcount+1)==threadcount) {
    //only do wakeup if we are ready to GC
    pthread_cond_signal(&gccond);
  }
  pthread_mutex_unlock(&gclistlock);
}

void restartaftergc() {
  if (needtocollect) {
    pthread_mutex_lock(&gclock); // Wait for GC
    pthread_mutex_unlock(&gclock);
  }
  pthread_mutex_lock(&gclistlock);
  listcount--;
  pthread_mutex_unlock(&gclistlock);
}
#endif

#if defined(STM)||defined(THREADS)||defined(MLP)
#define MEMORYBLOCK 65536
void * helper(struct garbagelist *, int);
void * mygcmalloc(struct garbagelist * stackptr, int size) {
  if ((size&7)!=0)
    size=(size&~7)+8;
  if (memorybase==NULL||size>(memorytop-memorybase)) {
    int toallocate=(size>MEMORYBLOCK)?size:MEMORYBLOCK;
    memorybase=helper(stackptr, toallocate);
    bzero(memorybase, toallocate);
    memorytop=memorybase+toallocate;
  }
  char *retvalue=memorybase;
  memorybase+=size;
  return retvalue;
}

void * helper(struct garbagelist * stackptr, int size) {
#else
void * mygcmalloc(struct garbagelist * stackptr, int size) {
#endif
  void *ptr;
#if defined(THREADS)||defined(DSTM)||defined(STM)||defined(MLP)
  while (pthread_mutex_trylock(&gclock)!=0) {
    stopforgc(stackptr);
    restartaftergc();
  }
#endif
  ptr=curr_heapptr;
  if ((size&7)!=0)
    size=(size&~7)+8;
  curr_heapptr+=size;
  if (curr_heapptr>curr_heapgcpoint||curr_heapptr<curr_heapbase) {
    if (curr_heapbase==0) {
      /* Need to allocate base heap */
      curr_heapbase=malloc(INITIALHEAPSIZE);
      if (curr_heapbase==NULL) {
	printf("malloc failed.  Garbage colletcor couldn't get enough memory.  Try changing heap size.\n");
	exit(-1);
      }
#if defined(STM)||defined(THREADS)||defined(MLP)
#else
      bzero(curr_heapbase, INITIALHEAPSIZE);
#endif
      curr_heaptop=curr_heapbase+INITIALHEAPSIZE;
      curr_heapgcpoint=((char *) curr_heapbase)+GCPOINT(INITIALHEAPSIZE);
      curr_heapptr=curr_heapbase+size;
          
      to_heapbase=malloc(INITIALHEAPSIZE);
      if (to_heapbase==NULL) {
	printf("malloc failed.  Garbage collector couldn't get enough memory.  Try changing heap size.\n");
	exit(-1);
      }
      
      to_heaptop=to_heapbase+INITIALHEAPSIZE;
      to_heapptr=to_heapbase;
      ptr=curr_heapbase;
#if defined(THREADS)||defined(DSTM)||defined(STM)||defined(MLP)
      pthread_mutex_unlock(&gclock);
#endif
      return ptr;
    }

    /* Grow the to heap if necessary */
    {
      INTPTR curr_heapsize=curr_heaptop-curr_heapbase;
      INTPTR to_heapsize=to_heaptop-to_heapbase;
      INTPTR last_heapsize=0;
      if (lastgcsize>0) {
	last_heapsize=HEAPSIZE(lastgcsize, size);
	if ((last_heapsize&7)!=0)
	  last_heapsize+=(8-(last_heapsize%8));
      }
      if (curr_heapsize>last_heapsize)
	last_heapsize=curr_heapsize;
      if (last_heapsize>to_heapsize) {
	free(to_heapbase);
	to_heapbase=malloc(last_heapsize);
	if (to_heapbase==NULL) {
	  printf("Error Allocating enough memory\n");
	  exit(-1);
	}
	to_heaptop=to_heapbase+last_heapsize;
	to_heapptr=to_heapbase;
      }
    }

    /* Do our collection */
    collect(stackptr);

    /* Update stat on previous gc size */
    lastgcsize=(to_heapptr-to_heapbase)+size;

#ifdef GARBAGESTATS
    printf("Garbage collected: Old bytes: %u\n", curr_heapptr-curr_heapbase);
    printf("New space: %u\n", to_heapptr-to_heapbase);
    printf("Total space: %u\n", to_heaptop-to_heapbase);
    {
      int i;
      for(i=0;i<MAXSTATS;i++) {
	if (garbagearray[i]!=0)
	  printf("Type=%d Size=%u\n", i, garbagearray[i]);
      }
    }
#endif
    /* Flip to/curr heaps */
    {
      void * tmp=to_heapbase;
      to_heapbase=curr_heapbase;
      curr_heapbase=tmp;

      tmp=to_heaptop;
      to_heaptop=curr_heaptop;
      curr_heaptop=tmp;

      tmp=to_heapptr;
      curr_heapptr=to_heapptr+size;
      curr_heapgcpoint=((char *) curr_heapbase)+GCPOINT(curr_heaptop-curr_heapbase);
      to_heapptr=to_heapbase;

      /* Not enough room :(, redo gc */
      if (curr_heapptr>curr_heapgcpoint) {
#if defined(THREADS)||defined(DSTM)||defined(STM)||defined(MLP)
	pthread_mutex_unlock(&gclock);
#endif
	return mygcmalloc(stackptr, size);
      }

      bzero(tmp, curr_heaptop-tmp);
#if defined(THREADS)||defined(DSTM)||defined(STM)||defined(MLP)
      pthread_mutex_unlock(&gclock);
#endif
      return tmp;
    }
  } else {
#if defined(THREADS)||defined(DSTM)||defined(STM)||defined(MLP)
    pthread_mutex_unlock(&gclock);
#endif
    return ptr;
  }
}

int gc_createcopy(void * orig, void ** copy_ptr) {
  if (orig==0) {
    *copy_ptr=NULL;
    return 0;
  } else {
    int type=((int *)orig)[0];
    if (type==-1) {
      *copy_ptr=((void **)orig)[1];
      return 0;
    }
    if (type<NUMCLASSES) {
      /* We have a normal object */
#ifdef STM
      int size=classsize[type]+sizeof(objheader_t);
      void *newobj=tomalloc(size);
      memcpy(newobj,((char *) orig)-sizeof(objheader_t), size);
      newobj=((char *)newobj)+sizeof(objheader_t);
#else
      int size=classsize[type];
      void *newobj=tomalloc(size);
      memcpy(newobj, orig, size);
#endif
#ifdef GARBAGESTATS
      garbagearray[type]+=size;
#endif
      ((int *)orig)[0]=-1;
      ((void **)orig)[1]=newobj;
      *copy_ptr=newobj;
      return 1;
    } else {
      /* We have an array */
      struct ArrayObject *ao=(struct ArrayObject *)orig;
      int elementsize=classsize[type];
      int length=ao->___length___;
#ifdef STM
#ifdef STMARRAY
      int basesize=length*elementsize;
      basesize=(basesize+LOWMASK)&HIGHMASK;
      int versionspace=sizeof(int)*2*(basesize>>INDEXSHIFT);
      int size=sizeof(struct ArrayObject)+basesize+sizeof(objheader_t)+versionspace;
      void *newobj=tomalloc(size);
      memcpy(newobj, ((char*)orig)-sizeof(objheader_t)-versionspace, size);
      newobj=((char *)newobj)+sizeof(objheader_t)+versionspace;
#else
      int size=sizeof(struct ArrayObject)+length*elementsize+sizeof(objheader_t);
      void *newobj=tomalloc(size);
      memcpy(newobj, ((char*)orig)-sizeof(objheader_t), size);
      newobj=((char *)newobj)+sizeof(objheader_t);
#endif
#else
      int size=sizeof(struct ArrayObject)+length*elementsize;
      void *newobj=tomalloc(size);
      memcpy(newobj, orig, size);
#endif
#ifdef GARBAGESTATS
      garbagearray[type]+=size;
#endif
      ((int *)orig)[0]=-1;
      ((void **)orig)[1]=newobj;
      *copy_ptr=newobj;
      return 1;
    }
  }
}

#ifdef MLP
updateForwardList(struct Queue *forwardList, int prevUpdate){

  struct QueueItem * fqItem=getHead(forwardList);
  while(fqItem!=NULL){
    struct garbagelist * gl=(struct garbagelist *)&(((SESEcommon*)(fqItem->objectptr))[1]);
    if(prevUpdate==TRUE){
      updateAscendantSESE(gl);	
    }
    // do something here
    while(gl!=NULL) {
      int i;
	 for(i=0; i<gl->size; i++) {
	   void * orig=gl->array[i];
	   ENQUEUE(orig, gl->array[i]);
	 }
	 gl=gl->next;
    }    
    // iterate forwarding list of seseRec      
    SESEcommon *common=(SESEcommon*)fqItem->objectptr;
    struct Queue* fList=common->forwardList;
    updateForwardList(fList,prevUpdate);   
    fqItem=getNextQueueItem(fqItem);
  }   

}

updateMemoryQueue(SESEcommon_p seseParent){
  // update memory queue
  int i,binidx;
  for(i=0; i<seseParent->numMemoryQueue; i++){
    MemoryQueue *memoryQueue=seseParent->memoryQueueArray[i];
    MemoryQueueItem *memoryItem=memoryQueue->head;
    while(memoryItem!=NULL){
      if(memoryItem->type==HASHTABLE){
	Hashtable *ht=(Hashtable*)memoryItem;
	for(binidx=0; binidx<NUMBINS; binidx++){
	  BinElement *bin=ht->array[binidx];
	  BinItem *binItem=bin->head;
	  while(binItem!=NULL){
	    if(binItem->type==READBIN){
	      ReadBinItem* readBinItem=(ReadBinItem*)binItem;
	      int ridx;
	      for(ridx=0; ridx<readBinItem->index; ridx++){
		REntry *rentry=readBinItem->array[ridx];		  
		struct garbagelist * gl= (struct garbagelist *)&(((SESEcommon*)(rentry->seseRec))[1]);
		updateAscendantSESE(gl);
		while(gl!=NULL) {
		  int i;
		  for(i=0; i<gl->size; i++) {
		    void * orig=gl->array[i];
		    ENQUEUE(orig, gl->array[i]);
		  }
		  gl=gl->next;
		} 
	      }	
	    }else{ //writebin
	      REntry *rentry=((WriteBinItem*)binItem)->val;
	      struct garbagelist * gl= (struct garbagelist *)&(((SESEcommon*)(rentry->seseRec))[1]);
	      updateAscendantSESE(gl);
	      while(gl!=NULL) {
		int i;
		for(i=0; i<gl->size; i++) {
		  void * orig=gl->array[i];
		  ENQUEUE(orig, gl->array[i]);
		}
		gl=gl->next;
	      } 
	    }
	    binItem=binItem->next;
	  }
	}
      }else if(memoryItem->type==VECTOR){
	Vector *vt=(Vector*)memoryItem;
	int idx;
	for(idx=0; idx<vt->index; idx++){
	  REntry *rentry=vt->array[idx];
	  if(rentry!=NULL){
	    struct garbagelist * gl= (struct garbagelist *)&(((SESEcommon*)(rentry->seseRec))[1]);
	    updateAscendantSESE(gl);
	    while(gl!=NULL) {
	      int i;
	      for(i=0; i<gl->size; i++) {
		void * orig=gl->array[i];
		ENQUEUE(orig, gl->array[i]);
	      }
	      gl=gl->next;
	    } 
	  }
	}
      }else if(memoryItem->type==SINGLEITEM){
	SCC *scc=(SCC*)memoryItem;
	REntry *rentry=scc->val;
	if(rentry!=NULL){
	  struct garbagelist * gl= (struct garbagelist *)&(((SESEcommon*)(rentry->seseRec))[1]);
	  updateAscendantSESE(gl);
	  while(gl!=NULL) {
	    int i;
	    for(i=0; i<gl->size; i++) {
	      void * orig=gl->array[i];
	      ENQUEUE(orig, gl->array[i]);
	    }
	    gl=gl->next;
	  } 
	}
      }
      memoryItem=memoryItem->next;
    }
  }     
 }
 
updateAscendantSESE(struct garbagelist *gl){ 
  int offsetsize=*((int*)((void*)gl-sizeof(int)));
  int prevSESECount=*((int*)((void*)gl+offsetsize));
  INTPTR tailaddr=(INTPTR)((void*)gl+offsetsize+sizeof(int));
  int prevIdx;
  for(prevIdx=0; prevIdx<prevSESECount; prevIdx++){
    SESEcommon* prevSESE=(SESEcommon*)*((INTPTR*)(tailaddr+sizeof(void*)*prevIdx));
    if(prevSESE!=NULL){
      struct garbagelist * prevgl=(struct garbagelist *)&(((SESEcommon*)(prevSESE))[1]);
      while(prevgl!=NULL) {
	int i;
	for(i=0; i<prevgl->size; i++) {
	  void * orig=prevgl->array[i];
	  ENQUEUE(orig, prevgl->array[i]);
	}
	prevgl=prevgl->next;
      } 
    }
  }
}
#endif

int within(void *ptr){ //debug function
  if(ptr>curr_heapptr || ptr<curr_heapbase){
    __asm__ __volatile__ ("int $3");  // breakpoint
  }
}
