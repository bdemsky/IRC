#ifndef GARBAGE_H
#define GARBAGE_H
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
  unsigned int tc_mask;
  chashlistnode_t **tc_table;
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
