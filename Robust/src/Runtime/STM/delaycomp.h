#ifndef DELAYCOMP_H
#define DELAYCOMP_H

//There is probably a better way for these...but we'll just hardcode
//them for now..probably a real implementation would page protect the
//page after...then default to something simpler

#define MAXPOINTERS 1024*1024*1
#define MAXVALUES 1024*1024*2
#define MAXBRANCHES 1024*1024*4

struct pointerlist {
  int count;
  void * prev;
  void * array[MAXPOINTERS];
};

struct primitivelist {
  int count;
  int array[MAXVALUES];
};

struct branchlist {
  int count;
  char array[MAXBRANCHES];
};

extern __thread struct pointerlist ptrstack;
extern __thread struct primitivelist primstack;
extern __thread struct branchlist branchstack;

//Pointers

#define RESTOREPTR(x) x=ptrstack.array[ptrstack.count++];

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
