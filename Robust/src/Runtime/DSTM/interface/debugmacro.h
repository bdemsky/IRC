#ifndef _DEBUGMACRO_H_
#define _DEBUGMACRO_H_

/** Macro to print oid and object type **/
//#define LOGOIDTYPES //turn on printing oid and type events
#ifdef LOGOIDTYPES
#define LOGOIDTYPE(x,y,z,t) printf("[%s: %u %u %lld]\n", x, y, z, t);
#else
#define LOGOIDTYPE(x,y,z,t)
#endif


/** Macro to print prefetch site id **/
//#define LOGPREFETCHSITES
#ifdef LOGPREFETCHSITES 
#define LOGPREFETCHSITE(PTR) printf("[siteid= %u] ", PTR->siteid);
#else
#define LOGPREFETCHSITE(PTR)
#endif


/*
#define LOGEVENTS //turn on Logging events
#ifdef LOGEVENTS
char bigarray[16*1024*1024];
int bigindex=0;
#define LOGEVENT(x) { \
    int tmp=bigindex++;				\
    bigarray[tmp]=x;				\
  }
#else
#define LOGEVENT(x)
#endif
*/

/**
 * Record Time after clock synchronization 
 **/
/*
#define LOGTIMES
#ifdef LOGTIMES
char bigarray1[8*1024*1024];
unsigned int bigarray2[8*1024*1024];
unsigned int bigarray3[8*1024*1024];
long long bigarray4[8*1024*1024];
int bigindex1=0;
#define LOGTIME(x,y,z,a) {\
  int tmp=bigindex1++; \
  bigarray1[tmp]=x; \
  bigarray2[tmp]=y; \
  bigarray3[tmp]=z; \
  bigarray4[tmp]=a; \
}
#else
#define LOGTIME(x,y,z,a)
#endif
*/

#endif
