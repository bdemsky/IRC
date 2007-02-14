/* This is a header file for  class objects */
/*****************************************************
 * $File: Object.h
 * $Author: adash@uci.edu
 * $Revision: 1.1 $Date:02/12/2006
 * $Description: This has all definitions for object
 ****************************************************/

#ifndef _object_h_
#define _object_h_
struct object_t {
	int o_id;
 	short version;
	int type;
	short ref_count;
	char update_flag;
};
typedef struct object_t object_t;

int getobjid(object_t *obj);
short getobjversion(object_t *obj);
int getobjtype(object_t *obj);
short getobjrcount(object_t *obj);
object_t *createobject(void);
int deleteobject(object_t *);

#endif
