#include "checkpoint.h"
#include "runtime.h"
#include "structdefs.h"
#include <string.h>
#include "Queue.h"
#ifdef DMALLOC
#include "dmalloc.h"
#endif
extern void * curr_heapbase;
extern void * curr_heapptr;
extern void * curr_heapgcpoint;
extern void * curr_heaptop;

extern void * to_heapbase;
extern void * to_heapptr;
extern void * to_heaptop;


#define MALLOCSIZE 20*1024

struct malloclist {
  struct malloclist *next;
  int size;
#ifdef RAW
  char * space;
#else
  char space[];
#endif
};

struct malloclist * top=NULL;
int offset=0;

void * cpmalloc(int size) {
  int endoffset=offset+size;
  int tmpoffset=0;
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
  tmpoffset=offset;
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

void checkvalid(void * ptr) {
  if (ptr>=curr_heapbase&&ptr<=curr_heaptop) {
#ifndef RAW
    printf("Valid\n");
#endif
  }
}

/*
   void validitycheck(struct ctable *forward, struct ctable *reverse) {
   struct RuntimeIterator rit;
   RuntimeHashiterator(forward, &rit);
   while(RunhasNext(&rit)) {
    struct ___Object___ * data=(struct ___Object___*) Runnext(&rit);
    int type=data->type;
    unsigned int * pointer=pointerarray[type];
    int size;
    int i;
    if (pointer!=0&&((int)pointer)!=1) {
      size=pointer[0];
      for(i=1; i<=size; i++) {
        int offset=pointer[i];
        void * ptr=*(void **)(((int) data) + offset);
        if (ptr!=NULL&&!RuntimeHashcontainskey(reverse, (int) ptr)) {
 #ifndef RAW
          printf("Bad\n");
 #endif
        }
        checkvalid(ptr);
      }
    }
   }

   RuntimeHashiterator(reverse, &rit);
   while(RunhasNext(&rit)) {
    struct ___Object___ * data=(struct ___Object___*) Runkey(&rit);
    int type=0;
    unsigned int * pointer=NULL;
    int size;
    int i;
    Runnext(&rit);
    type=data->type;
    pointer=pointerarray[type];
    if (pointer!=0&&((int)pointer)!=1) {
      size=pointer[0];
      for(i=1; i<=size; i++) {
        int offset=pointer[i];
        void * ptr=*(void **)(((int) data) + offset);
        if (ptr!=NULL&&!RuntimeHashcontainskey(reverse, (int) ptr)) {
 #ifndef RAW
          printf("Bad2\n");
 #endif
        }
        checkvalid(ptr);
      }
    }
   }
   }
 */


void ** makecheckpoint(int numparams, void ** srcpointer, struct ctable * forward, struct ctable * reverse) {
#ifdef PRECISE_GC
  void **newarray=cpmalloc(sizeof(void *)*numparams);
#else
  void **newarray=RUNMALLOC(sizeof(void *)*numparams);
#endif
  struct Queue *todo=createQueue();
  int i;

  for(i=0; i<numparams; i++) {
    void * objptr=srcpointer[i];
    void *dst;
    if ((dst=cSearch(forward, objptr))!=NULL)
      newarray[i]=dst;
    else {
      void * copy=createcopy(objptr);
      cInsert(forward, objptr, copy);
      cInsert(reverse, copy, objptr);
      addNewItem(todo, objptr);
      newarray[i]=copy;
    }
  }
  while(!isEmpty(todo)) {
    void * ptr=getItem(todo);
    int type=((int *)ptr)[0];
    {
      void *cpy;
      unsigned int * pointer=NULL;
      cpy=cSearch(forward, ptr);

      pointer=pointerarray[type];
#ifdef TASK
      if (type==TAGTYPE) {
        void *objptr=((struct ___TagDescriptor___*)ptr)->flagptr;
        if (objptr!=NULL) {
          void *dst;
          if ((dst=cSearch(forward, objptr))==NULL) {
            void *copy=createcopy(objptr);
            cInsert(forward, objptr, copy);
            cInsert(reverse, copy,  objptr);
            addNewItem(todo, objptr);
            ((struct ___TagDescriptor___*)cpy)->flagptr=copy;
          } else {
            ((struct ___TagDescriptor___*) cpy)->flagptr=dst;
          }
        }
      } else
#endif
      if (pointer==0) {
        /* Array of primitives */
        /* Do nothing */
      } else if (((int)pointer)==1) {
        /* Array of pointers */
        struct ArrayObject *ao=(struct ArrayObject *) ptr;
        struct ArrayObject *ao_cpy=(struct ArrayObject *) cpy;
        int length=ao->___length___;
        int i;
        for(i=0; i<length; i++) {
          void *dst;
          void *objptr=((void **)(((char *)&ao->___length___)+sizeof(int)))[i];
          if (objptr==NULL) {
            ((void **)(((char *)&ao_cpy->___length___)+sizeof(int)))[i]=NULL;
          } else if ((dst=cSearch(forward,objptr))!=NULL)
            ((void **)(((char *)&ao_cpy->___length___)+sizeof(int)))[i]=dst;
          else {
            void * copy=createcopy(objptr);
            cInsert(forward, objptr, copy);
            cInsert(reverse, copy, objptr);
            addNewItem(todo, objptr);
            ((void **)(((char *)&ao_cpy->___length___)+sizeof(int)))[i]=copy;
          }
        }
      } else {
        int size=pointer[0];
        int i;
        for(i=1; i<=size; i++) {
          int offset=pointer[i];
          void * objptr=*((void **)(((int)ptr)+offset));
          void *dst;
          if (objptr==NULL) {
            *((void **)(((int)cpy)+offset))=NULL;
          } else if ((dst=cSearch(forward, objptr))!=NULL)
            *((void **) &(((char *)cpy)[offset]))=dst;
          else {
            void * copy=createcopy(objptr);
            cInsert(forward, objptr, copy);
            cInsert(reverse, copy, objptr);
            addNewItem(todo, objptr);
            *((void **)(((int)cpy)+offset))=copy;
          }
        }
      }
    }
  }
  freeQueue(todo);
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

void restorecheckpoint(int numparams, void ** original, void ** checkpoint, struct ctable *forward, struct ctable * reverse) {
  struct Queue *todo=createQueue();
  struct ctable *visited=cCreate(256, 0.5);
  int i;

  for(i=0; i<numparams; i++) {
    if (checkpoint[i]!=NULL) {
      addNewItem(todo, checkpoint[i]);
      cInsert(visited, checkpoint[i], checkpoint[i]);
    }
  }

  while(!isEmpty(todo)) {
    void * ptr=(void *) getItem(todo);
    int type=((int *)ptr)[0];

    {
      void *cpy;
      unsigned int *pointer;
      int size;
      cpy=cSearch(reverse, ptr);
      pointer=pointerarray[type];
      size=classsize[type];
#ifdef TASK
      if (type==TAGTYPE) {
        void *objptr=((struct ___TagDescriptor___*)ptr)->flagptr;
        memcpy(cpy, ptr, size);
        if (objptr!=NULL) {
          if (cSearch(visited, objptr)==NULL) {
            cInsert(visited,  objptr, objptr);
            addNewItem(todo, objptr);
          }
          *((void **) &(((struct ___TagDescriptor___ *)cpy)->flagptr))=cSearch(reverse, objptr);
        }
      } else
#endif
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

        for(i=0; i<length; i++) {
          void *objptr=((void **)(((char *)&ao->___length___)+sizeof(int)))[i];
          if (objptr==NULL)
            ((void **)(((char *)&ao_cpy->___length___)+sizeof(int)))[i]=NULL;
          else {
            if (cSearch(visited, objptr)==NULL) {
              cInsert(visited,  objptr, objptr);
              addNewItem(todo, objptr);
            }
            *((void **) &((void **)(((char *)&ao_cpy->___length___)+sizeof(int)))[i])=cSearch(reverse, objptr);
          }
        }
      } else {
        int numptr=pointer[0];
        int i;
        void *flagptr;
        int oldflag;
        int currflag;
        if (hasflags[type]) {
          flagptr=(void *)(((int *)cpy)[2]);
          oldflag=(((int *)cpy)[1]);
          currflag=(((int *)ptr)[1]);
        }
        memcpy(cpy, ptr, size);
        for(i=1; i<=numptr; i++) {
          int offset=pointer[i];
          void * objptr=*((void **)(((int)ptr)+offset));
          if (objptr==NULL)
            *((void **)(((int)cpy)+offset))=NULL;
          else {
            if (cSearch(visited, objptr)==NULL) {
              cInsert(visited, objptr, objptr);
              addNewItem(todo, objptr);
            }
            *((void **) &(((char *)cpy)[offset]))=cSearch(reverse, objptr);
          }
        }
        if (hasflags[type]) {
          (((void **)cpy)[2])=flagptr;
          if (currflag!=oldflag) {
            flagorandinit(cpy, 0, 0xFFFFFFFF);
#ifdef MULTICORE
            enqueueObject(cpy, NULL,0); //TODO
#else
            enqueueObject(cpy);
#endif
          }
        }
      }
    }
  }
  freeQueue(todo);
  cDelete(visited);
}
