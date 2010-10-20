#ifndef HASHSTRUCTURE_H_
#define HASHSTRUCTURE_H_

#include "mlp_runtime.h"
//#include "WaitingQueue.h"

#define ITEM_NOT_AT_FRONT_OF_WAITINGQ 3
#define TRAVERSER_FINISHED 2
#define bitvt unsigned long long

//Note READEFFECT = READBIN and WRITEEFFECT=WRITEBIN. They mean the same thing
//but are named differently for clarity in code.
#define READEFFECT 0
#define WRITEEFFECT 1
#define WAITINGQUEUENOTE 2

#define READBIN 0
#define WRITEBIN 1
#define BINMASK 1
#define PARENTBIN 1

#define SPECREADY 3
#define SPECNOTREADY 2
#define READY 1
#define NOTREADY 0
#define READYMASK 1

#define TRUE 1
#define FALSE 0

#define RNUMBINS 64
#define RNUMREAD 64
#define RNUMRENTRY 256
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

extern HashStructure ** allHashStructures;

void rcr_createMasterHashTableArray(int maxSize); //temporary
HashStructure* rcr_createHashtable(int sizeofWaitingQueue);
WriteBinItem_rcr* rcr_createWriteBinItem();
ReadBinItem_rcr* rcr_createReadBinItem();
int rcr_isReadBinItem(BinItem_rcr* b);
int rcr_isWriteBinItem(BinItem_rcr* b);
inline int rcr_generateKey(void * ptr);

//Method signatures are not in their final form since I have still not decided what is the optimum amount of data
//to store in each entry.

int rcr_WRITEBINCASE(HashStructure *T, void *ptr, SESEcommon *task, int index);
int rcr_READBINCASE(HashStructure *T, void *ptr, SESEcommon * task, int index);
int rcr_TAILREADCASE(HashStructure *T, void * ptr, BinItem_rcr *val, BinItem_rcr *bintail, int key, SESEcommon * task, int index);
void rcr_TAILWRITECASE(HashStructure *T, void *ptr, BinItem_rcr *val, BinItem_rcr *bintail, int key, SESEcommon * task, int index);

#endif
