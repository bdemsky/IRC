/* This is the C file for the "objects" */

/****************************************
* $File: Object.c
* $Author: adash@uci.edu	
* $Revision: 1.1 $Date:02/12/2006
* $Description: Header file for objects
* ******************************************/

#include "object.h"
#include <stdio.h>

unsigned int getobjid(object_t *obj) {
	return obj->o_id;
}

short getobjversion(object_t *obj) {
	return obj->version;
}

short getobjtype(object_t *obj) {
	return obj->type;
}

 
short getobjrcount(object_t *obj) {
	return obj->ref_count;
} 

object_t *createobject(void) {
	/* TODO:
	   1. Create object using malloc
	   2. Assign it a unique id
	   3. Insert a new entry in machine table with <id, addr>
	   */
}

int deleteobject(object *ptr) {
	/* TODO:
	   1. Remove entry from machine entry
	   2. Inform other machines of delete
	   3. free memory
	   4. decrement object reference count
	   */
}
