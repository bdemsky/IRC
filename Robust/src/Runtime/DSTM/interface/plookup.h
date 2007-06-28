#ifndef _PLOOKUP_H_
#define _PLOOKUP_H_

#include <stdlib.h>
#include <stdio.h>
#include "dstm.h"

typedef struct plistnode {
	unsigned int mid;
	int local; 		/*Variable that keeps track if this pile is for LOCAL machine */
	unsigned int *oidmod;
	unsigned int *oidread;
	int nummod;
	int numread;
	int sum_bytes;
	char *objread;
	char *objmodified;
	int vote;
	struct plistnode *next;
} plistnode_t;

plistnode_t  *pCreate(int);
plistnode_t *pInsert(plistnode_t *pile, objheader_t *headeraddr, unsigned int mid, int num_objs);
int pCount(plistnode_t *pile);
int pListMid(plistnode_t *pile, unsigned int *list);
void pDelete(plistnode_t *pile);

#endif

