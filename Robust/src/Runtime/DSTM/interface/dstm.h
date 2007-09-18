#ifndef _DSTM_H_
#define _DSTM_H_

#ifdef MAC
#define MSG_NOSIGNAL 0
#endif

#define GET_NTUPLES(x) 	((int *)(x + sizeof(prefetchqelem_t)))
#define GET_PTR_OID(x) 	((unsigned int *)(x + sizeof(prefetchqelem_t) + sizeof(int)))
#define GET_PTR_EOFF(x,n) ((short *)(x + sizeof(prefetchqelem_t) + sizeof(int) + (n*sizeof(unsigned int))))
#define GET_PTR_ARRYFLD(x,n) ((short *)(x + sizeof(prefetchqelem_t) + sizeof(int) + (n*sizeof(unsigned int)) + (n*sizeof(short))))


//Coordinator Messages
#define READ_REQUEST 		1
#define READ_MULT_REQUEST 	2
#define MOVE_REQUEST 		3
#define MOVE_MULT_REQUEST	4
#define	TRANS_REQUEST		5
#define	TRANS_ABORT		6
#define TRANS_COMMIT 		7
#define TRANS_PREFETCH		8
#define TRANS_ABORT_BUT_RETRY_COMMIT_WITH_RELOCATING	9

//Participant Messages
#define OBJECT_FOUND			10
#define OBJECT_NOT_FOUND		11
#define OBJECTS_FOUND 			12
#define OBJECTS_NOT_FOUND		13
#define TRANS_AGREE 			17
#define TRANS_DISAGREE			18
#define TRANS_AGREE_BUT_MISSING_OBJECTS	19
#define TRANS_SOFT_ABORT		20
#define TRANS_SUCESSFUL			21
#define TRANS_PREFETCH_RESPONSE		22
#define START_REMOTE_THREAD		23

//Control bits for status of objects in Machine pile
#define OBJ_LOCKED_BUT_VERSION_MATCH	14
#define OBJ_UNLOCK_BUT_VERSION_MATCH	15
#define VERSION_NO_MATCH		16

//Max number of objects 
#define MAX_OBJECTS  20


#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <pthread.h>
#include "clookup.h"
#include "queue.h"
#include "mcpileq.h"

#define DEFAULT_OBJ_STORE_SIZE 1048510 //1MB
#define TID_LEN 20
//bit designations for status field of objheader
#define DIRTY 0x01
#define NEW   0x02
#define LOCK  0x04
#define LOCAL  0x08

#ifdef COMPILER

#include "structdefs.h"

typedef struct objheader {
	unsigned short version;
	unsigned short rcount;
} objheader_t;

#define OID(x)\
    (*((unsigned int *)&((struct ___Object___ *)((unsigned int) x + sizeof(objheader_t)))->___nextobject___))

#define COMPOID(x)\
    (*((unsigned int *)&((struct ___Object___ *) x)->___nextobject___))

#define STATUS(x)\
	 *((unsigned int *) &(((struct ___Object___ *)((unsigned int) x + sizeof(objheader_t)))->___localcopy___))

#define TYPE(x)\
        ((struct ___Object___ *)((unsigned int) x + sizeof(objheader_t)))->type

#define GETSIZE(size, x) {\
  int type=TYPE(x);\
  if (type<NUMCLASSES) {\
    size=classsize[type];\
  } else {\
    size=classsize[type]*((struct ArrayObject *)&((objheader_t *)x)[1])->___length___+sizeof(struct ArrayObject);\
  }\
}

#else

typedef struct objheader {
	unsigned int oid;
	unsigned short type;
	unsigned short version;
	unsigned short rcount;
	char status;
} objheader_t;

#define OID(x) x->oid
#define TYPE(x) x->type
#define STATUS(x) x->status
#define GETSIZE(size, x) size=classsize[TYPE(x)]
#endif


typedef struct objstr {
	unsigned int size; //this many bytes are allocated after this header
	void *top;
	struct objstr *next;
} objstr_t;

typedef struct transrecord {
  objstr_t *cache;
  chashtable_t *lookupTable;
#ifdef COMPILER
  struct ___Object___ * revertlist;
#endif
} transrecord_t;
// Structure is a shared structure that keeps track of responses from the participants
typedef struct thread_response {
  char rcv_status;
} thread_response_t;

// Structure that holds  fixed data to be sent along with TRANS_REQUEST
typedef struct fixed_data {
  char control;			/* control message */
  char trans_id[TID_LEN];	/* transaction id */
  int mcount;			/* participant count */
  short numread;		/* no of objects read */
  short nummod;			/* no of objects modified */
  short numcreated;		/* no of objects created */
  int sum_bytes;		/* total bytes of modified objects in a transaction */
} fixed_data_t;

/* Structure that holds trans request information for each participant */
typedef struct trans_req_data {
  fixed_data_t f; 		/* Holds first few fixed bytes of data sent during TRANS_REQUEST protcol*/
  unsigned int *listmid;	/* Pointer to array holding list of participants */
  char *objread;		/* Pointer to array holding oid and version number of objects that are only read */ 
  unsigned int *oidmod;		/* Pointer to array holding oids of objects that are modified */
  unsigned int *oidcreated;		/* Pointer to array holding oids of objects that are newly created */
} trans_req_data_t;		

/* Structure that holds information of objects that are not found in the participant
 * and objs locked within a transaction during commit process */
typedef struct trans_commit_data{
  unsigned int *objlocked;	/* Pointer to array holding oids of objects locked inside a transaction */
  unsigned int *objnotfound;    /* Pointer to array holding oids of objects not found on the participant machine */
  void *modptr;			/* Pointer to the address in the mainobject store of the participant that holds all modified objects */
  int numlocked;		/* no of objects locked */
  int numnotfound;		/* no of objects not found */
} trans_commit_data_t;


#define PRINT_TID(PTR) printf("DEBUG -> %x %d\n", PTR->mid, PTR->thread_id);
/* Structure for passing multiple arguments to a thread
 * spawned to process each transaction on a machine */
typedef struct thread_data_array {
  int thread_id;	
  int mid;    
  trans_req_data_t *buffer;	/* Holds trans request information sent to participants */  
  thread_response_t *recvmsg;	/* Shared datastructure to keep track of the participants response to a trans request */
  pthread_cond_t *threshold;    /* Condition var to waking up a thread */
  pthread_mutex_t *lock;    	/* Lock for counting participants response */
  int *count;             	/* Variable to count responses from all participants to the TRANS_REQUEST protocol */
  char *replyctrl; 		/* Shared ctrl message that stores the reply to be sent to participants, filled by decideResponse() */
  char *replyretry;		/* Shared variable that keep track if coordinator needs retry */
  transrecord_t *rec;		/* To send modified objects */
} thread_data_array_t;


//Structure for passing arguments to the local m/c thread
typedef struct local_thread_data_array {
	thread_data_array_t *tdata;	/* Holds all the arguments send to a thread that is spawned when transaction commits */ 
	trans_commit_data_t *transinfo; /* Holds information of objects locked and not found in the participant */ 
} local_thread_data_array_t;

//Structure for members within prefetch tuples
typedef struct member {
	short offset;		/* Holds offset of the ptr field */
	short index;		/* Holds the array index value */ 
	struct member *next;	
}trans_member_t;

/* Initialize main object store and lookup tables, start server thread. */
int dstmInit(void);

/* Prototypes for object header */
unsigned int getNewOID(void);
/* end object header */

/* Prototypes for object store */
objstr_t *objstrCreate(unsigned int size); //size in bytes
void objstrDelete(objstr_t *store); //traverse and free entire list
void *objstrAlloc(objstr_t *store, unsigned int size); //size in bytes
/* end object store */

/* Prototypes for server portion */
void *dstmListen();
void *dstmAccept(void *);
int readClientReq(trans_commit_data_t *, int);
int processClientReq(fixed_data_t *, trans_commit_data_t *,unsigned int *, char *, void *, unsigned int *, int);
char handleTransReq(fixed_data_t *, trans_commit_data_t *, unsigned int *, char *, void *, int);
int decideCtrlMessage(fixed_data_t *, trans_commit_data_t *, int *, int *, int *, int *, int *, void *, unsigned int *, unsigned int *, int);
//int transCommitProcess(trans_commit_data_t *, int);
int transCommitProcess(void *, unsigned int *, unsigned int *, int, int, int);
/* end server portion */

/* Prototypes for transactions */
/* Function called at beginning. Passes in the first parameter. */
/* Returns 1 if this thread should run the main process */

int dstmStartup(const char *);
void transInit();
int processConfigFile();
void addHost(unsigned int);
void mapObjMethod(unsigned short);

void randomdelay(void);
transrecord_t *transStart();
objheader_t *transRead(transrecord_t *, unsigned int);
objheader_t *transCreateObj(transrecord_t *, unsigned int); //returns oid
int transCommit(transrecord_t *record); //return 0 if successful
void *transRequest(void *);	//the C routine that the thread will execute when TRANS_REQUEST begins
void *handleLocalReq(void *);	//the C routine that the local m/c thread will execute 
int decideResponse(thread_data_array_t *);// Coordinator decides what response to send to the participant
char sendResponse(thread_data_array_t *, int); //Sends control message back to Participants
void *getRemoteObj(transrecord_t *, unsigned int, unsigned int);
int transAbortProcess(void *, unsigned int *, int, int);
int transComProcess(void*, unsigned int *, unsigned int *, unsigned int *, int, int, int);
void prefetch(int, unsigned int *, unsigned short *, short*);
void *transPrefetch(void *);
void *mcqProcess(void *);
void checkPrefetchTuples(prefetchqelem_t *);
prefetchpile_t *foundLocal(prefetchqelem_t *);
prefetchpile_t *makePreGroups(prefetchqelem_t *, int *);
void checkPreCache(prefetchqelem_t *, int *, int, int, unsigned int, int, int, int);
int transPrefetchProcess(transrecord_t *, int **, short);
void sendPrefetchReq(prefetchpile_t*, int);
void getPrefetchResponse(int, int);
unsigned short getObjType(unsigned int oid);
int startRemoteThread(unsigned int oid, unsigned int mid);
/* end transactions */
#endif
