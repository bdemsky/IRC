#ifndef ARRAY_H
#define ARRAY_H

/* Array layout */
#define INDEXSHIFT 4   //must be at least 3 for doubles
#define DBLINDEXSHIFT INDEXSHIFT-1   //must be at least 3 for doubles

#define GETLOCKPTR(lock, array, byteindex) {				\
    lock=(int *)((char *)array-sizeof(objheader_t)-sizeof(int)*(byteindex>>DBLINDEXSHIFT)); \
  }

#define GETVERSIONPTR(version, array, byteindex) {			\
    version=(int *)((char *)array-sizeof(objheader_t)-sizeof(int)*(byteindex>>DBLINDEXSHIFT)-sizeof(int)); \
  }
#endif
