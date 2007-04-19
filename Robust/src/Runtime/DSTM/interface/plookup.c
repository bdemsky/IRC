#include "plookup.h"
extern int classsize[];

plistnode_t *pCreate(int objects) {
	plistnode_t *pile;
      	
	//Create main structure
	if((pile = calloc(1, sizeof(plistnode_t))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		return NULL;
	}	
	pile->next = NULL;
	if ((pile->oidmod = calloc(objects, sizeof(unsigned int))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		return NULL;
	}
	if ((pile->oidread = calloc(objects, sizeof(unsigned int))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		return NULL;
	}
	pile->nummod = pile->numread = pile->sum_bytes = 0;
	if ((pile->objread = calloc(objects, sizeof(int) + sizeof(short))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		return NULL;
	}
	pile->objmodified = NULL;
	pile->nummod = pile->numread = pile->sum_bytes = 0;

	return pile;
}

plistnode_t *pInsert(plistnode_t *pile, objheader_t *headeraddr, unsigned int mid, int num_objs) {
	plistnode_t *ptr, *tmp;
	int found = 0, offset;

	tmp = pile;
	//Add oid into a machine that is a part of the pile linked list structure
	while(tmp != NULL) {
		if (tmp->mid == mid) {
			if ((headeraddr->status & DIRTY) == 1) {
				tmp->oidmod[tmp->nummod] = headeraddr->oid;
				tmp->nummod = tmp->nummod + 1;
				tmp->sum_bytes += sizeof(objheader_t) + classsize[headeraddr->type];
			} else {
				tmp->oidread[tmp->numread] = headeraddr->oid;
				offset = (sizeof(unsigned int) + sizeof(short)) * tmp->numread;
				memcpy(tmp->objread + offset, &headeraddr->oid, sizeof(unsigned int));
				offset += sizeof(unsigned int);
				memcpy(tmp->objread + offset, &headeraddr->version, sizeof(short));
				tmp->numread = tmp->numread + 1;
			//	printf("DEBUG->pInsert() No of obj read = %d\n", tmp->numread);
			}
			found = 1;
			break;
		}
		tmp = tmp->next;
	}
	//Add oid for any new machine 
	if (!found) {
		if((ptr = pCreate(num_objs)) == NULL) {
			return NULL;
		}
		ptr->mid = mid;
		if ((headeraddr->status & DIRTY) == 1) {
			ptr->oidmod[ptr->nummod] = headeraddr->oid;
			ptr->nummod = ptr->nummod + 1;
			ptr->sum_bytes += sizeof(objheader_t) + classsize[headeraddr->type];
		} else {
			ptr->oidread[ptr->numread] = headeraddr->oid;
			memcpy(ptr->objread, &headeraddr->oid, sizeof(unsigned int));
			memcpy(ptr->objread + sizeof(unsigned int), &headeraddr->version, sizeof(short));
			ptr->numread = ptr->numread + 1;
		}
		ptr->next = pile;
		pile = ptr;
	}

	return pile;
}

//Count the number of machine groups
int pCount(plistnode_t *pile) {
	plistnode_t *tmp;
	int pcount = 0;
	tmp = pile;
	while(tmp != NULL) {
		pcount++;
		tmp = tmp->next;
	}
	return pcount;
}

//Make a list of mid's for each machine group
int pListMid(plistnode_t *pile, unsigned int *list) {
        int i = 0;
	plistnode_t *tmp;
	tmp = pile;
	while (tmp != NULL) {
		list[i] = tmp->mid;
		i++;
		tmp = tmp->next;
	}
	return 0;
}

//Delete the entire pile
void pDelete(plistnode_t *pile) {
	plistnode_t *next, *tmp;
	tmp = pile;
	while(tmp != NULL) {
		next = tmp->next;
		free(tmp->oidmod);
		free(tmp->oidread);
		free(tmp->objread);
		free(tmp);
		tmp = next;
	}
	return;
}
