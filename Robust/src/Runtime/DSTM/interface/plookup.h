#ifndef _PLOOKUP_H_
#define _PLOOKUP_H_

#include <stdlib.h>
#include <stdio.h>

typedef struct plistnode {
	unsigned int mid;
	unsigned int *obj; //this can be cast to another type or used to point to a larger structure
	int index;
	int vote;
	struct plistnode *next;
} plistnode_t;

plistnode_t  *pCreate(int);
plistnode_t  *pInsert(plistnode_t*, unsigned int mid, unsigned int oid, int);
int pCount(plistnode_t *pile);
int pListMid(plistnode_t *pile, unsigned int *list);
unsigned int *pSearch(plistnode_t *, unsigned int mid);
void pDelete(plistnode_t *);

#endif

