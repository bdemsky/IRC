#include "dstm.h"

extern int classsize[];

/* BEGIN object header */

// Get a new object id
unsigned int getNewOID(void) {
	static int id = 1;
	return id+=2;
}

// Get the size of the object for a given type
unsigned int objSize(objheader_t *object) {
	return classsize[TYPE(object)];
}

/* END object header */

