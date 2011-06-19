#ifndef BAMBOO_MULTICORE_GC_H
#define BAMBOO_MULTICORE_GC_H

struct garbagelist {
  int size;
  struct garbagelist *next;
  void * array[];
};

#endif // BAMBOO_MULTICORE_GC_H
