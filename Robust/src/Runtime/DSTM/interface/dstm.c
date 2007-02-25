#include "dstm.h"

obj_store_t *obj_begin; 		// points to the first location of the object store linked list
obj_listnode_t **hash;			// points to beginning of hash table
unsigned int hash_size;			// number of entries in hash table

extern int classsize[];

/* BEGIN - object store */
// Initializes the pointers...currently invoked inside main()
void dstm_init(void) {
	obj_begin = NULL;
	hash = NULL;
	hash_size = 0;
	return;
}
// Create a new object store of a given "size"  as a linked list
void create_objstr(unsigned int size) {
	obj_store_t *tmp, *ptr;
	static int id = 0; // keeps track of which object store is it when there are more than one object store 

	if ((tmp = (obj_store_t *) malloc(sizeof(obj_store_t))) < 0) {
		printf("DSTM: Malloc error %s %d\n", __FILE__, __LINE__);
		exit(-1);
	}
	if ((tmp->base = (char *) malloc(sizeof(char)*size)) < 0) {
		printf("DSTM: Malloc error %s %d\n", __FILE__, __LINE__);
		exit(-1);
	}
	tmp->size = size;
	tmp->top = tmp->base;
	tmp->id = id++;
	tmp->next = obj_begin;
	obj_begin = tmp;
	return;
}

// Delete the object store numbered "id"
void delete_objstr(int id) {
	//TODO Implement this along with garbage collector
	return;
}

obj_store_t *get_objstr_begin(void) {
	return obj_begin;
}

/* END object store */

/* BEGIN object header */
// Get a new object id
int get_newID(void) {
	static int id = 0;
	
	return ++id;
}
// Insert the object header and object into the object store
int insertObject(obj_header_t h) {
	unsigned int left, req;  		// Keeps track of the space available in the current object store
	obj_header_t *header;

	left = obj_begin->size - (obj_begin->top - obj_begin->base);
	req = getObjSize(h) + sizeof(obj_header_t);
	if (req < left) {
		memcpy(obj_begin->top, &h, sizeof(obj_header_t));
		header = (obj_header_t *) obj_begin->top;
		obj_begin->top = obj_begin->top + sizeof(obj_header_t) + getObjSize(h);
	} else {
		return -1;
	}
	//TODO Update obj_addr_table
	addKey(h.oid, header);

	return 0;
}
// Get the size of the object for a given type
int getObjSize(obj_header_t h) {
	return classsize[h.type];
}
// Initial object when it is created at first
void createObject(unsigned short type) {
	obj_header_t h;

	h.oid = get_newID();
	h.type = type;
	h.version = 0;
	h.rcount = 1;
	h.status = CLEAN;
	insertObject(h);
}
/* END object header */

/* BEGIN hash*/

//hash table is an array of pointers that point to the beginning of a obj_listnode_t DS
// "size" in hash table is the no of indices in the hash table  and each index points to a pointer for an object_header  
void createHash(int size)  {
	int i;

	if ((hash = (obj_listnode_t **) malloc(sizeof(obj_listnode_t *)*size)) == NULL) {
		printf("Malloc error %s %d\n", __FILE__, __LINE__);
		exit(-1);
	}
	for (i = 0; i < size; i++) {		// initialize the hash elements
		hash[i] = NULL;
	}
	hash_size = size;
	return;
}

// Hashing for the Key
int hashkey(unsigned int oid) {
	//TODO: Design a better hash function
//	return (hash_size % oid);
	return (oid % hash_size);
}

//Add oid and its address to the new ob_listnode_t 
void addKey(unsigned int oid, obj_header_t *ptr) {
	int index;
	obj_listnode_t *node;

	index = hashkey(oid);
	if ((node = (obj_listnode_t *) malloc(sizeof(obj_listnode_t))) == NULL) {
		printf("Malloc error %s %d\n", __FILE__, __LINE__);
		exit(-1);
	}
	node->oid = oid;
	node->object = ptr; 
	node->next = hash[index];
	hash[index] = node;
	return;
}
// Get the address of the object header for a given oid
obj_header_t *findKey(unsigned int oid) {
	int index;
	obj_listnode_t *ptr;

	index = hashkey(oid);
	ptr = hash[index];
	while(ptr != NULL) {
		if (ptr->oid == oid) {
			return ptr->object;
		}
		ptr = ptr->next;
	}
	return NULL;
}
// Remove the pointer to the object header from a linked list of obj_listnode_t given an oid
int removeKey(unsigned int oid) {
	int index;
	obj_listnode_t *curr, *prev;		// prev points to previous node and curr points to the node to be deleted

	index = hashKey(oid);
	prev = curr = hash[index];
	for (; curr != NULL; curr = curr->next) {
		if (curr->oid == oid) {		// Find a match in the hash table
			prev->next = curr->next;
			if (hash[index] == curr) { // Special case when there is one element pointed by  the hash table
				hash[index] = NULL;
			}
			free(curr);
			return 0;
		} 
		prev = curr;
	}
	return -1;
}
