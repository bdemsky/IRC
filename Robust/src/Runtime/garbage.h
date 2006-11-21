#ifndef GARBAGE_H
#define GARBAGE_H
struct garbagelist {
  int size;
  struct garbagelist *next;
  void * array[];
};

void collect(struct garbagelist *stackptr);
int gc_createcopy(void * orig, void **);
void * mygcmalloc(struct garbagelist * ptr, int size);
#endif
