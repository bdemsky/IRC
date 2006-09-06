#include "checkpoint.h"
#include "runtime.h"
#include "structdefs.h"
#include <string.h>

void ** makecheckpoint(int numparams, void ** srcpointer, struct RuntimeHash * forward, struct RuntimeHash * reverse) {
  void **newarray=RUNMALLOC(sizeof(void *)*numparams);
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
      int * pointer=pointerarray[type];
      if (pointer==0) {
	/* Array of primitives */
	/* Do nothing */
      } else if (((int)pointer)==1) {
	/* Array of pointers */
	struct ArrayObject *ao=(struct ArrayObject *) ptr;
	int length=ao->___length___;
	int i;
	for(i=0;i<length;i++) {
	  void *objptr=((void **)(((char *)& ao->___length___)+sizeof(int)))[i];
	  if (RuntimeHashcontainskey(forward, (int) objptr))
	    RuntimeHashget(forward,(int) objptr,(int *) &((void **)(((char *)& ao->___length___)+sizeof(int)))[i]);
	  else {
	    void * copy=createcopy(objptr);
	    RuntimeHashadd(forward, (int) objptr, (int)copy);
	    RuntimeHashadd(reverse, (int) copy, (int) objptr);
	    RuntimeHashadd(todo, (int) objptr, (int) objptr);
	    ((void **)(((char *)& ao->___length___)+sizeof(int)))[i]=copy;
	  }
	}
      } else {
	int size=pointer[0];
	int i;
	for(i=1;i<=size;i++) {
	  int offset=pointer[i];
	  void * objptr=*((void **)(((int)ptr)+offset));
	  if (RuntimeHashcontainskey(forward, (int) objptr))
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
      void *newobj=RUNMALLOC(size);
      memcpy(newobj, orig, size);
      return newobj;
    } else {
      /* We have an array */
      struct ArrayObject *ao=(struct ArrayObject *)orig;
      int elementsize=classsize[type];
      int length=ao->___length___;
      int size=sizeof(struct ArrayObject)+length*elementsize;
      void *newobj=RUNMALLOC(size);
      memcpy(newobj, orig, size);
      return newobj;
    }
  }
}

void restorecheckpoint(int numparams, void ** original, void ** checkpoint, struct RuntimeHash *forward, struct RuntimeHash * reverse) {
  struct RuntimeHash *todo=allocateRuntimeHash(100);
  int i;

  for(i=0;i<numparams;i++) {
    RuntimeHashadd(todo, (int) checkpoint[i], (int) checkpoint[i]);
  }

  while(RuntimeHashcountset(todo)!=0) {
    void * ptr=(void *) RuntimeHashfirstkey(todo);
    int type=((int *)ptr)[0];
    RuntimeHashremove(todo, (int) ptr, (int) ptr);
    {
      void *cpy;
      RuntimeHashget(reverse, (int) ptr, (int *) &cpy);
      int * pointer=pointerarray[type];
      if (pointer==0) {
	/* Array of primitives */
	/* Do nothing */
      } else if ((int)pointer==1) {
	/* Array of pointers */
	struct ArrayObject *ao=(struct ArrayObject *) ptr;
	int length=ao->___length___;
	int i;
	for(i=0;i<length;i++) {
	  void *objptr=((void **)(((char *)& ao->___length___)+sizeof(int)))[i];
	  RuntimeHashget(reverse, (int) objptr, (int *) &((void **)(((char *)& ao->___length___)+sizeof(int)))[i]);
	}
      } else {
	int size=pointer[0];
	int i;
	for(i=1;i<=size;i++) {
	  int offset=pointer[i];
	  void * objptr=*((void **)(((int)ptr)+offset));
	  RuntimeHashget(reverse, (int) objptr, (int *) &(((char *)cpy)[offset]));
	}
      }
    }
  }
}


