#include "dstm.h"
#include "clookup.h"
#include "mlookup.h"
#include "llookup.h"

extern int classsize[];

transrecord_t *transStart()
{
	transrecord_t *tmp = malloc(sizeof(transrecord_t));
	tmp->cache = objstrCreate(1048576);
	tmp->lookupTable = chashCreate(HASH_SIZE, LOADFACTOR);
	return tmp;
}

objheader_t *transRead(transrecord_t *record, unsigned int oid)
{	
	unsigned int machinenumber;
	
	objheader_t *tmp, *objheader;
	void *objcopy;
	int size;
		//check cache
	if((objheader =(objheader_t *)chashSearch(record->lookupTable, oid)) != NULL){
		return(objheader);
	} else if ((objheader = (objheader_t *) mhashSearch(oid)) != NULL) {
		//Look up in Machine lookup table and found
		printf(" oid not found in cache\n");
		tmp = mhashSearch(oid);
		size = sizeof(objheader_t)+classsize[tmp->type];
		//Copy into cache
		objcopy = objstrAlloc(record->cache, size);
		memcpy(objcopy, (void *)tmp, size);
		//Insert into cache's lookup table
		chashInsert(record->lookupTable, objheader->oid, objcopy); 
		return(objcopy);
	} else {
		printf(" oid not found in Machine Lookup\n");
		machinenumber = lhashSearch(oid);
		//TODO:broadcast
		return(NULL);
	} 
}


objheader_t *transCreateObj(transrecord_t *record, unsigned short type)
{
	objheader_t *tmp = (objheader_t *) objstrAlloc(record->cache, classsize[type]);
	tmp->oid = getNewOID();
	tmp->type = type;
	tmp->version = 1;
	tmp->rcount = 0; //? not sure how to handle this yet
	tmp->status |= NEW;
	chashInsert(record->lookupTable, tmp->oid, tmp);
	return tmp;
}

int transCommit(transrecord_t *record){	
	//Move objects to machine that hosts it	

}

int transAbort(transrecord_t *record){

}
