#include "queue.h"
#include "dstm.h"

#define GET_STRIDE(x) ((x & 0x7000) >> 12)
#define GET_RANGE(x) (x & 0x0fff)
#define GET_STRIDEINC(x) ((x & 0x8000) >> 15)
void rangePrefetch(int, int, unsigned int *, unsigned short *, short *offset);
void *transPrefetchNew();
void checkIfLocal(char *ptr);
int isOidAvail(unsigned int oid);
int lookForObjs(int*, short *, int *, int *, int *);

/************* Internal functions *******************/
int getsize(short *ptr, int n);
