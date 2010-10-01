#ifndef HASHSTRUCTURE_H_
#define HASHSTRUCTURE_H_

#include "mlp_runtime.h"
#include "WaitingQueue.h"

#define ITEM_NOT_AT_FRONT_OF_WAITINGQ = 3;
#define TRAVERSER_FINISHED = 2;
#define NUM_WAITING_Q_ITEMS 20

#define READEFFECT 0
#define WRITEEFFECT 1

#define READBIN 0
#define WRITEBIN 1

#define READY 1
#define NOTREADY 0

#define TRUE 1
#define FALSE 0

#define NUMBINS 64
#define NUMREAD 64
#define NUMITEMS 64
#define NUMRENTRY 256
#define H_MASK (NUMBINS<<4)-1

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
  BinElement_rcr array[NUMBINS];
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
  TraverserData array[NUMREAD];
  int index;
} ReadBinItem_rcr;

#endif
