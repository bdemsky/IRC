/* This is a header file for Machine look up table */
/***********************************************************************
* $File: Machinelookup.h
* $Author: adash
* $Revision: 1.1 $Date:02/12/2006
* $Description: This has all the definitions of a machine lookup table
************************************************************************/
#ifndef _machine_lookup_h_
#define _machine_lookup_h_

#define HASHSIZE 101

static struct machinelookup *hashtab[HASHSIZE];
struct machinelookup {			//table entry
	struct machinelookup *next;	// next  entry in table
        int *o_id;			//O_id of the object
	void *ptr;			//address of the Object
};
typedef struct machinelookup mlt;

void * getaddress(int o_id);
unsigned hash(char *id);
//struct machinelookup *lookup( int *);

#endif
