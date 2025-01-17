#ifndef _DSTM_H_
#define _DSTM_H_

#ifdef MAC
#define MSG_NOSIGNAL 0
#endif

#define CFENCE   asm volatile ("" ::: "memory");
/***********************************************************
 *       Macros
 **********************************************************/
#define GET_SITEID(x) ((int *)(x))
#define GET_NTUPLES(x)  ((int *)(x + sizeof(int)))
#define GET_PTR_OID(x)  ((unsigned int *)(x + 2*sizeof(int)))
#define GET_PTR_EOFF(x,n) ((short *)(x + 2*sizeof(int) + (n*sizeof(unsigned int))))
#define GET_PTR_ARRYFLD(x,n) ((short *)(x + 2*sizeof(int) + (n*sizeof(unsigned int)) + (n*sizeof(short))))

#define ENDEBUG(s) { printf("Inside %s()\n", s); fflush(stdout); }
#define EXDEBUG(s) {printf("Outside %s()\n", s); fflush(stdout); }
/*****************************************
 *  Coordinator Messages
 ***************************************/
#define READ_REQUEST            1
#define READ_MULT_REQUEST       2
#define MOVE_REQUEST            3
#define MOVE_MULT_REQUEST       4
#define TRANS_REQUEST           5
#define TRANS_ABORT             6
#define TRANS_COMMIT            7
#define TRANS_PREFETCH          8
#define TRANS_ABORT_BUT_RETRY_COMMIT_WITH_RELOCATING    9

/*********************************
 * Participant Messages
 *******************************/
#define OBJECT_FOUND                    10
#define OBJECT_NOT_FOUND                11
#define OBJECTS_FOUND                   12
#define OBJECTS_NOT_FOUND               13
#define TRANS_AGREE                     17
#define TRANS_DISAGREE                  18
#define TRANS_AGREE_BUT_MISSING_OBJECTS 19
#define TRANS_SOFT_ABORT                20
#define TRANS_SUCESSFUL                 21
#define TRANS_PREFETCH_RESPONSE         22
#define START_REMOTE_THREAD             23
#define THREAD_NOTIFY_REQUEST           24
#define THREAD_NOTIFY_RESPONSE          25
#define TRANS_UNSUCESSFUL               26
#define CLOSE_CONNECTION  27

//Max number of objects
#define MAX_OBJECTS  20
//Transaction id per machine
#define TID_LEN 20
#define LISTEN_PORT 2156
#define UDP_PORT 2158
//Prefetch tuning paramters
//#define RETRYINTERVAL  20 //N (For Em3d, SOR, Moldyn benchmarks)
//#define SHUTDOWNINTERVAL  3 //M
#define RETRYINTERVAL 1  //N  (For MatrixMultiply, 2DFFT, 2DConv benchmarks)
#define SHUTDOWNINTERVAL 75 //M
#define NUM_TRY_TO_COMMIT 2
#define MEM_ALLOC_THRESHOLD 20485760 //20MB

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
#include "readstruct.h"
#ifdef ABORTREADERS
#include <setjmp.h>
#endif

//bit designations for status field of objheader
#define DIRTY 0x01
#define NEW   0x02
#define LOCK  0x04
#define LOCAL  0x08

/*******Global statistics *********/
extern int numprefetchsites;

/***********************************
 * Global Variables for statistics
 **********************************/
#ifdef TRANSSTATS
extern int numTransCommit;
extern int numTransAbort;
extern int nSoftAbort;
extern int nSoftAbortAbort;
extern int nSoftAbortCommit;
#endif

double idForTimeDelay;           /* TODO Remove, necessary to get time delay for starting transRequest for this id */
int transCount;                  /* TODO Remove, necessary to the transaction id */

#ifdef COMPILER

#include "structdefs.h"

typedef struct objheader {
  threadlist_t *notifylist;
  unsigned short version;
  unsigned short rcount;
} objheader_t;

#define OID(x) \
  (*((unsigned int *)&((struct ___Object___ *)((unsigned int) x + sizeof(objheader_t)))->___nextobject___))

#define COMPOID(x) \
  ((void*)((((void *) x )!=NULL) ? (*((unsigned int *)&((struct ___Object___ *) x)->___nextobject___)) : 0))

#define STATUS(x) \
  *((unsigned int *) &(((struct ___Object___ *)((unsigned int) x + sizeof(objheader_t)))->___localcopy___))

#define STATUSPTR(x) \
  ((unsigned int *) &(((struct ___Object___ *)((unsigned int) x + sizeof(objheader_t)))->___localcopy___))

#define TYPE(x) \
  ((struct ___Object___ *)((unsigned int) x + sizeof(objheader_t)))->type

#define GETSIZE(size, x) { \
    int type=TYPE(x); \
    if (type<NUMCLASSES) { \
      size=classsize[type]; \
    } else { \
      size=classsize[type]*((struct ArrayObject *)&((objheader_t *)x)[1])->___length___+sizeof(struct ArrayObject); \
    } \
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
  unsigned int size;       //this many bytes are allocated after this header
  void *top;
  struct objstr *next;
  struct objstr *prev;
} objstr_t;

typedef struct oidmidpair {
  unsigned int oid;
  unsigned int mid;
} oidmidpair_t;

// Structure is a shared structure that keeps track of responses from the participants
typedef struct thread_response {
  char rcv_status;
} thread_response_t;

// Structure that holds  fixed data to be sent along with TRANS_REQUEST
typedef struct fixed_data {
  char control;                 /* control message */
  char trans_id[TID_LEN];       /* transaction id */
  int mcount;                   /* participant count */
  unsigned int numread;         /* no of objects read */
  unsigned int nummod;                  /* no of objects modified */
  unsigned int numcreated;              /* no of objects created */
  int sum_bytes;                /* total bytes of modified objects in a transaction */
} fixed_data_t;

/* Structure that holds trans request information for each participant */
typedef struct trans_req_data {
  fixed_data_t f;               /* Holds first few fixed bytes of data sent during TRANS_REQUEST protcol*/
  unsigned int *listmid;        /* Pointer to array holding list of participants */
  char *objread;                /* Pointer to array holding oid and version number of objects that are only read */
  unsigned int *oidmod;         /* Pointer to array holding oids of objects that are modified */
  unsigned int *oidcreated;     /* Pointer to array holding oids of objects that are newly created */
} trans_req_data_t;

/* Structure that holds information of objects that are not found in the participant
 * and objs locked within a transaction during commit process */
typedef struct trans_commit_data {
  unsigned int *objlocked;      /* Pointer to array holding oids of objects locked inside a transaction */
  unsigned int *objnotfound;    /* Pointer to array holding oids of objects not found on the participant machine */
  unsigned int *objvernotmatch;    /* Pointer to array holding oids whose version doesn't match on the participant machine */
  void *modptr;                 /* Pointer to the address in the mainobject store of the participant that holds all modified objects */
  int numlocked;                /* no of objects locked */
  int numnotfound;              /* no of objects not found */
  int numvernotmatch;           /* no of objects whose version doesn't match */
} trans_commit_data_t;


#define PRINT_TID(PTR) printf("DEBUG -> %x %d\n", PTR->mid, PTR->thread_id);

/* Initialize main object store and lookup tables, start server thread. */
int dstmInit(void);
void send_data(int fd, void *buf, int buflen);
void recv_data(int fd, void *buf, int buflen);
int recv_data_errorcode(int fd, void *buf, int buflen);
void recv_data_buf(int fd, struct readstruct *, void *, int);

/* Prototypes for object header */
unsigned int getNewOID(void);
/* end object header */

/* Prototypes for object store */
objstr_t *objstrCreate(unsigned int size); //size in bytes
void objstrDelete(objstr_t *store); //traverse and free entire list
void *objstrAlloc(objstr_t **store, unsigned int size); //size in bytes
void clearObjStore(); // TODO:currently only clears the prefetch cache object store
/* end object store */

/* Prototypes for server portion */
void *dstmListen(void *);
int startlistening();
void *dstmAccept(void *);
int readClientReq(trans_commit_data_t *, int, struct readstruct * readbuffer);
int processClientReq(fixed_data_t *, trans_commit_data_t *,unsigned int *, char *, void *, unsigned int *, int, struct readstruct *);
char handleTransReq(fixed_data_t *, trans_commit_data_t *, unsigned int *, char *, void *, int);
char decideCtrlMessage(fixed_data_t *, trans_commit_data_t *, int *, int *, int *, int *, int *, void *, unsigned int *, unsigned int *, int);
int transCommitProcess(void *, unsigned int *, unsigned int *, int, int, int);
void processReqNotify(unsigned int numoid, unsigned int *oid, unsigned short *version, unsigned int mid, unsigned int threadid);
char getCommitCountForObjMod(unsigned int *, unsigned int *, unsigned int *, int *,
                             int *, int *, int *, int *, int *, int *, char *, unsigned int, unsigned short);
char getCommitCountForObjRead(unsigned int *, unsigned int *, unsigned int *, int *, int *, int *, int *, int *,
                              int *, int *, char *, unsigned int, unsigned short);

void procRestObjs(char *, char *, int, int, int, unsigned int *, unsigned int *, int *, int *, int *, int *);
void processVerNoMatch(unsigned int *, unsigned int *, int *, int *, int *, int *, unsigned int, unsigned short);

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
void transStart();
//#define TRANSREAD(x,y,z) /* Use "z" which is program point at which TRANSREAD is generated, use this as transRead2(inputvalue,z) */
#define TRANSREAD(x,y) { \
    unsigned int inputvalue; \
    if ((inputvalue=(unsigned int)y)==0) x=NULL;                                                                                                                                                                                                                                                 \
         else { \
           chashlistnode_t * cnodetmp=&c_table[(inputvalue&c_mask)>>1];    \
           do { \
             if (cnodetmp->key==inputvalue) {x=(void *)& ((objheader_t*)cnodetmp->val)[1]; break; } \
             cnodetmp=cnodetmp->next; \
             if (cnodetmp==NULL) {x=(void *)transRead2(inputvalue); asm volatile ("" : "=m" (c_table),"=m" (c_mask)); break; } \
           } while(1); \
         }}

__attribute__((pure)) objheader_t *transRead(unsigned int);
__attribute__((pure)) objheader_t *transRead2(unsigned int);
//__attribute__((pure)) objheader_t *transRead2(unsigned int, char ptr[]);
objheader_t *transCreateObj(unsigned int); //returns oid header
int transCommit(); //return 0 if successful
void *transRequest(void *);     //the C routine that the thread will execute when TRANS_REQUEST begins
char decideResponse(char *, char *,  int); // Coordinator decides what response to send to the participant
void *getRemoteObj(unsigned int, unsigned int); // returns object header from main object store after object is copied into it from remote machine
void handleLocalReq(trans_req_data_t *, trans_commit_data_t *, char *);
int transComProcess(trans_req_data_t *, trans_commit_data_t *);
void doLocalProcess(char, trans_req_data_t *tdata, trans_commit_data_t *);
int transAbortProcess(trans_commit_data_t *);
void transAbort();
void sendPrefetchResponse(int sd, char *control, char *sendbuffer, int *size);
void prefetch(int, int, unsigned int *, unsigned short *, short*);
void *transPrefetch(void *);
void *mcqProcess(void *);
prefetchpile_t *foundLocal(char *, int, int); // returns node with prefetch elements(oids, offsets, siteid)
int lookupObject(unsigned int * oid, short offset, int *);
int checkoid(unsigned int oid, int isLastOffset);
int transPrefetchProcess(int **, short);
void sendPrefetchReq(prefetchpile_t*, int, int);
void sendPrefetchReqnew(prefetchpile_t*, int);
int getPrefetchResponse(int, struct readstruct *);
unsigned short getObjType(unsigned int oid);
int startRemoteThread(unsigned int oid, unsigned int mid);
plistnode_t *pInsert(plistnode_t *pile, objheader_t *headeraddr, unsigned int mid, int num_objs);
void commitCountForObjRead(char *, unsigned int *, unsigned int *, int *, int *, int *, int *, int *, unsigned int, unsigned short);
void commitCountForObjMod(char *, unsigned int *, unsigned int *, int *, int *, int *, int *, int *, unsigned int, unsigned short);

long long myrdtsc(void);
/* Sends notification request for thread join, if sucessful returns 0 else returns -1 */
int reqNotify(unsigned int *oidarry, unsigned short *versionarry, unsigned int numoid);
void threadNotify(unsigned int oid, unsigned short version, unsigned int tid);
int notifyAll(threadlist_t **head, unsigned int oid, unsigned int version);

/* Internal functions from signal.c */
int getthreadid();
double getMax(double *array, int size);
double getMin(double *array, int size);
double getfast(int siteid, int threadid);
double getslowest(int siteid, int threadid);
double getavg(int siteid, int threadid);
double getavgperthd(int siteid, int threadid);
double avgfast(int siteid, int threadid);
double avgslow(int siteid, int threadid);
void bubblesort();
void swap(double *e1, double *e2);
double avgofthreads(int siteid, int threadid);

/* end transactions */

#include "trans.h"
#endif
