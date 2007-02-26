#include "garbage.h"
#include "runtime.h"
#include "structdefs.h"
#include "Queue.h"
#include "SimpleHash.h"
#include "GenericHashtable.h"
#include <string.h>
#ifdef THREADS
#include "thread.h"
#endif
#ifdef DMALLOC
#include "dmalloc.h"
#endif


#define NUMPTRS 100

#define INITIALHEAPSIZE 10*1024
#define GCPOINT(x) ((int)((x)*0.9))
/* This define takes in how full the heap is initially and returns a new heap size to use */
#define HEAPSIZE(x,y) (((int)((x)/0.6))+y)

#ifdef TASK
extern struct Queue * activetasks;
extern struct parameterwrapper * objectqueues[NUMCLASSES];
extern struct genhashtable * failedtasks;
extern struct taskparamdescriptor *currtpd;
extern struct RuntimeHash *forward;
extern struct RuntimeHash *reverse;
extern struct RuntimeHash *fdtoobject;
#endif

#ifdef THREADS
int needtocollect=0;
struct listitem * list=NULL;
int listcount=0;
#endif

struct pointerblock {
  void * ptrs[NUMPTRS];
  struct pointerblock *next;
};

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
    } else 
      tmp=malloc(sizeof(struct pointerblock));
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

int moreItems() {
  if ((head==tail)&&(tailindex==headindex))
    return 0;
  return 1;
}

void collect(struct garbagelist * stackptr) {
#ifdef THREADS
  needtocollect=1;
  pthread_mutex_lock(&gclistlock);
  while(1) {
    if ((listcount+1)==threadcount) {
      break; /* Have all other threads stopped */
    }
    pthread_cond_wait(&gccond, &gclistlock);
  }
#endif

  if (head==NULL) {
    headindex=0;
    tailindex=0;
    head=tail=malloc(sizeof(struct pointerblock));
  }
  /* Check current stack */
#ifdef THREADS
 {
   struct listitem *listptr=list;
   while(stackptr!=NULL) {
#endif
     
  while(stackptr!=NULL) {
    int i;
    for(i=0;i<stackptr->size;i++) {
      void * orig=stackptr->array[i];
      void * copy;
      if (gc_createcopy(orig,&copy))
	enqueue(orig);
      stackptr->array[i]=copy;
    }
    stackptr=stackptr->next;
  }
#ifdef THREADS
  /* Go to next thread */
  if (listptr!=NULL) {
    void * orig=listptr->locklist;
    void * copy;
    if (gc_createcopy(orig,&copy))
      enqueue(orig);
    listptr->locklist=copy;
    stackptr=listptr->stackptr;
    listptr=listptr->next;
  }
   }
 }
#endif
  
#ifdef TASK
  {
    /* Update objectsets */
    int i;
    for(i=0;i<NUMCLASSES;i++) {
      struct parameterwrapper * p=objectqueues[i];
      while(p!=NULL) {
	struct RuntimeHash * set=p->objectset;
	struct RuntimeNode * ptr=set->listhead;
	while(ptr!=NULL) {
	  void *orig=(void *)ptr->key;
	  void *copy;
	  if (gc_createcopy(orig, &copy))
	    enqueue(orig);
	  ptr->key=(int)copy;
	  
	  ptr=ptr->lnext;
	}
	RuntimeHashrehash(set); /* Rehash the table */
	p=p->next;
      }
    }
  }
  
  if (forward!=NULL) {
    struct RuntimeNode * ptr=forward->listhead;
    while(ptr!=NULL) {
      void * orig=(void *)ptr->key;
      void *copy;
      if (gc_createcopy(orig, &copy))
	enqueue(orig);
      ptr->key=(int)copy;

      ptr=ptr->lnext;
    }
    RuntimeHashrehash(forward); /* Rehash the table */
  }

  if (reverse!=NULL) {
    struct RuntimeNode * ptr=reverse->listhead;
    while(ptr!=NULL) {
      void *orig=(void *)ptr->data;
      void *copy;
      if (gc_createcopy(orig, &copy))
	enqueue(orig);
      ptr->data=(int)copy;

      ptr=ptr->lnext;
    }
  }

  {
    struct RuntimeNode * ptr=fdtoobject->listhead;
    while(ptr!=NULL) {
      void *orig=(void *)ptr->data;
      void *copy;
      if (gc_createcopy(orig, &copy))
	enqueue(orig);
      ptr->data=(int)copy;

      ptr=ptr->lnext;
    }
  }

  {
    /* Update current task descriptor */
    int i;
    for(i=0;i<currtpd->numParameters;i++) {
      void *orig=currtpd->parameterArray[i];
      void *copy;
      if (gc_createcopy(orig, &copy))
	enqueue(orig);
      currtpd->parameterArray[i]=copy;
    }

  }

  {
    /* Update active tasks */
    struct QueueItem * ptr=activetasks->head;
    while (ptr!=NULL) {
      struct taskparamdescriptor *tpd=ptr->objectptr;
      int i;
      for(i=0;i<tpd->numParameters;i++) {
	void *orig=tpd->parameterArray[i];
	void *copy;
	if (gc_createcopy(orig, &copy))
	  enqueue(orig);
	tpd->parameterArray[i]=copy;
      }
      ptr=ptr->next;
    }
  }
    /* Update failed tasks */
  {
    struct genpointerlist * ptr=failedtasks->list;
    while(ptr!=NULL) {
      struct taskparamdescriptor *tpd=ptr->src;
      int i;
      for(i=0;i<tpd->numParameters;i++) {
	void * orig=tpd->parameterArray[i];
	void * copy;
	if (gc_createcopy(orig, &copy))
	  enqueue(orig);
	tpd->parameterArray[i]=copy;
      }
      ptr=ptr->inext;
    }
    genrehash(failedtasks);
  }
#endif

  while(moreItems()) {
    void * ptr=dequeue();
    void *cpy=((void **)ptr)[1];
    int type=((int *)cpy)[0];
    int * pointer=pointerarray[type];
    if (pointer==0) {
      /* Array of primitives */
      /* Do nothing */
    } else if (((int)pointer)==1) {
      /* Array of pointers */
      struct ArrayObject *ao=(struct ArrayObject *) ptr;
      struct ArrayObject *ao_cpy=(struct ArrayObject *) cpy;
      int length=ao->___length___;
      int i;
      for(i=0;i<length;i++) {
	void *objptr=((void **)(((char *)& ao->___length___)+sizeof(int)))[i];
	void * copy;
	if (gc_createcopy(objptr, &copy))
	  enqueue(objptr);
	((void **)(((char *)& ao_cpy->___length___)+sizeof(int)))[i]=copy;
      }
    } else {
      int size=pointer[0];
      int i;
      for(i=1;i<=size;i++) {
	int offset=pointer[i];
	void * objptr=*((void **)(((int)ptr)+offset));
	void * copy;
	if (gc_createcopy(objptr, &copy))
	  enqueue(objptr);
	*((void **) (((int)cpy)+offset))=copy;
      }
    }
  }
#ifdef THREADS
  needtocollect=0;
  pthread_mutex_unlock(&gclistlock);
#endif
}

void * curr_heapbase=0;
void * curr_heapptr=0;
void * curr_heapgcpoint=0;
void * curr_heaptop=0;

void * to_heapbase=0;
void * to_heapptr=0;
void * to_heaptop=0;
long lastgcsize=0;

void * tomalloc(int size) {
  void * ptr=to_heapptr;
  if ((size%4)!=0)
    size+=(4-(size%4));
  to_heapptr+=size;
  return ptr;
}

#ifdef THREADS

void checkcollect(void * ptr) {
  if (needtocollect) {
    struct listitem * tmp=stopforgc((struct garbagelist *)ptr);
    pthread_mutex_lock(&gclock); // Wait for GC
    restartaftergc(tmp);
    pthread_mutex_unlock(&gclock);

  }
}

struct listitem * stopforgc(struct garbagelist * ptr) {
  struct listitem * litem=malloc(sizeof(struct listitem));
  litem->stackptr=ptr;
  litem->locklist=pthread_getspecific(threadlocks);
  litem->prev=NULL;
  pthread_mutex_lock(&gclistlock);
  litem->next=list;
  if(list!=NULL)
    list->prev=litem;
  list=litem;
  listcount++;
  pthread_cond_signal(&gccond);
  pthread_mutex_unlock(&gclistlock);
  return litem;
}

void restartaftergc(struct listitem * litem) {
  pthread_mutex_lock(&gclistlock);
  pthread_setspecific(threadlocks, litem->locklist);
  if (litem->prev==NULL) {
    list=litem->next;
  } else {
    litem->prev->next=litem->next;
  }
  if (litem->next!=NULL) {
    litem->next->prev=litem->prev;
  }
  listcount--;
  pthread_mutex_unlock(&gclistlock);
  free(litem);
}
#endif

void * mygcmalloc(struct garbagelist * stackptr, int size) {
  void *ptr;
#ifdef THREADS
  if (pthread_mutex_trylock(&gclock)!=0) {
    struct listitem *tmp=stopforgc(stackptr);
    pthread_mutex_lock(&gclock);
    restartaftergc(tmp);
  }
#endif
  ptr=curr_heapptr;
  if ((size%4)!=0)
    size+=(4-(size%4));
  curr_heapptr+=size;
  if (curr_heapptr>curr_heapgcpoint) {
    if (curr_heapbase==0) {
      /* Need to allocate base heap */
      curr_heapbase=malloc(INITIALHEAPSIZE);
      bzero(curr_heapbase, INITIALHEAPSIZE);
      curr_heaptop=curr_heapbase+INITIALHEAPSIZE;
      curr_heapgcpoint=((char *) curr_heapbase)+GCPOINT(INITIALHEAPSIZE);
      curr_heapptr=curr_heapbase+size;

      to_heapbase=malloc(INITIALHEAPSIZE);
      to_heaptop=to_heapbase+INITIALHEAPSIZE;
      to_heapptr=to_heapbase;
      ptr=curr_heapbase;
#ifdef THREADS
      pthread_mutex_unlock(&gclock);
#endif
      return ptr;
    }

    /* Grow the to heap if necessary */
    {
      int curr_heapsize=curr_heaptop-curr_heapbase;
      int to_heapsize=to_heaptop-to_heapbase;
      int last_heapsize=0;
      if (lastgcsize>0) {
	last_heapsize=HEAPSIZE(lastgcsize, size);
	if ((last_heapsize%4)!=0)
	  last_heapsize+=(4-(last_heapsize%4));
      }
      if (curr_heapsize>last_heapsize)
	last_heapsize=curr_heapsize;
      if (last_heapsize>to_heapsize) {
	free(to_heapbase);
	to_heapbase=malloc(last_heapsize);
	to_heaptop=to_heapbase+last_heapsize;
	to_heapptr=to_heapbase;
      }
    }
   
    /* Do our collection */
    collect(stackptr);

    /* Update stat on previous gc size */
    lastgcsize=(to_heapptr-to_heapbase)+size;

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
#ifdef THREADS
	pthread_mutex_unlock(&gclock);
#endif
	return mygcmalloc(stackptr, size);
      }
      
      bzero(tmp, curr_heaptop-tmp);
#ifdef THREADS
      pthread_mutex_unlock(&gclock);
#endif
      return tmp;
    }
  } else {
#ifdef THREADS
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
    } if (type<NUMCLASSES) {
      /* We have a normal object */
      int size=classsize[type];
      void *newobj=tomalloc(size);
      memcpy(newobj, orig, size);
      ((int *)orig)[0]=-1;
      ((void **)orig)[1]=newobj;
      *copy_ptr=newobj;
      return 1;
    } else {
      /* We have an array */
      struct ArrayObject *ao=(struct ArrayObject *)orig;
      int elementsize=classsize[type];
      int length=ao->___length___;
      int size=sizeof(struct ArrayObject)+length*elementsize;
      void *newobj=tomalloc(size);
      memcpy(newobj, orig, size);
      ((int *)orig)[0]=-1;
      ((void **)orig)[1]=newobj;
      *copy_ptr=newobj;
      return 1;
    }
  }
}
