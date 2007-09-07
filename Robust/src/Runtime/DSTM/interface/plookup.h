#ifndef _PLOOKUP_H_
#define _PLOOKUP_H_

#include <stdlib.h>
#include <stdio.h>
#include "dstm.h"

/* This structure is created using a transaction record.
 * It is filled out with pile information necessary for 
 * participants involved in a transaction. */
typedef struct plistnode {
	unsigned int mid;
	short numread;		/* no of objects modified */
	short nummod;  		/* no of objects read */
	short numcreated; /* no of objects created */
	int sum_bytes;		/* total bytes of objects modified */
	char *objread;		/* Pointer to array containing oids of objects read and their version numbers*/
	unsigned int *oidmod;	/* Pointer to array containing oids of modified objects */ 
	unsigned int *oidcreated;	/* Pointer to array containing oids of newly created objects */ 
	struct plistnode *next;
} plistnode_t;

plistnode_t  *pCreate(int);
plistnode_t *pInsert(plistnode_t *pile, objheader_t *headeraddr, unsigned int mid, int num_objs);
int pCount(plistnode_t *pile);
int pListMid(plistnode_t *pile, unsigned int *list);
void pDelete(plistnode_t *pile);

#endif

