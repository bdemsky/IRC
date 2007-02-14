
/****************************************************
* $File: machinelookup.c
* $Author: adash@uci.edu	
* $Revision: 1.1 $Date:02/12/2006
* $Description: machine/node look up table operations 
******************************************************/

#include "object.h"
#include "machinelookup.h"

void* getaddress(int o_id){
//TODO: 1. lookup for o_id and get address)

}

unsigned hash(char *id) {
	unsigned hashval;
	for(hashval = 0; *id!= 100;  id++)
		hashval= *id + 31 * hashval;
	return hashval % HASHSIZE;
}

	
