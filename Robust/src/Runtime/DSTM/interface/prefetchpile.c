#include "dstm.h"

/* Make a queue of prefetchpile_t type */
prefetchpile_t poolqueue; //Global queue for machine piles

/* Create new machine group */
prefetchpile_t *createPile(int numoffsets) {
	prefetchpile_t *pile;
	if((pile = calloc(1, sizeof(prefetchpile_t))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		return NULL;
	}

	/* Create a new object pile */
	if((pile->objpiles = calloc(1, sizeof(objpile_t))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		return NULL;
	}

	/* Create a ptr to the offset array for a given prefetch oid tuple */
	if((pile->objpiles->offset = calloc(numoffsets, sizeof(short))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		return NULL;
	}
	
	pile->next = NULL;

	return pile;
}

/* Into into prefetch pile*/
void pileIns(prefetchpile_t *pile, short *endoffsets, short* arryfields, unsigned int *oid,int mnum,int noffsets, int index) {
	prefetchpile_t *tmp, *ptr;
	objpile_t *opile;
	short *offsetarry;
	int found = 0, k;

	tmp = pile;
	while(tmp != NULL) {
		//Check if mnum already exists in the pile
		if(tmp->mid == mnum) {
			/* Create a new object pile */
			if((opile = calloc(1, sizeof(objpile_t))) == NULL) {
				printf("Calloc error %s %d\n", __FILE__, __LINE__);
				return NULL;
			}
			opile->next = tmp->objpiles;
			tmp->objpiles = opile;

			tmp->objpiles->oid = oid[index];
			if(index == 0)
				k = 0;
			else
				k = endoffsets[index -1];
			//Copy the offset values into objpile
			for(i = 0; i < numoffsets[i]; i++) {
				ptr->objpile->offsets[i] = arryfields[k];
				k++;
			}
			/* Create a ptr to the offset array for a given prefetch oid tuple */
			if((offsetarry = calloc(numoffsets, sizeof(short))) == NULL) {
				printf("Calloc error %s %d\n", __FILE__, __LINE__);
				return NULL;
			}


			found = 1;
			break;
		}
		tmp = tmp->next;
	}

	//Add New machine pile to the linked list
	if(!found) {
		if((ptr = createPile(noffsets)) == NULL) {
			printf("No new pile created %s %d\n", __FILE__, __LINE__);
			return;
		}
		ptr->mid = mnum;
		ptr->objpile->oid = oid[index];
		if(index == 0)
			k = 0;
		else
			k = endoffsets[index -1];
		//Copy the offset values into objpile
		for(i = 0; i < numoffsets[i]; i++) {
			ptr->objpile->offsets[i] = arryfields[k];
			k++;
		}
		ptr->next = pile;
		pile = ptr;
	}

	return pile;
}

/* Insert into object pile */


