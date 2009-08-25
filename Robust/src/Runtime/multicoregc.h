#ifndef MULTICORE_GC_H
#define MULTICORE_GC_H

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

#endif // MULTICORE_GC_H
