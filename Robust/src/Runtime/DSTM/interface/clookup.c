 #include "clookup.h"

cachehashtable_t *cachehashCreate(unsigned int size, float loadfactor) {
	cachehashtable_t *ctable;
	cachehashlistnode_t *nodes; 
        int i; 
      	
	if((ctable = calloc(1, sizeof(cachehashtable_t))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		return NULL;
	}	

        // Allocate space for the hash table 
	if((nodes = calloc(HASH_SIZE, sizeof(cachehashlistnode_t))) == NULL) { 
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		return NULL;
	}       

        ctable->table = nodes;
        ctable->size = size; 
        ctable->numelements = 0; // Initial number of elements in the hash
        ctable->loadfactor = loadfactor;
        
	return ctable;
}

//Finds the right bin in the hash table
unsigned int cachehashFunction(cachehashtable_t *table, unsigned int key) {
	return ( key % (table->size));
}

//Store objects and their pointers into hash
unsigned int cachehashInsert(cachehashtable_t *table, unsigned int key, void *val) {
	unsigned int newsize;
	int index;
	cachehashlistnode_t *ptr, *node;

	if(table->numelements > (table->loadfactor * table->size)) {
		//Resize
		newsize = 2 * table->size + 1;
		cachehashResize(table,newsize);
	}

	ptr = table->table;
	table->numelements++;
	index = cachehashFunction(table, key);
#ifdef DEBUG
	printf("DEBUG -> index = %d, key = %d, val = %x\n", index, key, val);
#endif
	if(ptr[index].next == NULL && ptr[index].key == 0) {	// Insert at the first position in the hashtable
		ptr[index].key = key;
		ptr[index].val = val;
	} else {			// Insert in the beginning of linked list
		if ((node = calloc(1, sizeof(cachehashlistnode_t))) == NULL) {
			printf("Calloc error %s, %d\n", __FILE__, __LINE__);
			return 1;
		}
		node->key = key;
		node->val = val ;
		node->next = ptr[index].next;
		ptr[index].next = node;
	}
	return 0;
}

// Search for an address for a given oid
void *cachehashSearch(cachehashtable_t *table, unsigned int key) {
	int index;
	cachehashlistnode_t *ptr, *node;

	ptr = table->table;
	index = cachehashFunction(table, key);
	node = &ptr[index];
	while(node != NULL) {
		if(node->key == key) {
			return node->val;
		}
		node = node->next;
	}
	return NULL;
}

unsigned int cachehashRemove(cachehashtable_t *table, unsigned int key) {
	int index;
	cachehashlistnode_t *curr, *prev;
	cachehashlistnode_t *ptr, *node;
	
	ptr = table->table;
	index = cachehashFunction(table,key);
	curr = &ptr[index];

	for (; curr != NULL; curr = curr->next) {
		if (curr->key == key) {         // Find a match in the hash table
			table->numelements--;  // Decrement the number of elements in the global hashtable
			if ((curr == &ptr[index]) && (curr->next == NULL))  { // Delete the first item inside the hashtable with no linked list of cachehashlistnode_t 
				curr->key = 0;
				curr->val = NULL;
			} else if ((curr == &ptr[index]) && (curr->next != NULL)) { //Delete the first item with a linked list of cachehashlistnode_t  connected 
				curr->key = curr->next->key;
				curr->val = curr->next->val;
				node = curr->next;
				curr->next = curr->next->next;
				free(node);
			} else {						// Regular delete from linked listed 	
				prev->next = curr->next;
				free(curr);
			}
			return 0;
		}       
		prev = curr; 
	}
	return 1;
}

unsigned int cachehashResize(cachehashtable_t *table, unsigned int newsize) {
	cachehashlistnode_t *node, *ptr, *curr, *next;	// curr and next keep track of the current and the next cachehashlistnodes in a linked list
	unsigned int oldsize;
	int isfirst;    // Keeps track of the first element in the cachehashlistnode_t for each bin in hashtable
	int i,index;   	
	cachehashlistnode_t *newnode; 		
	
	ptr = table->table;
	oldsize = table->size;
	
	if((node = calloc(newsize, sizeof(cachehashlistnode_t))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		return 1;
	}

	table->table = node; 		//Update the global hashtable upon resize()
	table->size = newsize;
	table->numelements = 0;

	for(i = 0; i < oldsize; i++) {			//Outer loop for each bin in hash table
		curr = &ptr[i];
		isfirst = 1;			
		while (curr != NULL) {			//Inner loop to go through linked lists
			if (curr->key == 0) {		//Exit inner loop if there the first element for a given bin/index is NULL
				break;			//key = val =0 for element if not present within the hash table
			}
			next = curr->next;

			index = cachehashFunction(table, curr->key);
#ifdef DEBUG
			printf("DEBUG(resize) -> index = %d, key = %d, val = %x\n", index, curr->key, curr->val);
#endif
			// Insert into the new table
			if(table->table[index].next == NULL && table->table[index].key == 0) { 
				table->table[index].key = curr->key;
				table->table[index].val = curr->val;
				table->numelements++;
			}else { 
				if((newnode = calloc(1, sizeof(cachehashlistnode_t))) == NULL) { 
					printf("Calloc error %s, %d\n", __FILE__, __LINE__);
					return 1;
				}       
				newnode->key = curr->key;
				newnode->val = curr->val;
				newnode->next = table->table[index].next;
				table->table[index].next = newnode;    
				table->numelements++;
			}       

			//free the linked list of cachehashlistnode_t if not the first element in the hash table
			if (isfirst != 1) {
				free(curr);
			} 
			
			isfirst = 0;
			curr = next;
		}
	}

	free(ptr);		//Free the memory of the old hash table	
	return 0;
}

//Delete the entire hash table
void cachehashDelete(cachehashtable_t *table) {
	int i, isFirst;
	cachehashlistnode_t *ptr, *curr, *next;
	ptr = table->table;

	for(i=0 ; i<table->size ; i++) {
		curr = &ptr[i];
		isFirst = 1 ;
		while(curr  != NULL) {
			next = curr->next;
			if(isFirst != 1) {
				free(curr);
			}
			isFirst = 0;
			curr = next;
		}
	}

	free(ptr);
	free(table);
}
