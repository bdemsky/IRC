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
  struct ___Object___ * locklist;
};

#ifdef TASK
void fixtags();
#endif

#if defined(THREADS)||defined(DSTM)
void checkcollect(void * ptr);
struct listitem * stopforgc(struct garbagelist * ptr);
void restartaftergc(struct listitem * litem);
#endif
void * tomalloc(int size);
void collect(struct garbagelist *stackptr);
int gc_createcopy(void * orig, void **);
void * mygcmalloc(struct garbagelist * ptr, int size);
#endif
