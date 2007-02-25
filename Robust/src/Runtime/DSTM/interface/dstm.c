#include "dstm.h"

obj_store_t *obj_begin; 
obj_listnode_t *obj_lnode_begin;

extern int classsize[];

/* BEGIN - object store */

void dstm_init(void) {
	obj_begin = NULL;
	obj_lnode_begin = NULL;
	return;
}

void create_objstr(unsigned int size) {
	obj_store_t *tmp, *ptr;
	static int id = 0;

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
	if (obj_begin == NULL) { 	// First entry
		obj_begin = tmp;
		tmp->next = NULL;
	} else { 			// Insert to the front of the linked list
		tmp->next = obj_begin;
		obj_begin = tmp;
	}
	return;
}

void delete_objstr(int id) {
	//TODO Implement this along with garbage collector
	return;
}

obj_store_t *get_objstr_begin(void) {
	return obj_begin;
}

/* END object store */

/* BEGIN object header */
int get_newID(void) {
	static int id = 0;
	
	return ++id;
}

int insertObject(obj_header_t h) {
	int left, req;

	left = obj_begin->size - (obj_begin->top - obj_begin->base);
	req = getObjSize(h) + sizeof(obj_header_t);
	if (req < left) {
		memcpy(obj_begin->top, &h, sizeof(obj_header_t));
		obj_begin->top = obj_begin->top + sizeof(obj_header_t) + getObjSize(h);
	} else {
		return -1;
	}
	//TODO Update obj_addr_table
	return 0;
}

int getObjSize(obj_header_t h) {
	return classsize[h.type];
}

void createObject(unsigned short type) {
	obj_header_t h;

	h.oid = get_newID();
	h.type = type;
	h.version = 0;
	h.rcount = 1;
	h.status = CLEAN;
	insert_object(h);
}
/* END object header */

/* BEGIN obj_listnode */

int IsEmpty(obj_listnode_t *node) {
	return obj_lnode_begin == NULL;
}

void insert_lnode(obj_listnode_t *node) {
	if(obj_lnode_begin == NULL) { 		// Enter "node" as the first node in linked list
		obj_lnode_begin = node;
		node->next = NULL;
	} else {                              // Enter "node" to the beginning of the linked list
		node->next = obj_lnode_begin;
		obj_lnode_begin = node ;

	}
				
}

int delete_lnode(obj_listnode_t *node, unsigned int oid) {
	obj_listnode_t pre, curr;
	if( obj_lnode_begin == NULL){
		printf(" No nodes to delete ");
		return -1;
	 }else {
	//Traverse list 	
	pre = curr = obj_lnode_begin;
	while(node->next != NULL) {
		pre = curr;
		curr= curr->next;
		if(oid == node->oid){
		}
	}
}


/* END obj_listnode */
