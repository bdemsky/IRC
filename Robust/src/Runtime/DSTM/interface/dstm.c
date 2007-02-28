#include "dstm.h"

obj_store_t *obj_begin; 		// points to the first location of the object store linked list
//unsigned int hash_size;			// number of entries in hash table

extern int classsize[];

/* BEGIN - object store */
// Initializes the pointers...currently invoked inside main()
void dstm_init(void) {
	obj_begin = NULL;
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
	printf("The object store size is : %d\n", tmp->size);
	tmp->top = tmp->base;
	tmp->id = id++;
	printf("The object store id is : %d\n", tmp->id);
	tmp->next = obj_begin;		//adds new object store to the linked list and updates obj_begin pointer to new object store node
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
	extern obj_addr_table_t mlut;		//declared in the main mlut=>machine look up table
	unsigned int left, req;  		// Keeps track of the space available in the current object store
	obj_header_t *header;
	
	left = obj_begin->size - (obj_begin->top - obj_begin->base);
	req = getObjSize(h) + sizeof(obj_header_t);
	if (req < left) {
		memcpy(obj_begin->top, &h, sizeof(obj_header_t));
		header = (obj_header_t *) obj_begin->top;
		printf("The header points to : %d\n", header);
		obj_begin->top = obj_begin->top + sizeof(obj_header_t) + getObjSize(h); //increment object store top when new object is inserted
		printf("Top now points to :%d\n", obj_begin->top);
	} else {
		return -1;
	}
	printf("header: %d\n", header);
	printf("The oid is : %d\n", h.oid);
	addKey(h.oid, header, &mlut);			//Update obj_addr_table
	printf("Object id = %d\n",h.oid);
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
//obj_addr_table is a generic hash table structure
//hash is an array of pointers that point to the beginning of a obj_listnode_t DS
// "size" in hash table is the no of indices in the hash table  and each index points to a pointer for an object_header  
void createHash(obj_addr_table_t *table, int size, float loadfactor)  {
	int i;

	if ((table->hash = (obj_listnode_t **) malloc(sizeof(obj_listnode_t *)*size)) == NULL) {
		printf("Malloc error %s %d\n", __FILE__, __LINE__);
		exit(-1);
	}
	for (i = 0; i < size; i++) {		// initialize the hash elements
		table->hash[i] = NULL;
	}

	table->size = size;
	table->numelements = 0;
	table->loadfactor = loadfactor;
	
	return;
}

// Hash Resize
void resize(obj_addr_table_t * table){
	int newCapacity = 2*(table->size) + 1;
	obj_listnode_t **old;
	//if ((table->hash = (obj_listnode_t **) malloc(sizeof(obj_listnode_t *)*size)) == NULL) {
}

// Hashing for the Key
int hashKey(unsigned int oid, obj_addr_table_t *table) {
	// hash32shiftmult
	int c2=0x27d4eb2d; // a prime or an odd constant
	oid = (oid ^ 61) ^ (oid >> 16);
	oid = oid + (oid << 3);
	oid = oid ^ (oid >> 4);
	oid = oid * c2;
	oid = oid ^ (oid >> 15);
	printf("The bucket number is %d\n", oid % (table->size));
	return (oid % (table->size));
}

//Add oid and its address to the new ob_listnode_t 
void addKey(unsigned int oid, obj_header_t *ptr, obj_addr_table_t *table) {
	int index;
	obj_listnode_t *node;
	
	table->numelements++;
	if(table->numelements > (table->loadfactor * table->size)){
	//TODO : check if table is nearly full and then resize
	}

	index = hashKey(oid,table);
	if ((node = (obj_listnode_t *) malloc(sizeof(obj_listnode_t))) == NULL) {
		printf("Malloc error %s %d\n", __FILE__, __LINE__);
		exit(-1);
	}
	node->oid = oid;
	node->object = ptr; 
	node->next = table->hash[index];
	table->hash[index] = node;
	return;
}
// Get the address of the object header for a given oid
obj_header_t *findKey(unsigned int oid, obj_addr_table_t *table) {
	int index;
	obj_listnode_t *ptr;

	index = hashKey(oid,table);
	ptr = table->hash[index];
	while(ptr != NULL) {
		if (ptr->oid == oid) {
			return ptr->object;
		}
		ptr = ptr->next;
	}
	return NULL;
}
// Remove the pointer to the object header from a linked list of obj_listnode_t given an oid
int removeKey(unsigned int oid, obj_addr_table_t *table) {
	int index;
	obj_listnode_t *curr, *prev;		// prev points to previous node and curr points to the node to be deleted

	index = hashKey(oid,table);
	prev = curr = table->hash[index];
	for (; curr != NULL; curr = curr->next) {
		if (curr->oid == oid) {		// Find a match in the hash table
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
