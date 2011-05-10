#ifndef GCQUEUE_H
#define GCQUEUE_H
#include "stdio.h"

#ifdef MGC
#include "interrupt.h"
#endif

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

extern struct pointerblock *gchead;
extern int gcheadindex;
extern struct pointerblock *gctail;
extern int gctailindex;
extern struct pointerblock *gctail2;
extern int gctailindex2;
extern struct pointerblock *gcspare;

extern struct lobjpointerblock *gclobjhead;
extern int gclobjheadindex;
extern struct lobjpointerblock *gclobjtail;
extern int gclobjtailindex;
extern struct lobjpointerblock *gclobjtail2;
extern int gclobjtailindex2;
extern struct lobjpointerblock *gclobjspare;

static void gc_queueinit() {
  // initialize queue
  if (gchead==NULL) {
    gcheadindex=gctailindex=gctailindex2 = 0;
    gchead=gctail=gctail2=RUNMALLOC(sizeof(struct pointerblock));
  } else {
    gctailindex=gctailindex2=gcheadindex=0;
    gctail=gctail2=gchead;
  }
  gchead->next=NULL;
  // initialize the large obj queues
  if (gclobjhead==NULL) {
    gclobjheadindex=0;
    gclobjtailindex=0;
    gclobjtailindex2=0;
    gclobjhead=gclobjtail=gclobjtail2=RUNMALLOC(sizeof(struct lobjpointerblock));
  } else {
    gclobjtailindex=gclobjtailindex2=gclobjheadindex=0;
    gclobjtail=gclobjtail2=gclobjhead;
  }
  gclobjhead->next=gclobjhead->prev=NULL;
}

////////////////////////////////////////////////////////////////////
// functions that should be invoked with interrupts off
////////////////////////////////////////////////////////////////////
static void gc_enqueue_I(unsigned int ptr) {
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
static unsigned int gc_dequeue_I() {
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

// dequeue and do not destroy the queue
static unsigned int gc_dequeue2_I() {
  if (gctailindex2==NUMPTRS) {
    struct pointerblock *tmp=gctail2;
    gctail2=gctail2->next;
    gctailindex2=0;
  } 
  return gctail2->ptrs[gctailindex2++];
}

static int gc_moreItems_I() {
  return !((gchead==gctail)&&(gctailindex==gcheadindex));
} 

static int gc_moreItems2_I() {
  return !((gchead==gctail2)&&(gctailindex2==gcheadindex));
} 

// should be invoked with interruption closed 
// enqueue a large obj: start addr & length
static void gc_lobjenqueue_I(unsigned int ptr,
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
static unsigned int gc_lobjdequeue_I(unsigned int * length,
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

static int gc_lobjmoreItems_I() {
  return !((gclobjhead==gclobjtail)&&(gclobjtailindex==gclobjheadindex));
} 

// dequeue and don't destroy the queue
static void gc_lobjdequeue2_I() {
  if (gclobjtailindex2==NUMLOBJPTRS) {
    gclobjtail2=gclobjtail2->next;
    gclobjtailindex2=1;
  } else {
    gclobjtailindex2++;
  }  
}

static int gc_lobjmoreItems2_I() {
  return !((gclobjhead==gclobjtail2)&&(gclobjtailindex2==gclobjheadindex));
} 

// 'reversly' dequeue and don't destroy the queue
static void gc_lobjdequeue3_I() {
  if (gclobjtailindex2==0) {
    gclobjtail2=gclobjtail2->prev;
    gclobjtailindex2=NUMLOBJPTRS-1;
  } else {
    gclobjtailindex2--;
  }  
}

static int gc_lobjmoreItems3_I() {
  return !((gclobjtail==gclobjtail2)&&(gclobjtailindex2==gclobjtailindex));
} 

static void gc_lobjqueueinit4_I() {
  gclobjtail2 = gclobjtail;
  gclobjtailindex2 = gclobjtailindex;
} 

static unsigned int gc_lobjdequeue4_I(unsigned int * length,
                                      unsigned int * host) {
  if (gclobjtailindex2==NUMLOBJPTRS) {
    gclobjtail2=gclobjtail2->next;
    gclobjtailindex2=0;
  } 
  if(length != NULL) {
    *length = gclobjtail2->lengths[gclobjtailindex2];
  }
  if(host != NULL) {
    *host = (unsigned int)(gclobjtail2->hosts[gclobjtailindex2]);
  }
  return gclobjtail2->lobjs[gclobjtailindex2++];
} 

static int gc_lobjmoreItems4_I() {
  return !((gclobjhead==gclobjtail2)&&(gclobjtailindex2==gclobjheadindex));
}

////////////////////////////////////////////////////////////////////
// functions that can be invoked in normal places
////////////////////////////////////////////////////////////////////
static void gc_enqueue(unsigned int ptr) {
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

static unsigned int gc_dequeue() {
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
  unsigned int r = gctail->ptrs[gctailindex++];
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  return r;
} 

static int gc_moreItems() {
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  int r = !((gchead==gctail)&&(gctailindex==gcheadindex));
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  return r;
}

// dequeue and do not destroy the queue
static unsigned int gc_dequeue2() {
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  if (gctailindex2==NUMPTRS) {
    struct pointerblock *tmp=gctail2;
    gctail2=gctail2->next;
    gctailindex2=0;
  } 
  unsigned int r = gctail2->ptrs[gctailindex2++];
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  return r;
}

static int gc_moreItems2() {
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  int r = !((gchead==gctail2)&&(gctailindex2==gcheadindex));
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  return r;
}

static void gc_lobjenqueue(unsigned int ptr,
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
