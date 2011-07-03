#ifndef GCQUEUE_H
#define GCQUEUE_H
#include "stdio.h"
#include "multicore.h"

#ifdef MGC
#include "interrupt.h"
#endif

#define NUMPTRS 120

struct pointerblock {
  void * ptrs[NUMPTRS];
  struct pointerblock *next;
};

#define NUMLOBJPTRS 20

struct lobjpointerblock {
  void * lobjs[NUMLOBJPTRS];
  int lengths[NUMLOBJPTRS];
  int hosts[NUMLOBJPTRS];
  struct lobjpointerblock *next;
  struct lobjpointerblock *prev;
};

extern struct pointerblock *gchead;
extern int gcheadindex;
extern struct pointerblock *gctail;
extern int gctailindex;
extern struct pointerblock *gcspare;

extern struct lobjpointerblock *gclobjhead;
extern int gclobjheadindex;
extern struct lobjpointerblock *gclobjtail;
extern int gclobjtailindex;
extern struct lobjpointerblock *gclobjspare;

INLINE static void gc_queueinit() {
  // initialize queue
  if (gchead==NULL) {
    gcheadindex=gctailindex=0;
    gchead=gctail=RUNMALLOC(sizeof(struct pointerblock));
  } else {
    gctailindex=gcheadindex=0;
    gctail=gchead;
  }
  gchead->next=NULL;
  // initialize the large obj queues
  if (gclobjhead==NULL) {
    gclobjheadindex=0;
    gclobjtailindex=0;
    gclobjhead=gclobjtail=RUNMALLOC(sizeof(struct lobjpointerblock));
  } else {
    gclobjtailindex=gclobjheadindex=0;
    gclobjtail=gclobjhead;
  }
  gclobjhead->next=gclobjhead->prev=NULL;
}

////////////////////////////////////////////////////////////////////
// functions that should be invoked with interrupts off
////////////////////////////////////////////////////////////////////
INLINE static void gc_enqueue_I(void * ptr) {
  if (gcheadindex==NUMPTRS) {
    struct pointerblock * tmp;
    if (gcspare!=NULL) {
      tmp=gcspare;
      gcspare=NULL;
      tmp->next = NULL;
    } else {
      tmp=RUNMALLOC_I(sizeof(struct pointerblock));
    } 
    gchead->next=tmp;
    gchead=tmp;
    gcheadindex=0;
  } 
  gchead->ptrs[gcheadindex++]=ptr;
}

// dequeue and destroy the queue
INLINE static void * gc_dequeue_I() {
  if (gctailindex==NUMPTRS) {
    struct pointerblock *tmp=gctail;
    gctail=gctail->next;
    gctailindex=0;
    if (gcspare!=NULL) {
      RUNFREE_I(tmp);
    } else {
      gcspare=tmp;
      gcspare->next = NULL;
    } 
  } 
  return gctail->ptrs[gctailindex++];
} 

INLINE static int gc_moreItems_I() {
  return !((gchead==gctail)&&(gctailindex==gcheadindex));
} 

// should be invoked with interruption closed 
// enqueue a large obj: start addr & length
INLINE static void gc_lobjenqueue_I(void * ptr,
                             unsigned int length,
                             unsigned int host) {
  if (gclobjheadindex==NUMLOBJPTRS) {
    struct lobjpointerblock * tmp;
    if (gclobjspare!=NULL) {
      tmp=gclobjspare;
      gclobjspare=NULL;
      tmp->next = NULL;
      tmp->prev = NULL;
    } else {
      tmp=RUNMALLOC_I(sizeof(struct lobjpointerblock));
    }  
    gclobjhead->next=tmp;
    tmp->prev = gclobjhead;
    gclobjhead=tmp;
    gclobjheadindex=0;
  } 
  gclobjhead->lobjs[gclobjheadindex]=ptr;
  gclobjhead->lengths[gclobjheadindex]=length;
  gclobjhead->hosts[gclobjheadindex++]=host;
} 

// dequeue and destroy the queue
INLINE static void * gc_lobjdequeue_I(unsigned int * length,
                                     unsigned int * host) {
  if (gclobjtailindex==NUMLOBJPTRS) {
    struct lobjpointerblock *tmp=gclobjtail;
    gclobjtail=gclobjtail->next;
    gclobjtailindex=0;
    gclobjtail->prev = NULL;
    if (gclobjspare!=NULL) {
      RUNFREE_I(tmp);
    } else {
      gclobjspare=tmp;
      tmp->next = NULL;
      tmp->prev = NULL;
    }  
  } 
  if(length != NULL) {
    *length = gclobjtail->lengths[gclobjtailindex];
  }
  if(host != NULL) {
    *host = (unsigned int)(gclobjtail->hosts[gclobjtailindex]);
  }
  return gclobjtail->lobjs[gclobjtailindex++];
} 

INLINE static int gc_lobjmoreItems_I() {
  return !((gclobjhead==gclobjtail)&&(gclobjtailindex==gclobjheadindex));
} 

////////////////////////////////////////////////////////////////////
// functions that can be invoked in normal places
////////////////////////////////////////////////////////////////////
INLINE static void gc_enqueue(void * ptr) {
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  if (gcheadindex==NUMPTRS) {
    struct pointerblock * tmp;
    if (gcspare!=NULL) {
      tmp=gcspare;
      gcspare=NULL;
      tmp->next = NULL;
    } else {
      tmp=RUNMALLOC_I(sizeof(struct pointerblock));
    } 
    gchead->next=tmp;
    gchead=tmp;
    gcheadindex=0;
  } 
  gchead->ptrs[gcheadindex++]=ptr;
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
}

INLINE static void * gc_dequeue() {
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  if (gctailindex==NUMPTRS) {
    struct pointerblock *tmp=gctail;
    gctail=gctail->next;
    gctailindex=0;
    if (gcspare!=NULL) {
      RUNFREE_I(tmp);
    } else {
      gcspare=tmp;
      gcspare->next = NULL;
    } 
  } 
  void * r = gctail->ptrs[gctailindex++];
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  return r;
} 

INLINE static int gc_moreItems() {
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  int r = !((gchead==gctail)&&(gctailindex==gcheadindex));
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  return r;
}

INLINE static void gc_lobjenqueue(void * ptr,
                           unsigned int length,
                           unsigned int host) {
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  if (gclobjheadindex==NUMLOBJPTRS) {
    struct lobjpointerblock * tmp;
    if (gclobjspare!=NULL) {
      tmp=gclobjspare;
      gclobjspare=NULL;
      tmp->next = NULL;
      tmp->prev = NULL;
    } else {
      tmp=RUNMALLOC_I(sizeof(struct lobjpointerblock));
    }  
    gclobjhead->next=tmp;
    tmp->prev = gclobjhead;
    gclobjhead=tmp;
    gclobjheadindex=0;
  } 
  gclobjhead->lobjs[gclobjheadindex]=ptr;
  gclobjhead->lengths[gclobjheadindex]=length;
  gclobjhead->hosts[gclobjheadindex++]=host;
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
} 
#endif
