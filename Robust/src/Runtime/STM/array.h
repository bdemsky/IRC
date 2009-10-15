#ifndef ARRAY_H
#define ARRAY_H

/* Array layout */
#define INDEXSHIFT 4   //must be at least 3 for doubles
//#define DBLINDEXSHIFT INDEXSHIFT-1   //must be at least 3 for doubles
#define INDEXLENGTH (1<<INDEXSHIFT)
#define LOWMASK (INDEXLENGTH-1) //mast off low order bits
#define HIGHMASK ~(LOWMASK) //mask off high order bits

#define STMNONE 0
#define STMCLEAN 1
#define STMDIRTY 2

#define MAXARRAYSIZE 2147483647

#define GETLOCKPTR(lock, array, byteindex) {				\
    lock=(unsigned int *)((char *)array-sizeof(objheader_t)-sizeof(int)*2*(byteindex>>INDEXSHIFT)-2*sizeof(int)); \
  }

#define GETLOCKVAL(lock, array, byteindex) {				\
    lock=*(unsigned int *)((char *)array-sizeof(objheader_t)-sizeof(int)*2*(byteindex>>INDEXSHIFT)-2*sizeof(int)); \
  }

#define GETVERSIONVAL(version, array, byteindex) {			\
    version=*(unsigned int *)((char *)array-sizeof(objheader_t)-sizeof(int)*2*(byteindex>>INDEXSHIFT)-sizeof(int)); \
  }

#define GETVERSIONPTR(version, array, byteindex) {			\
    version=(unsigned int *)((char *)array-sizeof(objheader_t)-sizeof(int)*2*(byteindex>>INDEXSHIFT)-sizeof(int)); \
  }

#define STMGETARRAY(dst, array, index, type) {				\
    int byteindex=index*sizeof(type);					\
    int * lengthoff=&array->___length___;				\
    int *status;							\
    if (array!=array->___objlocation___) {				\
      GETLOCKPTR(status, array, byteindex);				\
      if ((*status)==STMNONE&&!(array->___objstatus___&NEW)) {		\
	arraycopy(array, byteindex);					\
	*status=STMCLEAN;};						\
    }									\
    dst=((type *)(((char *) lengthoff)+sizeof(int)))[index];		\
  }

#define STMSETARRAY(array, index, src, type) {				\
    int byteindex=index*sizeof(type);					\
    int * lengthoff=&array->___length___;				\
    int *status;							\
    GETLOCKPTR(status, array, byteindex);				\
    if (*status==STMNONE&&!(array->___objstatus___&NEW))		\
      arraycopy(array, byteindex, sizeof(type)*(*lengthoff));		\
    *status=STMDIRTY;							\
    ((type *)(((char *) lengthoff)+sizeof(int)))[index]=src;		\
  }
#endif
