#ifndef HASHSTRUCTURE_H_
#define HASHSTRUCTURE_H_

#include "mlp_runtime.h"
//#include "WaitingQueue.h"
#include "memPool.h"

#define bitvt unsigned long long

//Note READEFFECT = READBIN and WRITEEFFECT=WRITEBIN. They mean the same thing
//but are named differently for clarity in code.
#define READEFFECT 0
#define WRITEEFFECT 1
#define WAITINGQUEUENOTE 2

#define READBIN 0
#define WRITEBIN 1
#define BINMASK 1
#define PARENTBIN 1ULL

#define SPEC 2
#define READY 1          //Item is ready and we haven't seen this bin before
#define NOTREADY 0       //Item is not ready and we haven't seen this bin before
#define SPECREADY (SPEC|READY)      //Item is ready and we've seen this bin before
#define SPECNOTREADY (SPEC|NOTREADY)   //Item is not ready and we've seen this bin before
#define READYMASK 1


#define TRUE 1
#define FALSE 0

#define RNUMBINS 131072
#define RNUMREAD 24
#define RH_MASK (RNUMBINS)-1

//Note: put resolved things at the end and unresolved at the front.
typedef struct BinItem_rcr {
  int total;
  int status;
  int type;
  //TODO keep track of record ptr here
  struct BinItem_rcr * next;
} BinItem_rcr;

typedef struct BinElement_rcr {
  BinItem_rcr * head;
  BinItem_rcr * tail;
} BinElement_rcr;

typedef struct Hashtable_rcr {
  BinElement_rcr array[RNUMBINS];
  //  WaitingQueueBin * waitingQueue;
  
  // use a different memory queue for each
  // bin item type because the difference in
  // size is very big
  MemPool* memPoolWrite;
  MemPool* memPoolRead;
} HashStructure;

//Todo this is a clone of REntry, remove data fields as necessary
typedef struct Entry_rcr {
  SESEcommon * task;
  bitvt bitindex;
} TraverserData;

typedef struct WriteBinItem_rcr {
  BinItem_rcr item;
  SESEcommon * task;
  bitvt bitindexwr;
  bitvt bitindexrd;
} WriteBinItem_rcr;

typedef struct ReadBinItem_rcr {
  BinItem_rcr item;
  TraverserData array[RNUMREAD];
  //We don't need a head index since if the item before it was freed, then all these would be considered ready as well.
  int index;
} ReadBinItem_rcr;

extern __thread HashStructure ** allHashStructures;

HashStructure ** rcr_createMasterHashTableArray(int maxSize);
HashStructure* rcr_createHashtable();
WriteBinItem_rcr* rcr_createWriteBinItem( HashStructure* htable );
ReadBinItem_rcr* rcr_createReadBinItem( HashStructure* htable );
inline int rcr_generateKey(void * ptr);

//Method signatures are not in their final form since I have still not decided what is the optimum amount of data
//to store in each entry.

void RESOLVE(SESEcommon *record, bitvt mask);
int rcr_WRITEBINCASE(HashStructure *T, int key, SESEcommon *task, struct rcrRecord *rcrrec, int index);
int rcr_READBINCASE(HashStructure *T, int key, SESEcommon * task, struct rcrRecord *rcrrec, int index);

int rcr_WTWRITEBINCASE(HashStructure *T, int key, SESEcommon *task, struct rcrRecord *rcrrec, int index);
int rcr_WTREADBINCASE(HashStructure *T, int key, SESEcommon * task, struct rcrRecord *rcrrec, int index);
int rcr_TAILREADCASE(HashStructure *T, BinItem_rcr *val, BinItem_rcr *bintail, int key, SESEcommon * task, struct rcrRecord *rcrrec, int index);
void rcr_TAILWRITECASE(HashStructure *T, BinItem_rcr *val, BinItem_rcr *bintail, int key, SESEcommon * task, struct rcrRecord *rcrrec, int index);
void rcr_RETIREHASHTABLE(HashStructure *T, SESEcommon *task, int key, BinItem_rcr *b);
#endif
