
typedef struct {
	unsigned int oid;
	unsigned short type;
	unsigned short version;
	unsigned short rcount;
	unsigned short status;
} obj_header;

typedef struct obj_str{
	void *base;
	unsigned int size;
	void *top; //next available location
	struct obj_str *next;
} obj_store;

//use for hash tables, transaction records.
//to check oid, do object->oid
typedef struct obj_lnode{
	obj_header *object;
	struct obj_lnode *next;
} obj_listnode;

typedef struct {
	unsigned int size; //number of elements, not bytes
	obj_listnode *table; //this should point to an array of object lists, of the specified size
} obj_addr_table;

typedef struct {
	obj_listnode *obj_list;
	obj_store *cache;
	obj_addr_table *lookupTable;
} trans_record;

typedef struct obj_location_lnode {
	unsigned int oid;
	unsigned int mid;
	struct obj_location_lnode *next;
} obj_location_listnode;

typedef struct {
	unsigned int size; //number of elements, not bytes
	obj_location_listnode *table; //this should point to an array of object lists, of the specified size
} obj_location_table;

unsigned int objSize(obj_header *);
int insertAddr(obj_addr_table *, obj_header *);
int removeAddr(obj_addr_table *, unsigned int);
obj_header *getAddr(obj_addr_table *, unsigned int);
trans_record *transStart();
obj_header *transRead(trans_record *, unsigned int);
int transCommit(trans_record *);
int insertLocation(obj_location_table *, unsigned int, unsigned int);
int removeLocation(obj_location_table *, unsigned int);
unsigned int getLocation(obj_location_table *, unsigned int);

