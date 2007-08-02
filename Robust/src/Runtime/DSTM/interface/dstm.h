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

typedef struct objheader {
	unsigned int oid;
	unsigned short type;
	unsigned short version;
	unsigned short rcount;
	char status;
} objheader_t;

typedef struct objstr {
	unsigned int size; //this many bytes are allocated after this header
	void *top;
	struct objstr *next;
} objstr_t;

typedef struct transrecord {
	objstr_t *cache;
	chashtable_t *lookupTable;
} transrecord_t;
// Structure that keeps track of responses from the participants
typedef struct thread_response {
	char rcv_status;
}thread_response_t;

// Structure that holds  fixed data sizes to be sent along with TRANS_REQUEST
typedef struct fixed_data {
	char control;
	char trans_id[TID_LEN];	
	int mcount;		// Machine count
	short numread;		// Number of objects read
	short nummod;		// Number of objects modified
	int sum_bytes;	// Total bytes modified
}fixed_data_t;

// Structure that holds  variable data sizes per machine participant
typedef struct trans_req_data {
	fixed_data_t f;
	unsigned int *listmid;
	char *objread;
	unsigned int *oidmod;
}trans_req_data_t;

// Structure passed to dstmAcceptinfo() on server side to complete TRANS_COMMIT process 
typedef struct trans_commit_data{
	unsigned int *objmod;
	unsigned int *objlocked;
	unsigned int *objnotfound;
	void *modptr;
	int nummod;
	int numlocked;
	int numnotfound;
}trans_commit_data_t;


#define PRINT_TID(PTR) printf("DEBUG -> %x %d\n", PTR->mid, PTR->thread_id);
//structure for passing multiple arguments to thread
typedef struct thread_data_array {
	int thread_id;
	int mid;    
	int pilecount;
	trans_req_data_t *buffer;
	thread_response_t *recvmsg;//shared datastructure to keep track of the control message receiv
	pthread_cond_t *threshold; //threshhold for waking up a thread
	pthread_mutex_t *lock;    //lock the count variable
	int *count;             //variable to count responses of TRANS_REQUEST protocol from all participants
	char *replyctrl; 	//shared ctrl message that stores the reply to be sent, filled by decideResp
	char *replyretry;	//shared variable to find out if we need retry (TRANS_COMMIT case) 
	transrecord_t *rec;	// To send modified objects
} thread_data_array_t;


//Structure for passing arguments to the local m/c thread
typedef struct local_thread_data_array {
	thread_data_array_t *tdata;
	trans_commit_data_t *transinfo; //Required for trans commit process
} local_thread_data_array_t;

// Structure to save information about an oid necesaary for the decideControl()
typedef struct objinfo {
	unsigned int oid;
	int poss_val; //Status of object(locked but version matches, version mismatch, oid not present in machine etc) 
}objinfo_t;

//Structure for members within prefetch tuples
typedef struct member {
	short offset;
	short index;
	struct member *next;
}trans_member_t;

/*
//Structure that holds the compiler generated prefetch data
typedef struct compprefetchdata {
transrecord_t *record;
} compprefetchdata_t;
*/

/* Initialize main object store and lookup tables, start server thread. */
int dstmInit(void);

/* Prototypes for object header */
unsigned int getNewOID(void);
unsigned int objSize(objheader_t *object);
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
int processClientReq(fixed_data_t *, trans_commit_data_t *,unsigned int *, char *, void *, int);
char handleTransReq(fixed_data_t *, trans_commit_data_t *, unsigned int *, char *, void *, int);
int decideCtrlMessage(fixed_data_t *, trans_commit_data_t *, int *, int *, int *, int *, int *, void *, unsigned int *, unsigned int *, unsigned int *, int);
int transCommitProcess(trans_commit_data_t *, int);
/* end server portion */

/* Prototypes for transactions */
void randomdelay(void);
transrecord_t *transStart();
objheader_t *transRead(transrecord_t *, unsigned int);
objheader_t *transCreateObj(transrecord_t *, unsigned short); //returns oid
int transCommit(transrecord_t *record); //return 0 if successful
void *transRequest(void *);	//the C routine that the thread will execute when TRANS_REQUEST begins
void *handleLocalReq(void *);	//the C routine that the local m/c thread will execute 
int decideResponse(thread_data_array_t *);// Coordinator decides what response to send to the participant
char sendResponse(thread_data_array_t *, int); //Sends control message back to Participants
void *getRemoteObj(transrecord_t *, unsigned int, unsigned int);
int transAbortProcess(void *, unsigned int *, int, int, int);
int transComProcess(trans_commit_data_t *);
void prefetch(int, unsigned int *, short *, short*);
void *transPrefetch(void *);
void *mcqProcess(void *);
void checkPrefetchTuples(prefetchqelem_t *);
prefetchpile_t *foundLocal(prefetchqelem_t *);
prefetchpile_t *makePreGroups(prefetchqelem_t *, int *);
void checkPreCache(prefetchqelem_t *, int *, int, int, unsigned int, int, int, int);
int transPrefetchProcess(transrecord_t *, int **, short);
void *sendPrefetchReq(prefetchpile_t*, int);
/* end transactions */
#endif
