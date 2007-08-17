#include "checkpoint.h"
#include "runtime.h"
#include "structdefs.h"
#include <string.h>
#ifdef DMALLOC
#include "dmalloc.h"
#endif

#define MALLOCSIZE 20*1024

struct malloclist {
  struct malloclist *next;
  int size;
  char space[];
};

struct malloclist * top=NULL;
int offset=0;

void * cpmalloc(int size) {
  int endoffset=offset+size;
  if (top==NULL||endoffset>top->size) {
    int basesize=MALLOCSIZE;
    struct malloclist *tmp;
    if (size>basesize)
      basesize=size;
    tmp=RUNMALLOC(sizeof(struct malloclist)+basesize);
    tmp->next=top;
    top=tmp;
    top->size=basesize;
    offset=0;
  }
  int tmpoffset=offset;
  offset+=size;
  return &top->space[tmpoffset];
}

void freemalloc() {
  while(top!=NULL) {
    struct malloclist *next=top->next;
    RUNFREE(top);
    top=next;
  }
}


void ** makecheckpoint(int numparams, void ** srcpointer, struct RuntimeHash * forward, struct RuntimeHash * reverse) {
#ifdef PRECISE_GC
  void **newarray=cpmalloc(sizeof(void *)*numparams);
#else
  void **newarray=RUNMALLOC(sizeof(void *)*numparams);
#endif
  struct RuntimeHash *todo=allocateRuntimeHash(100);
  int i;
  for(i=0;i<numparams;i++) {
    void * objptr=srcpointer[i];
    if (RuntimeHashcontainskey(forward, (int) objptr))
      RuntimeHashget(forward,(int) objptr,(int *) &newarray[i]);
    else {
      void * copy=createcopy(objptr);
      RuntimeHashadd(forward, (int) objptr, (int)copy);
      RuntimeHashadd(reverse, (int) copy, (int) objptr);
      RuntimeHashadd(todo, (int) objptr, (int) objptr);
      newarray[i]=copy;
    }
  }
  while(RuntimeHashcountset(todo)!=0) {
    void * ptr=(void *) RuntimeHashfirstkey(todo);
    int type=((int *)ptr)[0];
    RuntimeHashremove(todo, (int) ptr, (int) ptr);
    {
      void *cpy;
      RuntimeHashget(forward, (int) ptr, (int *) &cpy);
      unsigned int * pointer=pointerarray[type];
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
	  if (objptr==NULL) {
	    ((void **)(((char *)& ao->___length___)+sizeof(int)))[i]=NULL;
	  } else if (RuntimeHashcontainskey(forward, (int) objptr))
	    RuntimeHashget(forward,(int) objptr,(int *) &((void **)(((char *)& ao_cpy->___length___)+sizeof(int)))[i]);
	  else {
	    void * copy=createcopy(objptr);
	    RuntimeHashadd(forward, (int) objptr, (int)copy);
	    RuntimeHashadd(reverse, (int) copy, (int) objptr);
	    RuntimeHashadd(todo, (int) objptr, (int) objptr);
	    ((void **)(((char *)& ao_cpy->___length___)+sizeof(int)))[i]=copy;
	  }
	}
      } else {
	int size=pointer[0];
	int i;
	for(i=1;i<=size;i++) {
	  int offset=pointer[i];
	  void * objptr=*((void **)(((int)ptr)+offset));
	  if (objptr==NULL) {
	    *((void **) (((int)cpy)+offset))=NULL;
	  } else if (RuntimeHashcontainskey(forward, (int) objptr))
	    RuntimeHashget(forward, (int) objptr, (int *) &(((char *)cpy)[offset]));
	  else {
	    void * copy=createcopy(objptr);
	    RuntimeHashadd(forward, (int) objptr, (int) copy);
	    RuntimeHashadd(reverse, (int) copy, (int) objptr);
	    RuntimeHashadd(todo, (int) objptr, (int) objptr);
	    *((void **) (((int)cpy)+offset))=copy;
	  }
	}
      }
    }
  }
  freeRuntimeHash(todo);
  return newarray;
}

void * createcopy(void * orig) {
  if (orig==0)
    return 0;
  else {
    int type=((int *)orig)[0];
    if (type<NUMCLASSES) {
      /* We have a normal object */
      int size=classsize[type];
#ifdef PRECISE_GC
      void *newobj=cpmalloc(size);
#else
      void *newobj=RUNMALLOC(size);
#endif
      memcpy(newobj, orig, size);
      return newobj;
    } else {
      /* We have an array */
      struct ArrayObject *ao=(struct ArrayObject *)orig;
      int elementsize=classsize[type];
      int length=ao->___length___;
      int size=sizeof(struct ArrayObject)+length*elementsize;
#ifdef PRECISE_GC
      void *newobj=cpmalloc(size);
#else
      void *newobj=RUNMALLOC(size);
#endif
      memcpy(newobj, orig, size);
      return newobj;
    }
  }
}

void restorecheckpoint(int numparams, void ** original, void ** checkpoint, struct RuntimeHash *forward, struct RuntimeHash * reverse) {
  struct RuntimeHash *todo=allocateRuntimeHash(100);
  struct RuntimeHash *visited=allocateRuntimeHash(100);
  int i;

  for(i=0;i<numparams;i++) {
    if (checkpoint[i]!=NULL) {
      RuntimeHashadd(todo, (int) checkpoint[i], (int) checkpoint[i]);
      RuntimeHashadd(visited, (int) checkpoint[i], (int) checkpoint[i]);
    }
  }
  
  while(RuntimeHashcountset(todo)!=0) {
    void * ptr=(void *) RuntimeHashfirstkey(todo);
    int type=((int *)ptr)[0];
    RuntimeHashremove(todo, (int) ptr, (int) ptr);

    {
      void *cpy;
      unsigned int *pointer;
      int size;
      RuntimeHashget(reverse, (int) ptr, (int *) &cpy);
      pointer=pointerarray[type];
      size=classsize[type];

      if (pointer==0) {
	/* Array of primitives */
	struct ArrayObject *ao=(struct ArrayObject *) ptr;
	int length=ao->___length___;
	int cpysize=sizeof(struct ArrayObject)+length*size;
	memcpy(cpy, ptr, cpysize);

      } else if ((int)pointer==1) {
	/* Array of pointers */
	struct ArrayObject *ao=(struct ArrayObject *) ptr;
	struct ArrayObject *ao_cpy=(struct ArrayObject *) cpy;
	int length=ao->___length___;
	int i;
	int cpysize=sizeof(struct ArrayObject)+length*size;
	memcpy(ao_cpy, ao, cpysize);

	for(i=0;i<length;i++) {
	  void *objptr=((void **)(((char *)& ao->___length___)+sizeof(int)))[i];
	  if (objptr==NULL)
	    ((void **)(((char *)& ao_cpy->___length___)+sizeof(int)))[i]=NULL;
	  else {
	    if (!RuntimeHashcontainskey(visited, (int) objptr)) {
	      RuntimeHashadd(visited, (int) objptr, (int) objptr);
	      RuntimeHashadd(todo, (int) objptr, (int) objptr);
	    }
	    RuntimeHashget(reverse, (int) objptr, (int *) &((void **)(((char *)& ao_cpy->___length___)+sizeof(int)))[i]);
	  }
	}
      } else {
	int numptr=pointer[0];
	int i;
	void *flagptr;
	if (hasflags[type]) {
	  flagptr=(void *) (((int *)cpy)[2]);
	}
	memcpy(cpy, ptr, size);
	for(i=1;i<=numptr;i++) {
	  int offset=pointer[i];
	  void * objptr=*((void **)(((int)ptr)+offset));
	  if (objptr==NULL)
	    *((void **) (((int)cpy)+offset))=NULL;
	  else {
	    if (!RuntimeHashcontainskey(visited, (int) objptr)) {
	      RuntimeHashadd(visited, (int) objptr, (int) objptr);
	      RuntimeHashadd(todo, (int) objptr, (int) objptr);
	    }
	    RuntimeHashget(reverse, (int) objptr, (int *) &(((char *)cpy)[offset]));
	  }
	}
	if (hasflags[type]) {
	  (((void **)cpy)[2])=flagptr;
	  flagorandinit(cpy, 0, 0xFFFFFFFF);
	}
      }
    }
  }
  freeRuntimeHash(todo);
  freeRuntimeHash(visited);
}
