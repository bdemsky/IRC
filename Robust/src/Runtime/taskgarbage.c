#include "garbage.h"
#include "runtime.h"
#include "structdefs.h"
#include "SimpleHash.h"
#include "GenericHashtable.h"
#include <string.h>

#ifdef TASK

extern struct genhashtable * activetasks;
extern struct genhashtable * failedtasks;
extern struct taskparamdescriptor *currtpd;
extern struct ctable *forward;
extern struct ctable *reverse;
extern struct RuntimeHash *fdtoobject;

#ifndef MULTICORE
extern struct parameterwrapper * objectqueues[NUMCLASSES];
#endif


void searchtaskroots() {
  {
    /* Update objectsets */
    int i;
    for(i=0; i<NUMCLASSES; i++) {
#if !defined(MULTICORE)
      struct parameterwrapper * p=objectqueues[i];
      while(p!=NULL) {
        struct ObjectHash * set=p->objectset;
        struct ObjectNode * ptr=set->listhead;
        while(ptr!=NULL) {
          void *orig=(void *)ptr->key;
          ENQUEUE(orig, *((void **)(&ptr->key)));
          ptr=ptr->lnext;
        }
        ObjectHashrehash(set); /* Rehash the table */
        p=p->next;
      }
#endif
    }
  }

#ifndef FASTCHECK
  if (forward!=NULL) {
    struct cnode * ptr=forward->listhead;
    while(ptr!=NULL) {
      void * orig=(void *)ptr->key;
      ENQUEUE(orig, *((void **)(&ptr->key)));
      ptr=ptr->lnext;
    }
    crehash(forward); /* Rehash the table */
  }

  if (reverse!=NULL) {
    struct cnode * ptr=reverse->listhead;
    while(ptr!=NULL) {
      void *orig=(void *)ptr->val;
      ENQUEUE(orig, *((void**)(&ptr->val)));
      ptr=ptr->lnext;
    }
  }
#endif

  {
    struct RuntimeNode * ptr=fdtoobject->listhead;
    while(ptr!=NULL) {
      void *orig=(void *)ptr->data;
      ENQUEUE(orig, *((void**)(&ptr->data)));
      ptr=ptr->lnext;
    }
  }

  {
    /* Update current task descriptor */
    int i;
    for(i=0; i<currtpd->numParameters; i++) {
      void *orig=currtpd->parameterArray[i];
      ENQUEUE(orig, currtpd->parameterArray[i]);
    }

  }

  /* Update active tasks */
  {
    struct genpointerlist * ptr=activetasks->list;
    while(ptr!=NULL) {
      struct taskparamdescriptor *tpd=ptr->src;
      int i;
      for(i=0; i<tpd->numParameters; i++) {
        void * orig=tpd->parameterArray[i];
        ENQUEUE(orig, tpd->parameterArray[i]);
      }
      ptr=ptr->inext;
    }
    genrehash(activetasks);
  }

  /* Update failed tasks */
  {
    struct genpointerlist * ptr=failedtasks->list;
    while(ptr!=NULL) {
      struct taskparamdescriptor *tpd=ptr->src;
      int i;
      for(i=0; i<tpd->numParameters; i++) {
        void * orig=tpd->parameterArray[i];
        ENQUEUE(orig, tpd->parameterArray[i]);
      }
      ptr=ptr->inext;
    }
    genrehash(failedtasks);
  }
}

struct pointerblock *taghead=NULL;
int tagindex=0;

void enqueuetag(struct ___TagDescriptor___ *ptr) {
  if (tagindex==NUMPTRS) {
    struct pointerblock * tmp=malloc(sizeof(struct pointerblock));
    tmp->next=taghead;
    taghead=tmp;
    tagindex=0;
  }
  taghead->ptrs[tagindex++]=ptr;
}

/* Fix up the references from tags.  This can't be done earlier,
   because we don't want tags to keep objects alive */
void fixtags() {
  while(taghead!=NULL) {
    int i;
    struct pointerblock *tmp=taghead->next;
    for(i=0; i<tagindex; i++) {
      struct ___TagDescriptor___ *tagd=taghead->ptrs[i];
      struct ___Object___ *obj=tagd->flagptr;
      struct ___TagDescriptor___ *copy=((struct ___TagDescriptor___**)tagd)[1];
      if (obj==NULL) {
        /* Zero object case */
      } else if (obj->type==-1) {
        /* Single object case */
        copy->flagptr=((struct ___Object___**)obj)[1];
      } else if (obj->type==OBJECTARRAYTYPE) {
        /* Array case */
        struct ArrayObject *ao=(struct ArrayObject *) obj;
        int livecount=0;
        int j;
        int k=0;
        struct ArrayObject *aonew;

        /* Count live objects */
        for(j=0; j<ao->___cachedCode___; j++) {
          struct ___Object___ * tobj=ARRAYGET(ao, struct ___Object___ *, j);
          if (tobj->type==-1)
            livecount++;
        }

        livecount=((livecount-1)/OBJECTARRAYINTERVAL+1)*OBJECTARRAYINTERVAL;
        aonew=(struct ArrayObject *) tomalloc(sizeof(struct ArrayObject)+sizeof(struct ___Object___*)*livecount);
        memcpy(aonew, ao, sizeof(struct ArrayObject));
        aonew->type=OBJECTARRAYTYPE;
        aonew->___length___=livecount;
        copy->flagptr=aonew;
        for(j=0; j<ao->___cachedCode___; j++) {
          struct ___Object___ * tobj=ARRAYGET(ao, struct ___Object___ *, j);
          if (tobj->type==-1) {
            struct ___Object___ * tobjcpy=((struct ___Object___**)tobj)[1];
            ARRAYSET(aonew, struct ___Object___*, k++,tobjcpy);
          }
        }
        aonew->___cachedCode___=k;
        for(; k<livecount; k++) {
          ARRAYSET(aonew, struct ___Object___*, k, NULL);
        }
      } else {
        /* No object live anymore */
        copy->flagptr=NULL;
      }
    }
    free(taghead);
    taghead=tmp;
    tagindex=NUMPTRS;
  }
}
#endif
