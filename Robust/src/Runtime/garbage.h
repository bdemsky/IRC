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
};

#ifdef THREADS
void checkcollect(void * ptr);
struct listitem * stopforgc(struct garbagelist * ptr);
void restartaftergc(struct listitem * litem);
#endif
void collect(struct garbagelist *stackptr);
int gc_createcopy(void * orig, void **);
void * mygcmalloc(struct garbagelist * ptr, int size);
#endif
