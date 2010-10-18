#ifndef HASHSTRUCTURE_H_
#define HASHSTRUCTURE_H_

#include "mlp_runtime.h"
#include "WaitingQueue.h"

#define ITEM_NOT_AT_FRONT_OF_WAITINGQ 3
#define TRAVERSER_FINISHED 2


//Note READEFFECT = READBIN and WRITEEFFECT=WRITEBIN. They mean the same thing
//but are named differently for clarity in code.
#define READEFFECT 0
#define WRITEEFFECT 1
#define WAITINGQUEUENOTE 2

#define READBIN 0
#define WRITEBIN 1

#define READY 1
#define NOTREADY 0

#define TRUE 1
#define FALSE 0

#define RNUMBINS 64
#define RNUMREAD 64
#define RNUMRENTRY 256
#define RH_MASK (RNUMBINS<<4)-1

//Note: put resolved things at the end and unresolved at the front.
typedef struct BinItem_rcr {
  int total;
  int status;
  int type;
  //TODO keep track of record ptr here
  struct BinItem_rcr * next;
} BinItem_rcr;

typedef struct trackerElement {
  BinItem_rcr * item;
  struct trackerElement * next;
} TrackerElement;

typedef struct tracker {
  TrackerElement * head;
  TrackerElement * tail;
} VariableTracker;

//TODO more closely tie this in with Jim's stuff
struct genericObjectStruct {
        int type;
        int oid;
        int allocsite;
        int ___cachedCode___;
        int ___cachedHash___;
};


typedef struct BinElement_rcr {
  BinItem_rcr * head;
  BinItem_rcr * tail;
} BinElement_rcr;


typedef struct Hashtable_rcr {
  BinElement_rcr array[RNUMBINS];
  WaitingQueueBin * waitingQueue;
} HashStructure;

//Todo this is a clone of REntry, remove data fields as necessary
typedef struct entry_rcr{
  //fields to handle next item.
  struct Hashtable_rcr* hashtable;
  BinItem_rcr* binitem; //stores binItem so we can get access to the next ptr in the queue

  //fields to help resume traverser
  struct genericObjectStruct * resumePtr;
//  int allocsite; //not needed since we can get it form ptr later
  int traverserID;
  SESEcommon * task;
  struct genericObjectStruct * heaproot;
} TraverserData;

typedef struct WriteBinItem_rcr {
  BinItem_rcr item;
  TraverserData val;
} WriteBinItem_rcr;

typedef struct ReadBinItem_rcr {
  BinItem_rcr item;
  TraverserData array[RNUMREAD];
  //We don't need a head index since if the item before it was freed, then all these would be considered ready as well.
  int index;
  
} ReadBinItem_rcr;

typedef struct WQNote_rcr {
  BinItem_rcr item;
  int allocSiteID;
} WaitingQueueNote;

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

int rcr_WRITEBINCASE(HashStructure *T, void *ptr, int traverserID, SESEcommon *task, void *heaproot);
int rcr_READBINCASE(HashStructure *T, void *ptr, int traverserID, SESEcommon * task, void *heaproot);
int rcr_TAILREADCASE(HashStructure *T, void * ptr, BinItem_rcr *val, BinItem_rcr *bintail, int key, int traverserID, SESEcommon * task, void *heaproot);
void rcr_TAILWRITECASE(HashStructure *T, void *ptr, BinItem_rcr *val, BinItem_rcr *bintail, int key, int traverserID, SESEcommon * task, void *heaproot);
int rcr_REMOVETABLEITEM(HashStructure* table, void * ptr, int traverserID, SESEcommon *task, void * heaproot);

#endif
