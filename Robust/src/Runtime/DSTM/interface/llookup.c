/************************************************************************************************
  IMP NOTE:
  
   All llookup hash function prototypes returns 0 or NULL when there is an error or else returns 1
   llookup hash is an array of lhashlistnode_t
   oid = mid = 0 in a given lhashlistnode_t for each bin in the hash table ONLY if the entry is empty =>
   the OID's can be any unsigned int except 0
***************************************************************************************************/
#include "llookup.h"

extern  lhashtable_t llookup;		//Global Hash table

// Creates a hash table with default size HASH_SIZE and an array of lhashlistnode_t 
unsigned int lhashCreate(unsigned int size, float loadfactor) {
	lhashlistnode_t *nodes;
	int i;

	// Allocate space for the hash table 
	if((nodes = calloc(HASH_SIZE, sizeof(lhashlistnode_t))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		return 0;
	}
	for (i = 0; i < HASH_SIZE; i++) 
		nodes[i].next = NULL;
	
	llookup.table = nodes;
	llookup.size = size;
	llookup.numelements = 0; // Initial number of elements in the hash
	llookup.loadfactor = loadfactor;
	return 1;
}

// Assign to oids to bins inside hash table
unsigned int lhashFunction(unsigned int oid) {
	return( oid % (llookup.size));
}

// Insert oid and mid mapping into the hash table
unsigned int lhashInsert(unsigned int oid, unsigned int mid) {
	unsigned int newsize;
	int index;
	lhashlistnode_t *ptr;
	lhashlistnode_t *node;
	
	if (llookup.numelements > (llookup.loadfactor * llookup.size)) {
		//Resize Table
		newsize = 2 * llookup.size + 1;		
		lhashResize(newsize);
	}
	ptr = llookup.table;
	llookup.numelements++;
	
	index = lhashFunction(oid);
	if(ptr[index].next == NULL && ptr[index].oid == 0) {	// Insert at the first position in the hashtable
		ptr[index].oid = oid;
		ptr[index].mid = mid;
	} else {			// Insert in the linked list
		if ((node = calloc(1, sizeof(lhashlistnode_t))) == NULL) {
			printf("Calloc error %s, %d\n", __FILE__, __LINE__);
			return 0;
		}
		node->oid = oid;
		node->mid = mid;
		node->next = ptr[index].next;
		ptr[index].next = node;
	}
	return 1;
}

// Search for a value in the hash table
unsigned int lhashSearch(unsigned int oid) {
	int index;
	lhashlistnode_t *ptr;
	lhashlistnode_t *tmp;

	ptr = llookup.table;	// Address of the beginning of hash table	
	index = lhashFunction(oid);
	tmp = &ptr[index];
	while(tmp != NULL) {
		if(tmp->oid == oid) {
			return tmp->mid;
		}
		tmp = tmp->next;
	}
	return 0;
}

// Remove an entry from the hash table
unsigned int lhashRemove(unsigned int oid) {
	int index;
	lhashlistnode_t *curr, *prev, *tmp;
	lhashlistnode_t *ptr;
	
	ptr = llookup.table;
	index = lhashFunction(oid);
	prev = curr = &ptr[index];

	for (; curr != NULL; curr = curr->next) {
		if (curr->oid == oid) {         // Find a match in the hash table
			llookup.numelements--;  // Decrement the number of elements in the global hashtable
			if ((curr == &ptr[index]) && (curr->next == NULL))  { // Delete the first item inside the hashtable with no linked list of lhashlistnode_t 
				curr->oid = 0;
				curr->mid = 0;
			} else if ((curr == &ptr[index]) && (curr->next != NULL)) { //Delete the first item with a linked list of lhashlistnode_t  connected 
				curr->oid = curr->next->oid;
				curr->mid = curr->next->mid;
				tmp = curr->next;
				curr->next = curr->next->next;
				free(tmp);
			} else {						// Regular delete from linked listed 	
				prev->next = curr->next;
				free(curr);
			}
			return 1;
		}       
		prev = curr; 
	}
	return 0;
}

// Resize table
unsigned int lhashResize(unsigned int newsize) {
	lhashlistnode_t *node, *curr, *next;	// curr and next keep track of the current and the next lhashlistnodes in a linked list
	lhashtable_t oldtable;
	int i, isfirst;    // Keeps track of the first element in the lhashlistnode_t for each bin in hashtable

	oldtable.table = llookup.table; // copy  orginial lhashtable_t type to a new structure
	oldtable.size = llookup.size;
	oldtable.numelements = llookup.numelements;
	oldtable.loadfactor = llookup.loadfactor;
	if((node = calloc(newsize, sizeof(lhashlistnode_t))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		return 0;
	}
	llookup.table = node; 		//Update the global hashtable upon resize()
	llookup.size = newsize;
	llookup.numelements = 0;

	for(i = 0; i < oldtable.size; i++) {
		curr = next = &oldtable.table[i];
		isfirst = 1;				//isfirst = true by default
		while (curr != NULL) {
			if (curr->oid == 0) {		//Exit inner loop if there the first element for a given bin/index is NULL
				break;			//oid = mid =0 for element if not present within the hash table
			}
			next = curr->next;
			lhashInsert(curr->oid, curr->mid);	//Call hashInsert into the new resized table 
			if (isfirst != 1) {
				free(curr);		//free the linked list of lhashlistnode_t if not the first element in the hash table
			} 
			isfirst = 0;			//set isFirst = false inside inner loop
			curr = next;
		}
	}
	free(oldtable.table);				//free the copied hashtable calloc-ed
	return 1;
}
