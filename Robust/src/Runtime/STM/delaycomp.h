#ifndef DELAYCOMP_H
#define DELAYCOMP_H

//There is probably a better way for these...but we'll just hardcode
//them for now..probably a real implementation would page protect the
//page after...then default to something simpler

#define MAXPOINTERS 1024*1024*16
#define MAXVALUES 1024*1024*16

struct pointerlist {
  int count;
  void * prev;
  void * array[MAXPOINTERS];
};

struct primitivelist {
  int count;
  int array[MAXVALUES];
};

extern __thread struct pointerlist ptrstack;
extern __thread struct primitivelist primstack;

//Pointers

#define RESTOREPTR(x) x=ptrstack.array[--ptrstack.count]

#define STOREPTR(x) ptrstack.array[ptrstack.count++]=x; dc_t_chashInsertOnce(x,x);

//Branches

#define RESTOREANDBRANCH(loc) if (primstack.array[--primstack.count]) goto loc

#define STOREANDBRANCH(cond, loc) if (primstack.array[primstack.count++]=cond) goto loc

//Integers

#define RESTOREI(x) x=primstack.array[--primstack.count]

#define STOREI(x) primstack.array[primstack.count++]=x

//Floats

#define RESTOREF(x) x=*((float *)&primstack.array[--primstack.count])

#define STOREF(x) *((float *)&primstack.array[primstack.count++])=x

//Doubles

#define RESTORED(x) x=*((double *)&primstack.array[primstack.count-=2])

#define STORED(x) *((double *)&primstack.array[primstack.count])=x; primstack.count+=2

//Bytes

#define RESTOREB(x) x=*((char *)&primstack.array[--primstack.count])

#define STOREB(x) *((char *)&primstack.array[primstack.count++])=x

//Characters

#define RESTOREC(x) x=*((short *)&primstack.array[--primstack.count])

#define STOREC(x) *((short *)&primstack.array[primstack.count++])=x

//Doubles

#define RESTOREJ(x) x=*((long long *)&primstack.array[primstack.count-=2])

#define STOREJ(x) *((long long *)&primstack.array[primstack.count])=x; primstack.count+=2

//Booleans

#define RESTOREZ(x) x=primstack.array[--primstack.count]

#define STOREZ(x) primstack.array[primstack.count++]=x

#endif
