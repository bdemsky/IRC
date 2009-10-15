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
  void * buffer[1024];
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
#ifdef STMARRAY
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

#define STOREARRAY(x,z) {void * y=COMPOID(x); arraystack.array[arraystack.count]=y; arraystack.index[arraystack.count++]=z; dc_t_chashInsertOnce(y,y,z);}

#define STOREARRAYNOLOCK(x,z) {void * y=COMPOID(x); arraystack.array[arraystack.count]=y; arraystack.index[arraystack.count++]=z;}

#define STOREARRAYNOTRANS(x,z) {void * y=x; arraystack.array[arraystack.count]=y; arraystack.index[arraystack.count++]=z; dc_t_chashInsertOnce(y,y,z);}

#define STOREARRAYNOLOCKNOTRANS(x,z) {void * y=x; arraystack.array[arraystack.count]=y; arraystack.index[arraystack.count++]=z; }

//Pointers

#define RESTOREPTR(x) x=ptrstack.array[ptrstack.maxcount++];

#define STOREPTR(x) {void * y=COMPOID(x); ptrstack.array[ptrstack.count++]=y; dc_t_chashInsertOnce(y,y);}

#define STOREPTRNOLOCK(x) {void * y=COMPOID(x); ptrstack.array[ptrstack.count++]=y; }

#define STOREPTRNOTRANS(x) {void * y=x; ptrstack.array[ptrstack.count++]=y; dc_t_chashInsertOnce(y,y);}

#define STOREPTRNOLOCKNOTRANS(x) {void * y=x; ptrstack.array[ptrstack.count++]=y; }

//Branches

#define RESTOREANDBRANCH(loc) if (branchstack.array[branchstack.count++]) goto loc

#define STOREANDBRANCH(cond, loc) if (branchstack.array[branchstack.count++]=cond) goto loc

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
