#ifndef _DSTM_H_
#define _DSTM_H_

#ifdef MAC
#define MSG_NOSIGNAL 0
#endif

/***********************************************************
 *       Macros
 **********************************************************/
#define GET_SITEID(x) ((int *)(x))
#define GET_NTUPLES(x) 	((int *)(x + sizeof(int)))
#define GET_PTR_OID(x) 	((unsigned int *)(x + 2*sizeof(int)))
#define GET_PTR_EOFF(x,n) ((short *)(x + 2*sizeof(int) + (n*sizeof(unsigned int))))
#define GET_PTR_ARRYFLD(x,n) ((short *)(x + 2*sizeof(int) + (n*sizeof(unsigned int)) + (n*sizeof(short))))
#define ENDEBUG(s) { printf("Inside %s()\n", s); fflush(stdout);}
#define EXDEBUG(s) {printf("Outside %s()\n", s); fflush(stdout);}
/*****************************************
 *  Coordinator Messages
 ***************************************/
#define READ_REQUEST 		1
#define READ_MULT_REQUEST 	2
#define MOVE_REQUEST 		3
#define MOVE_MULT_REQUEST	4
#define	TRANS_REQUEST		5
#define	TRANS_ABORT		6
#define TRANS_COMMIT 		7
#define TRANS_PREFETCH		8
#define TRANS_ABORT_BUT_RETRY_COMMIT_WITH_RELOCATING	9

/*********************************
 * Participant Messages
 *******************************/
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
#define THREAD_NOTIFY_REQUEST		24
#define THREAD_NOTIFY_RESPONSE		25
#define TRANS_UNSUCESSFUL		26
#define CLOSE_CONNECTION  27

//Max number of objects 
#define MAX_OBJECTS  20
#define DEFAULT_OBJ_STORE_SIZE 1048510 //1MB
//Transaction id per machine
#define TID_LEN 20
#define LISTEN_PORT 2156
#define UDP_PORT 2158
//Prefetch tuning paramters
#define RETRYINTERVAL  7 //N
#define SHUTDOWNINTERVAL  4  //M

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <pthread.h>
#include "clookup.h"
#include "queue.h"
#include "mcpileq.h"
#include "threadnotify.h"
#include <arpa/inet.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <sys/time.h>
#include <netdb.h>
#include <netinet/in.h>
#include <sys/types.h>
#include <unistd.h>
#include <errno.h>
#include <time.h>
#include "sockpool.h"
#include <signal.h>
#include "plookup.h"
#include "dsmdebug.h"

//bit designations for status field of objheader
#define DIRTY 0x01
#define NEW   0x02
#define LOCK  0x04
#define LOCAL  0x08

/*******Global statistics *********/
extern int numprefetchsites;

#ifdef COMPILER

#include "structdefs.h"

typedef struct objheader {
	threadlist_t *notifylist;
	unsigned short version;
	unsigned short rcount;
} objheader_t;

#define OID(x)\
    (*((unsigned int *)&((struct ___Object___ *)((unsigned int) x + sizeof(objheader_t)))->___nextobject___))

#define COMPOID(x)\
    (*((unsigned int *)&((struct ___Object___ *) x)->___nextobject___))

#define STATUS(x)\
	 *((unsigned int *) &(((struct ___Object___ *)((unsigned int) x + sizeof(objheader_t)))->___localcopy___))

#define STATUSPTR(x)\
	 ((unsigned int *) &(((struct ___Object___ *)((unsigned int) x + sizeof(objheader_t)))->___localcopy___))

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
	threadlist_t *notifylist;
	unsigned int oid;
	unsigned short type;
	unsigned short version;
	unsigned short rcount;
	char status;
} objheader_t;

#define OID(x) x->oid
#define TYPE(x) x->type
#define STATUS(x) x->status
#define STATUSPTR(x) &x->status
#define GETSIZE(size, x) size=classsize[TYPE(x)]
#endif

typedef struct objstr {
	unsigned int size; //this many bytes are allocated after this header
	void *top;
	struct objstr *next;
} objstr_t;

typedef struct oidmidpair {
    unsigned int oid;
    unsigned int mid;
} oidmidpair_t;

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
  unsigned int numread;		/* no of objects read */
  unsigned int nummod;			/* no of objects modified */
  unsigned int numcreated;		/* no of objects created */
  int sum_bytes;		/* total bytes of modified objects in a transaction */
} fixed_data_t;

/* Structure that holds trans request information for each participant */
typedef struct trans_req_data {
  fixed_data_t f; 		/* Holds first few fixed bytes of data sent during TRANS_REQUEST protcol*/
  unsigned int *listmid;	/* Pointer to array holding list of participants */
  char *objread;		/* Pointer to array holding oid and version number of objects that are only read */ 
  unsigned int *oidmod;		/* Pointer to array holding oids of objects that are modified */
  unsigned int *oidcreated;	/* Pointer to array holding oids of objects that are newly created */
} trans_req_data_t;		

/* Structure that holds information of objects that are not found in the participant
 * and objs locked within a transaction during commit process */
typedef struct trans_commit_data{
  unsigned int *objlocked;	/* Pointer to array holding oids of objects locked inside a transaction */
  unsigned int *objnotfound;    /* Pointer to array holding oids of objects not found on the participant machine */
  unsigned int *objvernotmatch;    /* Pointer to array holding oids whose version doesn't match on the participant machine */
  void *modptr;			/* Pointer to the address in the mainobject store of the participant that holds all modified objects */
  int numlocked;		/* no of objects locked */
  int numnotfound;		/* no of objects not found */
  int numvernotmatch;		/* no of objects whose version doesn't match */
} trans_commit_data_t;


#define PRINT_TID(PTR) printf("DEBUG -> %x %d\n", PTR->mid, PTR->thread_id);
/* Structure for passing multiple arguments to a thread
 * spawned to process each transaction on a machine */
typedef struct thread_data_array {
  int thread_id;	
  int mid;    
  trans_req_data_t *buffer;	/* Holds trans request information sent to a participant, based on threadid */  
  thread_response_t *recvmsg;	/* Shared datastructure to keep track of the participants response to a trans request */
  pthread_cond_t *threshold;    /* Condition var to waking up a thread */
  pthread_mutex_t *lock;    	/* Lock for counting participants response */
  int *count;             	/* Variable to count responses from all participants to the TRANS_REQUEST protocol */
  char *replyctrl; 		/* Shared ctrl message that stores the reply to be sent to participants, filled by decideResponse() */
  char *replyretry;		/* Shared variable that keep track if coordinator needs retry */
  transrecord_t *rec;		/* To send modified objects */
  plistnode_t *pilehead;   /*  Shared variable, ptr to the head of the machine piles for the transaction rec */
} thread_data_array_t;


//Structure for passing arguments to the local m/c thread
typedef struct local_thread_data_array {
	thread_data_array_t *tdata;	/* Holds all the arguments send to a thread that is spawned when transaction commits */ 
	trans_commit_data_t *transinfo; /* Holds information of objects locked and not found in the participant */ 
} local_thread_data_array_t;

//Structure to store mid and socketid information
typedef struct midSocketInfo {
	unsigned int mid;		/* To communicate with mid use sockid in this data structure */
	int sockid;
} midSocketInfo_t;

/* Initialize main object store and lookup tables, start server thread. */
int dstmInit(void);
void send_data(int fd, void *buf, int buflen);
void recv_data(int fd, void *buf, int buflen);
int recv_data_errorcode(int fd, void *buf, int buflen);

/* Prototypes for object header */
unsigned int getNewOID(void);
/* end object header */

/* Prototypes for object store */
objstr_t *objstrCreate(unsigned int size); //size in bytes
void objstrDelete(objstr_t *store); //traverse and free entire list
void *objstrAlloc(objstr_t *store, unsigned int size); //size in bytes
void clearObjStore(); // TODO:currently only clears the prefetch cache object store
/* end object store */

/* Prototypes for server portion */
void *dstmListen(void *);
int startlistening();
void *dstmAccept(void *);
int readClientReq(trans_commit_data_t *, int);
int processClientReq(fixed_data_t *, trans_commit_data_t *,unsigned int *, char *, void *, unsigned int *, int);
char handleTransReq(fixed_data_t *, trans_commit_data_t *, unsigned int *, char *, void *, int);
char decideCtrlMessage(fixed_data_t *, trans_commit_data_t *, int *, int *, int *, int *, int *, void *, unsigned int *, unsigned int *, int);
int transCommitProcess(void *, unsigned int *, unsigned int *, int, int, int);
void processReqNotify(unsigned int numoid, unsigned int *oid, unsigned short *version, unsigned int mid, unsigned int threadid);
/* end server portion */

/* Prototypes for transactions */
/* Function called at beginning. Passes in the first parameter. */
/* Returns 1 if this thread should run the main process */

int dstmStartup(const char *);
void transInit();
int processConfigFile();
void addHost(unsigned int);
void mapObjMethod(unsigned short);

void randomdelay();
transrecord_t *transStart();
objheader_t *transRead(transrecord_t *, unsigned int);
objheader_t *transCreateObj(transrecord_t *, unsigned int); //returns oid header
int transCommit(transrecord_t *record); //return 0 if successful
void *transRequest(void *);	//the C routine that the thread will execute when TRANS_REQUEST begins
void decideResponse(thread_data_array_t *);// Coordinator decides what response to send to the participant
char sendResponse(thread_data_array_t *, int); //Sends control message back to Participants
void *getRemoteObj(transrecord_t *, unsigned int, unsigned int);// returns object header from main object store after object is copied into it from remote machine
void *handleLocalReq(void *);//handles Local requests 
int transComProcess(local_thread_data_array_t *);
int transAbortProcess(local_thread_data_array_t *);
void transAbort(transrecord_t *trans);
void sendPrefetchResponse(int sd, char *control, char *sendbuffer, int *size);
void prefetch(int, int, unsigned int *, unsigned short *, short*);
void *transPrefetch(void *);
void *mcqProcess(void *);
prefetchpile_t *foundLocal(char *);// returns node with prefetch elements(oids, offsets)
int lookupObject(unsigned int * oid, short offset);
int transPrefetchProcess(transrecord_t *, int **, short);
void sendPrefetchReq(prefetchpile_t*, int);
void sendPrefetchReqnew(prefetchpile_t*, int);
int getPrefetchResponse(int);
unsigned short getObjType(unsigned int oid);
int startRemoteThread(unsigned int oid, unsigned int mid);
plistnode_t *pInsert(plistnode_t *pile, objheader_t *headeraddr, unsigned int mid, int num_objs);

/* Sends notification request for thread join, if sucessful returns 0 else returns -1 */
int reqNotify(unsigned int *oidarry, unsigned short *versionarry, unsigned int numoid);
void threadNotify(unsigned int oid, unsigned short version, unsigned int tid);
int notifyAll(threadlist_t **head, unsigned int oid, unsigned int version);

/* end transactions */
#endif
