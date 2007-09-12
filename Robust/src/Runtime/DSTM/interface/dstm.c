#include "dstm.h"

extern int classsize[];

/* BEGIN object header */

// Get the size of the object for a given type
unsigned int objSize(objheader_t *object) {
	return classsize[TYPE(object)];
}

/* END object header */

