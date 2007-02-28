#ifndef _DSTM_H_
#define _DSTM_H_

#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#define LOADFACTOR 0.75
#define HASH_SIZE 100

enum status {CLEAN, DIRTY};

typedef struct obj_header {
	unsigned int oid;
	unsigned short type;
	unsigned short version;
	unsigned short rcount;
	char status;
} obj_header_t;

typedef struct obj_store {
	unsigned int id;
	char *base;
	unsigned int size;
	char *top; 		//next available location
	struct obj_store *next;
} obj_store_t;

//use for hash tables, transaction records.
//to check oid, do object->oid
typedef struct obj_lnode{
	obj_header_t *object;
	unsigned int oid;
	struct obj_lnode *next;
} obj_listnode_t;

/*
typedef struct obj_addr_table {
	unsigned int size; 	//number of elements, not bytes
	obj_listnode_t *table; 	//this should point to an array of object lists, of the specified size
} obj_addr_table_t;
*/

typedef struct hash_table {
	obj_listnode_t **hash;	// points to beginning of hash table
	float loadfactor;
	unsigned int numelements;
	unsigned int size;
}obj_addr_table_t;

typedef struct trans_record {
	obj_listnode_t *obj_list;
	obj_store_t *cache;
	obj_addr_table_t *lookupTable;
} trans_record_t;

typedef struct obj_location_lnode {
	unsigned int oid;
	unsigned int mid;
	struct obj_location_lnode *next;
} obj_location_listnode_t;

typedef struct {
	unsigned int size; //number of elements, not bytes
	obj_location_listnode_t *table; //this should point to an array of object lists, of the specified size
} obj_location_table;

/* Prototypes for object store */
void dstm_init(void);
void create_objstr(unsigned int);
void delete_objstr(int);
obj_store_t *get_objstr_begin(void);
/* end object store */


/* Prototypes for object header */
int get_newID(void);
int insertObject(obj_header_t h); 
int getObjSize(obj_header_t h);
void createObject(unsigned short type); 
/* end object header */

/* Prototypes for hash*/
void createHash(obj_addr_table_t *, int , float);
void resize(obj_addr_table_t * table);
int hashkey(unsigned int, obj_addr_table_t *);
void addKey(unsigned int, obj_header_t *, obj_addr_table_t *);
obj_header_t *findKey(unsigned int,obj_addr_table_t *);
int removeKey(unsigned int, obj_addr_table_t *);
/* end for hash */


/*
void * allocate_size(unsigned int);
void initializeobj(unsigned int);
unsigned int getobjSize(obj_header *);
int insertAddr(obj_addr_table *, obj_header *);
int removeAddr(obj_addr_table *, unsigned int);
obj_header *getAddr(obj_addr_table *, unsigned int);
trans_record *transStart();
obj_header *transRead(trans_record *, unsigned int);
int transCommit(trans_record *);
int insertLocation(obj_location_table *, unsigned int, unsigned int);
int removeLocation(obj_location_table *, unsigned int);
unsigned int getLocation(obj_location_table *, unsigned int);
*/

#endif
