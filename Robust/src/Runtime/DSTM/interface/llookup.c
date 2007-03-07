#include "llookup.h"

// Creates a hash table with default size HASH_SIZE and an array of lhashlistnode_t 
lhashtable_t lhashCreate(unsigned int size, float loadfactor) {
	lhashtable_t newtable;
	lhashlistnode_t *nodes;

	if((nodes = calloc(HASH_SIZE, sizeof(lhashlistnode_t))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		exit(-1);
	}
	
	newtable.table = nodes;
	newtable.size = size;
	newtable.numelements = 0; // Initial list of elements in the hash
	newtable.loadfactor = loadfactor;

	return newtable;
}

// Assign to oids to bins inside hash table
unsigned int lhashFunction(lhashtable_t table, unsigned int oid) {
	return( oid % (table.size));
}

// Insert oid and mid mapping into the hash table
void lhashInsert(lhashtable_t table, unsigned int oid, unsigned int mid) {
	unsigned int newsize;
	int index;
	lhashlistnode_t *ptr;
	lhashlistnode_t *node;
	
	ptr = table.table;
	table.numelements++;
	if (table.numelements > (table.loadfactor * table.size)) {
		//Resize Table
		newsize = 2 * table.size + 1;		
		lhashResize(table, newsize);
	}
	
	index = lhashFunction(table, oid);
	if(ptr[index].next == NULL) {	// Insert at the first position
		ptr[index].oid = oid;
		ptr[index].mid = mid;
	} else {			// Insert in the linked list
		if ((node = calloc(1, sizeof(lhashlistnode_t))) == NULL) {
			printf("Calloc error %s, %d\n", __FILE__, __LINE__);
			exit(-1);
		}
		node->oid = oid;
		node->mid = mid;
		node->next = ptr[index].next;
		ptr[index].next = node;
	}
}

// Search for a value in the hash table
int lhashSearch(lhashtable_t table, unsigned int oid) {
	int index;
	lhashlistnode_t *ptr;
	lhashlistnode_t *tmp;

	ptr = table.table;	// Address of the beginning of hash table	
	index = lhashFunction(table, oid);
	tmp = ptr[index].next;
	while(tmp != NULL) {
		if(tmp->oid == oid) {
			return tmp->mid;
		}
		tmp = tmp->next;
	}
	return -1;
}

// Remove an entry from the hash table
int lhashRemove(lhashtable_t table, unsigned int oid) {
	int index;
	lhashlistnode_t *curr, *prev;
	lhashlistnode_t *ptr;
	
	ptr = table.table;
	index = lhashFunction(table, oid);
	prev = curr = &ptr[index];

	for (; curr != NULL; curr = curr->next) {
		if (curr->oid == oid) {         // Find a match in the hash table
			table.numelements--;
			prev->next = curr->next;
			if (curr == &ptr[index]) {
				ptr[index].oid = 0;
				ptr[index].mid = 0;
		// TO DO: Delete the first element in the hash table	
			}
			free(curr);
			return 0;
		}       
		prev = curr; 
	}
	return -1;
}

// Resize table
void lhashResize(lhashtable_t table, unsigned int newsize) {
	int i;
	lhashtable_t oldtable;
	lhashlistnode_t *ptr;
	lhashlistnode_t *node;
	lhashlistnode_t *new;
	
	oldtable.table = table.table;
	oldtable.size = table.size;
	oldtable.numelements = table.numelements;
	oldtable.loadfactor = table.loadfactor;
	ptr = oldtable.table;

	//Allocate new space	
	if((node = calloc(newsize, sizeof(lhashlistnode_t))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		exit(-1);
	}
	
	table.table = node;
	table.numelements = 0;
	for(i=0; i< oldtable.size ; i++) { 	//For each entry in the old hashtable insert
	       					//the element into the new hash table 
		new = &ptr[i];	
		while ( new != NULL) {
			lhashInsert(table, new->oid, new->mid);
			new = new->next;
		}
	}
	free(oldtable.table);			// Free the oldhash table
	table.size = newsize;
}


