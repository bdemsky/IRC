#ifndef ARRAY_H
#define ARRAY_H

/* Array layout */
#define INDEXSHIFT 5   //must be at least 3 for doubles
#define INDEXLENGTH (1<<INDEXSHIFT)
#define LOWMASK (INDEXLENGTH-1) //mast off low order bits
#define HIGHMASK ~(LOWMASK) //mask off high order bits

#define STMNONE 0
#define STMCLEAN 1
#define STMDIRTY 2

#define MAXARRAYSIZE 2147483647

#define GETLOCKPTR(lock, array, byteindex) {				\
    lock=(unsigned int *)((char *)array-sizeof(objheader_t)-sizeof(int)*2*(byteindex)-2*sizeof(int)); \
  }

#define GETLOCKVAL(lock, array, byteindex) {				\
    lock=*(unsigned int *)((char *)array-sizeof(objheader_t)-sizeof(int)*2*(byteindex)-2*sizeof(int)); \
  }

#define GETVERSIONVAL(version, array, byteindex) {			\
    version=*(unsigned int *)((char *)array-sizeof(objheader_t)-sizeof(int)*2*(byteindex)-sizeof(int)); \
  }

#define SETVERSION(array, byteindex, version) {				\
    *(unsigned int *)((char *)array-sizeof(objheader_t)-sizeof(int)*2*(byteindex)-sizeof(int))=version; \
  }

#define GETVERSIONPTR(version, array, byteindex) {			\
    version=(unsigned int *)((char *)array-sizeof(objheader_t)-sizeof(int)*2*(byteindex)-sizeof(int)); \
  }

#ifdef EVENTMONITOR
#define EVGETARRAY(array, index) EVLOGEVENTARRAY(EV_ARRAYREAD,array->objuid,index)
#define EVSETARRAY(cond, array, index) if (cond!=STMDIRTY) EVLOGEVENTARRAY(EV_ARRAYWRITE,array->objuid,index)
#else
#define EVGETARRAY(array, index)
#define EVSETARRAY(cond, array, index)
#endif

#define STMGETARRAY(dst, array, index, type) {				\
    if (((char *)array)!=((char *)array->___objlocation___)) {		\
      if(!(array->___objstatus___&NEW)) {				\
	int byteindex=index*sizeof(type);				\
	int *status;							\
	int metaindex=(byteindex&HIGHMASK)>>INDEXSHIFT;			\
	GETLOCKPTR(status, array, metaindex);				\
	if (metaindex<array->lowindex||metaindex>array->highindex	\
	    ||(*status)==STMNONE) {					\
	  EVGETARRAY(array, metaindex);					\
	  arraycopy(array, byteindex);					\
	  (*status)=STMCLEAN;}						\
      }									\
    }									\
    dst=((type *)(((char *) &array->___length___)+sizeof(int)))[index];	\
  }

#define STMSETARRAY(array, index, src, type) {				\
    if (!(array->___objstatus___&NEW)) {				\
      int *status;							\
      int byteindex=index*sizeof(type);					\
      int metaindex=(byteindex&HIGHMASK)>>INDEXSHIFT;			\
      GETLOCKPTR(status, array, metaindex);				\
      if (metaindex<array->lowindex||metaindex>array->highindex		\
	  ||(*status)==STMNONE) {					\
	arraycopy(array, byteindex);					\
      }									\
      EVSETARRAY(*status, array, metaindex);				\
      (*status)=STMDIRTY;						\
    }									\
    ((type *)(((char *) &array->___length___)+sizeof(int)))[index]=src;	\
  }
#endif

#define VERSIONINCREMENT(array, index, type) {				\
    unsigned int * versionptr;						\
    GETVERSIONPTR(versionptr, array,((sizeof(type)*index)>>INDEXSHIFT)); \
    (*versionptr)++;							\
}
