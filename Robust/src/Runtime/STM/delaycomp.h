#ifndef DELAYCOMP_H
#define DELAYCOMP_H

//There is probably a better way for these...but we'll just hardcode
//them for now..probably a real implementation would page protect the
//page after...then default to something simpler

#define MAXPOINTERS 1024*1024*1
#define MAXVALUES 1024*1024*2
#define MAXBRANCHES 1024*1024*4
#define MAXARRAY 1024*1024

struct pointerlist {
  int count;
  void * prev;
  void * array[MAXPOINTERS];
  int maxcount;
  int buffer[2048];
};

struct primitivelist {
  int count;
  int array[MAXVALUES+1024];
};

struct branchlist {
  int count;
  char array[MAXBRANCHES+4096];
};

extern __thread struct pointerlist ptrstack;
extern __thread struct primitivelist primstack;
extern __thread struct branchlist branchstack;
#if defined(STMARRAY)&&!defined(DUALVIEW)
struct arraylist {
  int count;
  void * prev;
  void *array[MAXARRAY];
  int maxcount;
  int index[MAXARRAY+1024];
};

extern __thread struct arraylist arraystack;
#endif

//Arrays

#define RESTOREARRAY(x,z) {x=arraystack.array[arraystack.maxcount];z=arraystack.index[arraystack.maxcount++];}

#define STOREARRAY(x,z,t) {void * y=COMPOID(x); int ii=z;arraystack.array[arraystack.count]=y; arraystack.index[arraystack.count++]=ii; dc_t_chashInsertOnceArray(y,(ii*sizeof(t))>>INDEXSHIFT);}

//Pointers

#define RESTOREPTR(x) x=ptrstack.array[ptrstack.maxcount++];

#define STOREPTR(x) {void * y=COMPOID(x); ptrstack.array[ptrstack.count++]=y; dc_t_chashInsertOnce(y);}

#define STOREPTRNOLOCK(x) {void * y=COMPOID(x); ptrstack.array[ptrstack.count++]=y; }

//Branches

#define RESTOREBRANCH(loc) (branchstack.array[branchstack.count++])

#define STOREBRANCH(cond) branchstack.array[branchstack.count++]=cond

//Integers

#define RESTOREI(x) x=primstack.array[primstack.count++]

#define STOREI(x) primstack.array[primstack.count++]=x

//Floats

#define RESTOREF(x) x=*((float *)&primstack.array[primstack.count++])

#define STOREF(x) *((float *)&primstack.array[primstack.count++])=x

//Doubles

#define RESTORED(x) x=*((double *)&primstack.array[primstack.count]); primstack.count+=2

#define STORED(x) *((double *)&primstack.array[primstack.count])=x; primstack.count+=2

//Bytes

#define RESTOREB(x) x=*((char *)&primstack.array[primstack.count++])

#define STOREB(x) *((char *)&primstack.array[primstack.count++])=x

//Characters

#define RESTOREC(x) x=*((short *)&primstack.array[primstack.count++])

#define STOREC(x) *((short *)&primstack.array[primstack.count++])=x

//Longs

#define RESTOREJ(x) x=*((long long *)&primstack.array[primstack.count]); primstack.count+=2

#define STOREJ(x) *((long long *)&primstack.array[primstack.count])=x; primstack.count+=2

//Booleans

#define RESTOREZ(x) x=primstack.array[primstack.count++]

#define STOREZ(x) primstack.array[primstack.count++]=x

#endif
