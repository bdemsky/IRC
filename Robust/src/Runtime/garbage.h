#ifndef GARBAGE_H
#define GARBAGE_H
#ifdef STM
#include "stmlookup.h"
#endif
struct garbagelist {
  int size;
  struct garbagelist *next;
  void * array[];
};

struct listitem {
  struct listitem * prev;
  struct listitem * next;
  struct garbagelist * stackptr;
#ifdef THREADS
  struct ___Object___ * locklist;
#endif
#ifdef STM
  unsigned int tc_size;
  chashlistnode_t **tc_table;
  chashlistnode_t **tc_list;
  struct objlist * objlist;
  char **base;
#endif
};

#ifdef TASK
void fixtags();
#endif

#if defined(THREADS)||defined(DSTM)||defined(STM)
extern int needtocollect;
void checkcollect(void * ptr);
struct listitem * stopforgc(struct garbagelist * ptr);
void restartaftergc(struct listitem * litem);
#endif
void * tomalloc(int size);
void collect(struct garbagelist *stackptr);
int gc_createcopy(void * orig, void **);
void * mygcmalloc(struct garbagelist * ptr, int size);
#endif
#ifdef STM
void fixtable(chashlistnode_t **, chashlistnode_t **, unsigned int);
#endif
