#include "machinepile.h"

prefetchpile_t *insertPile(int mid, unsigned int oid, short numoffset, short *offset, prefetchpile_t *head) {
	prefetchpile_t *tmp = head;
	prefetchpile_t *ptr;
	objpile_t *objnode;
	unsigned int *oidarray;
	short *offvalues;
	int i;
	char found = 0;

	while (tmp != NULL) {
		if (tmp->mid == mid) { // Found a match with exsisting machine id
			if ((objnode = (objpile_t *) calloc(1, sizeof(objpile_t))) == NULL) {
				printf("Calloc error: %s %d\n", __FILE__, __LINE__);
				return NULL;
			}
			if ((offvalues = (short *) calloc(numoffset, sizeof(short))) == NULL) {
				printf("Calloc error: %s %d\n", __FILE__, __LINE__);
				return NULL;
			}
			/* Fill objpiles DS */
			objnode->oid = oid;
			objnode->numoffset = numoffset;
			for(i = 0; i<numoffset; i++)
				offvalues[i] = offset[i];
			objnode->offset = offvalues;
			objnode->next = tmp->objpiles;
			tmp->objpiles = objnode;
			found = 1;
			break;
		}
		tmp = tmp->next;
	}

	tmp = head;
	if(found != 1) {
		 if(tmp->mid == 0) {//First time
			tmp->mid = mid;
			if ((objnode = (objpile_t *) calloc(1, sizeof(objpile_t))) == NULL) {
				printf("Calloc error: %s %d\n", __FILE__, __LINE__);
				return NULL;
			}
			if ((offvalues = (short *) calloc(numoffset, sizeof(short))) == NULL) {
				printf("Calloc error: %s %d\n", __FILE__, __LINE__);
				return NULL;
			}
			// Fill objpiles DS
			objnode->oid = oid;
			objnode->numoffset = numoffset;
			for(i = 0; i<numoffset; i++)
				offvalues[i] = *((short *)offset + i); 
			objnode->offset = offvalues;
			objnode->next = NULL;
			tmp->objpiles = objnode;
			tmp->next = NULL;
		} else {
			if ((tmp = (prefetchpile_t *) calloc(1, sizeof(prefetchpile_t))) == NULL) {
				printf("Calloc error: %s %d\n", __FILE__, __LINE__);
				return NULL;
			}
			tmp->mid = mid;
			if ((objnode = (objpile_t *) calloc(1, sizeof(objpile_t))) == NULL) {
				printf("Calloc error: %s %d\n", __FILE__, __LINE__);
				return NULL;
			}
			if ((offvalues = (short *) calloc(numoffset, sizeof(short))) == NULL) {
				printf("Calloc error: %s %d\n", __FILE__, __LINE__);
				return NULL;
			}
			// Fill objpiles DS
			objnode->oid = oid;
			objnode->numoffset = numoffset;
			for(i = 0; i<numoffset; i++)
				offvalues[i] = *((short *)offset + i); 
			objnode->offset = offvalues;
			objnode->next = NULL;
			tmp->objpiles = objnode;
			tmp->next = head;
			head = tmp;
		}
	}
	
	return head;
}
