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
	if(ptr[index].next == NULL) {
		ptr[index].oid = oid;
		ptr[index].mid = mid;
	} else {
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

int lhashSearch(lhashtable_t table, unsigned int oid) {
	int index;
	lhashlistnode_t *ptr;
	lhashlistnode_t *tmp;

	ptr = table.table;	
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

/*
int lhashRemove(lhashtable_t table, unsigned int oid) {
	int index;
	lhashlistnode_t *ptr;
	lhashlistnode_t *tmp;
	
	index = lhashFunction(table, oid);
	ptr = table.table;
	tmp = ptr[index].next;
	if(ptr[index].oid == oid) {
		ptr[index] = tmp ;
		table.numelements--;
	}
}
*/

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
			if( &ptr[index] == curr) {
				ptr[index].next = NULL; // TO DO: Debug
			}
			free(curr);
			return 0;
		}       
		prev = curr; 
	}
	return -1;
}

void lhashResize(lhashtable_t table, unsigned int newsize) {
	int i, index;
	lhashtable_t oldtable;
	lhashlistnode_t *ptr;
	lhashlistnode_t *node;
	lhashlistnode_t *new;
	lhashlistnode_t *tmp;
	
	oldtable = table;
	ptr = oldtable.table;
	//Allocate new space	
	if((node = calloc(newsize, sizeof(lhashlistnode_t))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		exit(-1);
	}
	
	table.table = node;
	for(i=0; i< oldtable.size ; i++) {
		new = &ptr[i];	
		while ( new != NULL) {
			tmp = new->next;	
			index = lhashFunction(table, new->oid);
			new->next = &table.table[index];	
			table.table[index].next = new; // TO DO : Debug
			new = new->next;
		}
	}
}


