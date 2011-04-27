#include <stdio.h>
#include <sys/types.h>
#ifndef MULTICORE
#include <sys/stat.h>
#include <fcntl.h>
#endif
#include <stdlib.h>
#include <limits.h>

#include "GenericHashtable.h"
#include "mem.h"
#ifdef DMALLOC
#include "dmalloc.h"
#endif

void * getfirstkey(struct genhashtable *ht) {
  if(ht->list == NULL) {
    return NULL;
  }
  return ht->list->src;
}

int genputtable(struct genhashtable *ht, void * key, void * object) {
  unsigned int bin=genhashfunction(ht,key);
  struct genpointerlist * newptrlist=(struct genpointerlist *) RUNMALLOC(sizeof(struct genpointerlist));
  newptrlist->src=key;
  newptrlist->object=object;
  newptrlist->next=ht->bins[bin];
  newptrlist->inext=NULL;
  /* maintain linked list of ht entries for iteration*/
  if (ht->last==NULL) {
    ht->last=newptrlist;
    ht->list=newptrlist;
    newptrlist->iprev=NULL;
  } else {
    ht->last->inext=newptrlist;
    newptrlist->iprev=ht->last;
    ht->last=newptrlist;
  }
  ht->bins[bin]=newptrlist;
  ht->counter++;
  if(ht->counter>ht->currentsize&&ht->currentsize!=INT_MAX) {
    /* Expand hashtable */
    long newcurrentsize=(ht->currentsize<(INT_MAX/2)) ? ht->currentsize*2 : INT_MAX;
    long oldcurrentsize=ht->currentsize;
    struct genpointerlist **newbins=(struct genpointerlist **) RUNMALLOC(sizeof (struct genpointerlist *)*newcurrentsize);
    struct genpointerlist **oldbins=ht->bins;
    long j,i;
    for(j=0; j<newcurrentsize; j++) newbins[j]=NULL;
    ht->currentsize=newcurrentsize;
    for(i=0; i<oldcurrentsize; i++) {
      struct genpointerlist * tmpptr=oldbins[i];
      while(tmpptr!=NULL) {
	unsigned int hashcode=genhashfunction(ht, tmpptr->src);
	struct genpointerlist *nextptr=tmpptr->next;
	tmpptr->next=newbins[hashcode];
	newbins[hashcode]=tmpptr;
	tmpptr=nextptr;
      }
    }
    ht->bins=newbins;
    RUNFREE(oldbins);
  }
  return 1;
}

#ifdef MULTICORE
int genputtable_I(struct genhashtable *ht, void * key, void * object) {
  unsigned int bin=genhashfunction(ht,key);
  struct genpointerlist * newptrlist=(struct genpointerlist *) RUNMALLOC_I(sizeof(struct genpointerlist));
  newptrlist->src=key;
  newptrlist->object=object;
  newptrlist->next=ht->bins[bin];
  newptrlist->inext=NULL;
  /* maintain linked list of ht entries for iteration*/
  if (ht->last==NULL) {
    ht->last=newptrlist;
    ht->list=newptrlist;
    newptrlist->iprev=NULL;
  } else {
    ht->last->inext=newptrlist;
    newptrlist->iprev=ht->last;
    ht->last=newptrlist;
  }
  ht->bins[bin]=newptrlist;
  ht->counter++;
  if(ht->counter>ht->currentsize&&ht->currentsize!=INT_MAX) {
    /* Expand hashtable */
    long newcurrentsize=(ht->currentsize<(INT_MAX/2)) ? ht->currentsize*2 : INT_MAX;
    long oldcurrentsize=ht->currentsize;
    struct genpointerlist **newbins=(struct genpointerlist **) RUNMALLOC_I(sizeof (struct genpointerlist *)*newcurrentsize);
    struct genpointerlist **oldbins=ht->bins;
    long j,i;
    for(j=0; j<newcurrentsize; j++) newbins[j]=NULL;
    ht->currentsize=newcurrentsize;
    for(i=0; i<oldcurrentsize; i++) {
      struct genpointerlist * tmpptr=oldbins[i];
      while(tmpptr!=NULL) {
	unsigned int hashcode=genhashfunction(ht, tmpptr->src);
	struct genpointerlist *nextptr=tmpptr->next;
	tmpptr->next=newbins[hashcode];
	newbins[hashcode]=tmpptr;
	tmpptr=nextptr;
      }
    }
    ht->bins=newbins;
    RUNFREE(oldbins);
  }
  return 1;
}
#endif

int hashsize(struct genhashtable *ht) {
  return ht->counter;
}

void genrehash(struct genhashtable * ht) {
  struct genpointerlist **newbins=(struct genpointerlist **) RUNMALLOC(sizeof (struct genpointerlist *)*ht->currentsize);
  struct genpointerlist **oldbins=ht->bins;
  long i;

  for(i=0; i<ht->currentsize; i++) {
    struct genpointerlist * tmpptr=oldbins[i];
    while(tmpptr!=NULL) {
      unsigned int hashcode=genhashfunction(ht, tmpptr->src);
      struct genpointerlist *nextptr=tmpptr->next;
      tmpptr->next=newbins[hashcode];
      newbins[hashcode]=tmpptr;
      tmpptr=nextptr;
    }
  }
  ht->bins=newbins;
  RUNFREE(oldbins);
}

void * gengettable(struct genhashtable *ht, void * key) {
  struct genpointerlist * ptr=ht->bins[genhashfunction(ht,key)];
  while(ptr!=NULL) {
    if (((ht->comp_function==NULL)&&(ptr->src==key))||((ht->comp_function!=NULL)&&(*ht->comp_function)(ptr->src,key)))
      return ptr->object;
    ptr=ptr->next;
  }
#ifndef MULTICORE
  printf("XXXXXXXXX: COULDN'T FIND ENTRY FOR KEY %p\n",key);
#endif
  return NULL;
}

void * getnext(struct genhashtable *ht, void * key) {
  struct genpointerlist * ptr=ht->bins[genhashfunction(ht,key)];
  while(ptr!=NULL) {
    if (((ht->comp_function==NULL)&&(ptr->src==key))||((ht->comp_function!=NULL)&&(*ht->comp_function)(ptr->src,key)))
      if (ptr->inext!=NULL) {
	return ptr->inext->src;
      } else
	return NULL;
    ptr=ptr->next;
  }
#ifndef MULTICORE
  printf("XXXXXXXXX: COULDN'T FIND ENTRY FOR KEY %p...\n Likely concurrent removal--bad user!!!\n",key);
#endif
  return NULL;
}

int gencontains(struct genhashtable *ht, void * key) {
  struct genpointerlist * ptr=ht->bins[genhashfunction(ht,key)];
  //printf("In gencontains2\n");fflush(NULL);
  while(ptr!=NULL) {
    if (((ht->comp_function==NULL)&&(ptr->src==key))||((ht->comp_function!=NULL)&&(*ht->comp_function)(ptr->src,key)))
      return 1;
    ptr=ptr->next;
  }
  return 0;
}


void genfreekey(struct genhashtable *ht, void * key) {
  struct genpointerlist * ptr=ht->bins[genhashfunction(ht,key)];

  if (((ht->comp_function==NULL)&&(ptr->src==key))||((ht->comp_function!=NULL)&&(*ht->comp_function)(ptr->src,key))) {
    ht->bins[genhashfunction(ht,key)]=ptr->next;

    if (ptr==ht->last)
      ht->last=ptr->iprev;

    if (ptr==ht->list)
      ht->list=ptr->inext;

    if (ptr->iprev!=NULL)
      ptr->iprev->inext=ptr->inext;
    if (ptr->inext!=NULL)
      ptr->inext->iprev=ptr->iprev;

    RUNFREE(ptr);
    ht->counter--;
    return;
  }
  while(ptr->next!=NULL) {
    if (((ht->comp_function==NULL)&&(ptr->next->src==key))||((ht->comp_function!=NULL)&&(*ht->comp_function)(ptr->next->src,key))) {
      struct genpointerlist *tmpptr=ptr->next;
      ptr->next=tmpptr->next;
      if (tmpptr==ht->list)
	ht->list=tmpptr->inext;
      if (tmpptr==ht->last)
	ht->last=tmpptr->iprev;
      if (tmpptr->iprev!=NULL)
	tmpptr->iprev->inext=tmpptr->inext;
      if (tmpptr->inext!=NULL)
	tmpptr->inext->iprev=tmpptr->iprev;
      RUNFREE(tmpptr);
      ht->counter--;
      return;
    }
    ptr=ptr->next;
  }
#ifndef MULTICORE
  printf("XXXXXXXXX: COULDN'T FIND ENTRY FOR KEY %p\n",key);
#endif
}

unsigned int genhashfunction(struct genhashtable *ht, void * key) {
  if (ht->hash_function==NULL)
    return ((long unsigned int)key) % ht->currentsize;
  else
    return ((*ht->hash_function)(key)) % ht->currentsize;
}

struct genhashtable * genallocatehashtable(unsigned int (*hash_function)(void *),int (*comp_function)(void *, void *)) {
  struct genhashtable *ght;
  struct genpointerlist **gpl;
  int i;

  gpl=(struct genpointerlist **) RUNMALLOC(sizeof(struct genpointerlist *)*geninitialnumbins);
  for(i=0; i<geninitialnumbins; i++) {
    gpl[i]=NULL;
  }
  ght=(struct genhashtable *) RUNMALLOC(sizeof(struct genhashtable));
  ght->hash_function=hash_function;
  ght->comp_function=comp_function;
  ght->currentsize=geninitialnumbins;
  ght->bins=gpl;
  ght->counter=0;
  ght->list=NULL;
  ght->last=NULL;
  return ght;
}

void genfreehashtable(struct genhashtable * ht) {
  int i;
  for (i=0; i<ht->currentsize; i++) {
    if (ht->bins[i]!=NULL) {
      struct genpointerlist *genptr=ht->bins[i];
      while(genptr!=NULL) {
	struct genpointerlist *tmpptr=genptr->next;
	RUNFREE(genptr);
	genptr=tmpptr;
      }
    }
  }
  RUNFREE(ht->bins);
  RUNFREE(ht);
}

struct geniterator * gengetiterator(struct genhashtable *ht) {
  struct geniterator *gi=(struct geniterator*)RUNMALLOC(sizeof(struct geniterator));
  gi->ptr=ht->list;
  return gi;
}

void * gennext(struct geniterator *it) {
  struct genpointerlist *curr=it->ptr;
  if (curr==NULL)
    return NULL;
  if (it->finished&&(curr->inext==NULL))
    return NULL;
  if (it->finished) {
    it->ptr=curr->inext;
    return it->ptr->src;
  }
  if(curr->inext!=NULL)
    it->ptr=curr->inext;
  else
    it->finished=1;  /* change offsetting scheme */
  return curr->src;
}

void genfreeiterator(struct geniterator *it) {
  RUNFREE(it);
}
