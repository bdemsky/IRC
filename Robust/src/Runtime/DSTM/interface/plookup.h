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
unsigned int pInsert(plistnode_t*, unsigned int mid, unsigned int oid, int);
unsigned int *pSearch(plistnode_t *, unsigned int mid);
void pDelete(plistnode_t *);

#endif

