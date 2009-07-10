#include "machinepile.h"

void insertPile(int mid, unsigned int oid, short numoffset, short *offset, prefetchpile_t **head) {
  prefetchpile_t *ptr;
  objpile_t *objnode;
  unsigned int *oidarray;
  objpile_t **tmp;

  //Loop through the machines
  for(; 1; head=&((*head)->next)) {
    int tmid;
    if ((*head)==NULL||(tmid=(*head)->mid)>mid) {
      prefetchpile_t * tmp = (prefetchpile_t *) malloc(sizeof(prefetchpile_t));
      tmp->mid = mid;
      objnode =  malloc(sizeof(objpile_t));
      objnode->offset = offset;
      objnode->oid = oid;
      objnode->numoffset = numoffset;
      objnode->next = NULL;
      tmp->objpiles = objnode;
      tmp->next = *head;
      *head=tmp;
      return;
    }

    //keep looking
    if (tmid < mid)
      continue;

    //found mid list
    for(tmp=&((*head)->objpiles); 1; tmp=&((*tmp)->next)) {
      int toid;
      int matchstatus;

      if ((*tmp)==NULL||((toid=(*tmp)->oid)>oid)) {
	objnode = (objpile_t *) malloc(sizeof(objpile_t));
	objnode->offset = offset;
	objnode->oid = oid;
	objnode->numoffset = numoffset;
	objnode->next = *tmp;
	*tmp = objnode;
	return;
      }
      if (toid < oid)
	continue;

      /* Fill objpiles DS */
      int i;
      int onumoffset=(*tmp)->numoffset;
      short * ooffset=(*tmp)->offset;

      for(i=0; i<numoffset; i++) {
	if (i>onumoffset) {
	  //We've matched, let's just extend the current prefetch
	  (*tmp)->numoffset=numoffset;
	  (*tmp)->offset=offset;
	  return;
	}
	if (ooffset[i]<offset[i]) {
	  goto oidloop;
	} else if (ooffset[i]>offset[i]) {
	  //Place item before the current one
	  objnode = (objpile_t *) malloc(sizeof(objpile_t));
	  objnode->offset = offset;
	  objnode->oid = oid;
	  objnode->numoffset = numoffset;
	  objnode->next = *tmp;
	  *tmp = objnode;
	  return;
	}
      }
      //if we get to the end, we're already covered by this prefetch
      return;
oidloop:
      ;
    }
  }


}
