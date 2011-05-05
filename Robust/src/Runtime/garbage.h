#ifndef GARBAGE_H
#define GARBAGE_H
#ifdef STM
#include "stmlookup.h"
#endif
#ifdef JNI
#include "jni-private.h"
#endif

#define NUMPTRS 100

struct pointerblock {
  void * ptrs[NUMPTRS];
  struct pointerblock *next;
};

//Need to check if pointers are transaction pointers
//this also catches the special flag value of 1 for local copies
#ifdef DSTM
#define ENQUEUE(orig, dst) \
  if ((!(((unsigned int)orig)&0x1))) { \
    if (orig>=curr_heapbase&&orig<curr_heaptop) { \
      void *copy; \
      if (gc_createcopy(orig,&copy))                                                                                                                                                                                                                                                                    \
        enqueue(copy);                                                                                                                                                                 \
      dst=copy; \
    } \
  }
#elif defined(STM)
#define ENQUEUE(orig, dst) \
  if (orig>=curr_heapbase&&orig<curr_heaptop) { \
    void *copy; \
    if (gc_createcopy(orig,&copy))                                                                                                                                                                                                                                                      \
      enqueue(copy);                                                                                                                                                   \
    dst=copy; \
  }
#define SENQUEUE(orig, dst) \
  { \
    void *copy; \
    if (gc_createcopy(orig,&copy))                                                                                                                                                                                                                                                      \
      enqueue(copy);                                                                                                                                                   \
    dst=copy; \
  }
#elif defined(FASTCHECK)
#define ENQUEUE(orig, dst) \
  if (((unsigned int)orig)!=1) { \
    void *copy; \
    if (gc_createcopy(orig,&copy))                                                                                                                                                                                                                                                      \
      enqueue(copy);                                                                                                                                                   \
    dst=copy; }
#else
#define ENQUEUE(orig, dst) \
  if (orig!=NULL) { \
    void *copy; \
    if (gc_createcopy(orig,&copy))                                                                                                                                                                                                                                                      \
      enqueue(copy);                                                                                                                                                    \
    dst=copy; \
  }
#endif

struct garbagelist {
  int size;
  struct garbagelist *next;
  void * array[];
};

extern void * curr_heapbase;
extern void * curr_heapptr;
extern void * curr_heapgcpoint;
extern void * curr_heaptop;

extern void * to_heapbase;
extern void * to_heapptr;
extern void * to_heaptop;

struct listitem {
  struct listitem * prev;
  struct listitem * next;
  struct garbagelist * stackptr;
#ifdef THREADS
  struct lockvector * lvector;
#endif
#ifdef JNI
  struct jnireferences ** jnirefs;
#endif
#ifdef STM
  unsigned int tc_size;
  cliststruct_t **tc_structs;
  chashlistnode_t **tc_table;
  chashlistnode_t **tc_list;
  struct objlist * objlist;
#ifdef STMSTATS
  struct objlist * lockedlist;
#endif
#endif
#if defined(THREADS)||defined(STM)||defined(MLP)
  char **base;
#endif
#ifdef MLP
  void *seseCommon;
#endif
};

#ifdef TASK
void fixtags();
extern struct pointerblock *taghead;
extern int tagindex;
#endif

#if defined(THREADS)||defined(DSTM)||defined(STM)||defined(MLP)
extern int needtocollect;
void checkcollect(void * ptr);
void stopforgc(struct garbagelist * ptr);
void restartaftergc();
#endif
void * tomalloc(int size);
void collect(struct garbagelist *stackptr);
int gc_createcopy(void * orig, void **);
void * mygcmalloc(struct garbagelist * ptr, int size);
#endif
#ifdef STM
void fixtable(chashlistnode_t **, chashlistnode_t **, cliststruct_t **, unsigned int);
#endif

int within(void *ptr);
