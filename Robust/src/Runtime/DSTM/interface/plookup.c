#include "plookup.h"
extern int classsize[];

//NOTE: "pile" ptr points to the head of the linked list of the machine pile data structures 

/* This function creates a new pile data structure to hold
 * obj ids of objects modified or read inside a transaction,
 * no of objects read and no of objects modified
 * that belong to a single machine */

plistnode_t *pCreate(int objects) {
	plistnode_t *pile;
      	
	//Create main structure
	if((pile = calloc(1, sizeof(plistnode_t))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		return NULL;
	}	
	if ((pile->oidmod = calloc(objects, sizeof(unsigned int))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		free(pile);
		return NULL;
	}
	if ((pile->oidcreated = calloc(objects, sizeof(unsigned int))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		free(pile);
		free(pile->oidmod);
		return NULL;
	}
	if ((pile->objread = calloc(objects, sizeof(unsigned int) + sizeof(short))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		free(pile);
		free(pile->oidmod);
		free(pile->oidcreated);
		return NULL;
	}

	pile->nummod = pile->numread = pile->numcreated = pile->sum_bytes = pile->mid = 0;
	pile->next = NULL;
	return pile;
}

/* This function inserts necessary information into 
 * a machine pile data structure */
plistnode_t *pInsert(plistnode_t *pile, objheader_t *headeraddr, unsigned int mid, int num_objs) {
	plistnode_t *ptr, *tmp;
	int found = 0, offset = 0;

	tmp = pile;
	//Add oid into a machine that is already present in the pile linked list structure
	while(tmp != NULL) {
		if (tmp->mid == mid) {
		  int tmpsize;
		  
		  if (STATUS(headeraddr) & NEW) {
		    tmp->oidcreated[tmp->numcreated] = OID(headeraddr);
		    tmp->numcreated = tmp->numcreated + 1;
		    GETSIZE(tmpsize, headeraddr);
		    tmp->sum_bytes += sizeof(objheader_t) + tmpsize;
		  }else if (STATUS(headeraddr) & DIRTY) {
		    tmp->oidmod[tmp->nummod] = OID(headeraddr);
		    tmp->nummod = tmp->nummod + 1;
		    GETSIZE(tmpsize, headeraddr);
		    tmp->sum_bytes += sizeof(objheader_t) + tmpsize;
		  } else {
		    offset = (sizeof(unsigned int) + sizeof(short)) * tmp->numread;
		    *((unsigned int *)(((char *)tmp->objread) + offset))=OID(headeraddr);
		    offset += sizeof(unsigned int);
		    *((short *)(((char *)tmp->objread) + offset)) = headeraddr->version;
		    tmp->numread = tmp->numread + 1;
		  }
		  found = 1;
		  break;
		}
		tmp = tmp->next;
	}
	//Add oid for any new machine 
	if (!found) {
	  int tmpsize;
	  if((ptr = pCreate(num_objs)) == NULL) {
	    return NULL;
	  }
	  ptr->mid = mid;
	  if (STATUS(headeraddr) & NEW) {
	    ptr->oidcreated[ptr->numcreated] = OID(headeraddr);
	    ptr->numcreated = ptr->numcreated + 1;
	    GETSIZE(tmpsize, headeraddr);
	    ptr->sum_bytes += sizeof(objheader_t) + tmpsize;
	  } else if (STATUS(headeraddr) & DIRTY) {
	    ptr->oidmod[ptr->nummod] = OID(headeraddr);
	    ptr->nummod = ptr->nummod + 1;
	    GETSIZE(tmpsize, headeraddr);
	    ptr->sum_bytes += sizeof(objheader_t) + tmpsize;
	  } else {
	    *((unsigned int *)ptr->objread)=OID(headeraddr);
	    offset = sizeof(unsigned int);
	    *((short *)(((char *)ptr->objread) + offset)) = headeraddr->version;
	    ptr->numread = ptr->numread + 1;
	  }
	  ptr->next = pile;
	  pile = ptr;
	}

	/* Clear Flags */
	STATUS(headeraddr) &= ~NEW;
	STATUS(headeraddr) &= ~DIRTY;

	return pile;
}

//Count the number of machine piles
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
		free(tmp->oidcreated);
		free(tmp->objread);
		free(tmp);
		tmp = next;
	}
	return;
}
