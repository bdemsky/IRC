#include "dstm.h"
#include "clookup.h"
#include "mlookup.h"
#include "llookup.h"

transrecord_t *transStart()
{
	transrecord_t *tmp = malloc(sizeof(transrecord_t));
	tmp->cache = objstrCreate(1048576);
	tmp->lookupTable = cachehashCreate(HASH_SIZE, LOADFACTOR);
	return tmp;
}

objheader_t *transRead(transrecord_t *record, unsigned int oid)
{
		//check cache
		//else check machine lookup table
		//else check location lookup table
		//else broadcast
}

objheader_t *transCreateObj(transrecord_t *record, unsigned short type)
{
	objheader_t *tmp = objstrAlloc(record->cache, classsize[type]);
	tmp->oid = getNewOID();
	tmp->type = type;
	tmp->version = 1;
	tmp->rcount = 0; //? not sure how to handle this yet
	tmp->status |= NEW;
	cachehashInsert(record->lookupTable, tmp->oid, tmp);
	return tmp;
}

int transCommit(transrecord_t *record)
{
	
}

