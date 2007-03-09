#include "mlookup.h"

mhashtable_t mlookup; 	//Global hash table

unsigned int mhashCreate(unsigned int size, float loadfactor)  {
	mhashlistnode_t *nodes;
	int i;

	// Allocate space for the hash table 
	if((nodes = calloc(HASH_SIZE, sizeof(mhashlistnode_t))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		return 1;
	}
	
	mlookup.table = nodes;
	mlookup.size = size;
	mlookup.numelements = 0; // Initial number of elements in the hash
	mlookup.loadfactor = loadfactor;
	return 0;
}

// Assign to keys to bins inside hash table
unsigned int mhashFunction(unsigned int key) {
	return( key % (mlookup.size));
}

// Insert value and key mapping into the hash table
unsigned int mhashInsert(unsigned int key, void *val) {
	unsigned int newsize;
	int index;
	mhashlistnode_t *ptr, *node;
	
	if (mlookup.numelements > (mlookup.loadfactor * mlookup.size)) {
		//Resize Table
		newsize = 2 * mlookup.size + 1;		
		mhashResize(newsize);
	}
	ptr = mlookup.table;
	mlookup.numelements++;
	
	index = mhashFunction(key);
#ifdef DEBUG
	printf("DEBUG -> index = %d, key = %d, val = %x\n", index, key, val);
#endif
	if(ptr[index].next == NULL && ptr[index].key == 0) {	// Insert at the first position in the hashtable
		ptr[index].key = key;
		ptr[index].val = val;
	} else {			// Insert in the beginning of linked list
		if ((node = calloc(1, sizeof(mhashlistnode_t))) == NULL) {
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

// Return val for a given key in the hash table
void *mhashSearch(unsigned int key) {
	int index;
	mhashlistnode_t *ptr, *node;

	ptr = mlookup.table;	// Address of the beginning of hash table	
	index = mhashFunction(key);
	node = &ptr[index];
	while(node != NULL) {
		if(node->key == key) {
			return node->val;
		}
		node = node->next;
	}
	return NULL;
}

// Remove an entry from the hash table
unsigned int mhashRemove(unsigned int key) {
	int index;
	mhashlistnode_t *curr, *prev;
	mhashlistnode_t *ptr, *node;
	
	ptr = mlookup.table;
	index = mhashFunction(key);
	curr = &ptr[index];

	for (; curr != NULL; curr = curr->next) {
		if (curr->key == key) {         // Find a match in the hash table
			mlookup.numelements--;  // Decrement the number of elements in the global hashtable
			if ((curr == &ptr[index]) && (curr->next == NULL))  { // Delete the first item inside the hashtable with no linked list of mhashlistnode_t 
				curr->key = 0;
				curr->val = NULL;
			} else if ((curr == &ptr[index]) && (curr->next != NULL)) { //Delete the first item with a linked list of mhashlistnode_t  connected 
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

// Resize table
unsigned int mhashResize(unsigned int newsize) {
	mhashlistnode_t *node, *ptr, *curr, *next;	// curr and next keep track of the current and the next mhashlistnodes in a linked list
	unsigned int oldsize;
	int isfirst;    // Keeps track of the first element in the mhashlistnode_t for each bin in hashtable
	int i,index;   	
	mhashlistnode_t *newnode; 		
	
	ptr = mlookup.table;
	oldsize = mlookup.size;
	
	if((node = calloc(newsize, sizeof(mhashlistnode_t))) == NULL) {
		printf("Calloc error %s %d\n", __FILE__, __LINE__);
		return 1;
	}

	mlookup.table = node; 		//Update the global hashtable upon resize()
	mlookup.size = newsize;
	mlookup.numelements = 0;

	for(i = 0; i < oldsize; i++) {			//Outer loop for each bin in hash table
		curr = &ptr[i];
		isfirst = 1;			
		while (curr != NULL) {			//Inner loop to go through linked lists
			if (curr->key == 0) {		//Exit inner loop if there the first element for a given bin/index is NULL
				break;			//key = val =0 for element if not present within the hash table
			}
			next = curr->next;

			index = mhashFunction(curr->key);
#ifdef DEBUG
			printf("DEBUG(resize) -> index = %d, key = %d, val = %x\n", index, curr->key, curr->val);
#endif
			// Insert into the new table
			if(mlookup.table[index].next == NULL && mlookup.table[index].key == 0) { 
				mlookup.table[index].key = curr->key;
				mlookup.table[index].val = curr->val;
				mlookup.numelements++;
			}else { 
				if((newnode = calloc(1, sizeof(mhashlistnode_t))) == NULL) { 
					printf("Calloc error %s, %d\n", __FILE__, __LINE__);
					return 1;
				}       
				newnode->key = curr->key;
				newnode->val = curr->val;
				newnode->next = mlookup.table[index].next;
				mlookup.table[index].next = newnode;    
				mlookup.numelements++;
			}       

			//free the linked list of mhashlistnode_t if not the first element in the hash table
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

#if 0
// Hash Resize
vkey resize(obj_addr_table_t * table){
	int newCapacity = 2*(table->size) + 1;
	obj_listnode_t **old;
	//if ((table->hash = (obj_listnode_t **) malloc(sizeof(obj_listnode_t *)*size)) == NULL) {
}

// Hashing for the Key
int hashKey(unsigned int key, obj_addr_table_t *table) {
	// hash32shiftmult
	int c2=0x27d4eb2d; // a prime or an odd constant
	key = (key ^ 61) ^ (key >> 16);
	key = key + (key << 3);
	key = key ^ (key >> 4);
	key = key * c2;
	key = key ^ (key >> 15);
	printf("The bucket number is %d\n", key % (table->size));
	return (key % (table->size));
}

//Add key and its address to the new ob_listnode_t 
vkey addKey(unsigned int key, objheader_t *ptr, obj_addr_table_t *table) {
	int index;
	obj_listnode_t *node;
	
	table->numelements++;
	if(table->numelements > (table->loadfactor * table->size)){
	//TODO : check if table is nearly full and then resize
	}

	index = hashKey(key,table);
	if ((node = (obj_listnode_t *) malloc(sizeof(obj_listnode_t))) == NULL) {
		printf("Malloc error %s %d\n", __FILE__, __LINE__);
		exit(-1);
	}
	node->key = key;
	node->object = ptr; 
	node->next = table->hash[index];
	table->hash[index] = node;
	return;
}
// Get the address of the object header for a given key
objheader_t *findKey(unsigned int key, obj_addr_table_t *table) {
	int index;
	obj_listnode_t *ptr;

	index = hashKey(key,table);
	ptr = table->hash[index];
	while(ptr != NULL) {
		if (ptr->key == key) {
			return ptr->object;
		}
		ptr = ptr->next;
	}
	return NULL;
}
// Remove the pointer to the object header from a linked list of obj_listnode_t given an key
int removeKey(unsigned int key, obj_addr_table_t *table) {
	int index;
	obj_listnode_t *curr, *prev;		// prev points to previous node and curr points to the node to be deleted

	index = hashKey(key,table);
	prev = curr = table->hash[index];
	for (; curr != NULL; curr = curr->next) {
		if (curr->key == key) {		// Find a match in the hash table
			table->numelements--;
			prev->next = curr->next;
			if (table->hash[index] == curr) { // Special case when there is one element pointed by  the hash table
				table->hash[index] = NULL;
			}
			free(curr);
			return 0;
		} 
		prev = curr;
	}
	return -1;
} 

#endif
