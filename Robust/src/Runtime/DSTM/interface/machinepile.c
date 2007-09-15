#include "machinepile.h"

int insertPile(int mid, unsigned int oid, short numoffset, short *offset, prefetchpile_t **head) {
	prefetchpile_t *tmp = *head;
	objpile_t *objnode;
	unsigned int *oidarray;
	int ntuples;
	char found = 0;

	while (tmp != NULL) {
		if (tmp->mid == mid) { // Found a match with exsisting machine id
			if ((objnode = (objpile_t *) calloc(1, sizeof(objpile_t))) == NULL) {
				printf("Calloc error: %s %d\n", __FILE__, __LINE__);
				return -1;
			}
			/* Fill objpiles DS */
			objnode->oid = oid;
			objnode->numoffset = numoffset;
			objnode->offset = offset;
			objnode->next = tmp->objpiles;
			tmp->objpiles = objnode;
			found = 1;
			break;
		}
		tmp = tmp->next;
	}
	if (!found) {// Not found => insert new mid DS
		if ((tmp = (prefetchpile_t *) calloc(1, sizeof(prefetchpile_t))) == NULL) {
			printf("Calloc error: %s %d\n", __FILE__, __LINE__);
			return -1;
		}
		tmp->mid = mid;
		if ((objnode = (objpile_t *) calloc(1, sizeof(objpile_t))) == NULL) {
			printf("Calloc error: %s %d\n", __FILE__, __LINE__);
			return -1;
		}
		/* Fill objpiles DS */
		objnode->oid = oid;
		objnode->numoffset = numoffset;
		objnode->offset = offset;
		objnode->next = tmp->objpiles; // i.e., objnode->next = NULL;
		tmp->objpiles = objnode;
		tmp->next = *head;
		*head = tmp;
	}
	return 0;
}


