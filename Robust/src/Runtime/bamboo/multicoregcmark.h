#ifndef BABMOO_MULTICORE_GC_MARK_H
#define BAMBOO_MULTICORE_GC_MARK_H
#ifdef MULTICORE_GC
#include "multicore.h"

#define NUMPTRS 120

struct pointerblock {
  unsigned int ptrs[NUMPTRS];
  struct pointerblock *next;
};

#define NUMLOBJPTRS 20

struct lobjpointerblock {
  unsigned int lobjs[NUMLOBJPTRS];
  int lengths[NUMLOBJPTRS];
  int hosts[NUMLOBJPTRS];
  struct lobjpointerblock *next;
  struct lobjpointerblock *prev;
};

INLINE void gc_enqueue_I(unsigned int ptr);
INLINE unsigned int gc_dequeue_I();
INLINE void gc_lobjenqueue_I(unsigned int ptr, 
                             unsigned int length, 
                             unsigned int host);
INLINE int gc_lobjmoreItems_I();
INLINE void gc_lobjdequeue2_I();
INLINE int gc_lobjmoreItems2_I();
INLINE void gc_lobjdequeue3_I();
INLINE int gc_lobjmoreItems3_I();
INLINE void gc_lobjqueueinit4();
INLINE unsigned int gc_lobjdequeue4(unsigned int * length, 
                                    unsigned int * host);
INLINE int gc_lobjmoreItems4();

#endif // MULTICORE_GC
#endif // BAMBOO_MULTICORE_GC_MARK_H
