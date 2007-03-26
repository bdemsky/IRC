 #include "plookup.h"

plistnode_t *pCreate(int objects) {
	plistnode_t *pile;
      	
	//Create main structure
	if((pile = calloc(1, sizeof(plistnode_t))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		return NULL;
	}	
	pile->next = NULL;
	//Create array of objects
	if((pile->obj = calloc(objects, sizeof(unsigned int))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		return NULL;
	}
	pile->index = 0;
	//pile->vote = 0;
	return pile;
}

plistnode_t *pInsert(plistnode_t *pile, unsigned int mid, unsigned int oid, int num_objs) {
	plistnode_t *ptr, *tmp;
	int found = 0;

	tmp = pile;
	//Add oid into a machine that is a part of the pile linked list structure
	while(tmp != NULL) {
		if (tmp->mid == mid) {
			tmp->obj[tmp->index] = oid;
			tmp->index++;
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
		ptr->obj[ptr->index] = oid;
		ptr->index++;
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
	}
	return 0;
}

// Return objects for a given mid
unsigned int *pSearch(plistnode_t *pile, unsigned int mid) {
	plistnode_t *tmp;
	tmp = pile;
	while(tmp != NULL) {
		if(tmp->mid == mid) {
			return(tmp->obj);
		}
		tmp = tmp->next;
	}
	return NULL;
}

//Delete the entire pile
void pDelete(plistnode_t *pile) {
	plistnode_t *next, *tmp;
	tmp = pile;
	while(tmp != NULL) {
		next = tmp->next;
		free(tmp->obj);
		free(tmp);
		tmp = next;
	}
	return;
}
