#ifndef RUNTIME_ARCH
#define RUNTIME_ARCH

#include "structdefs.h"
#include "mem.h"
#include "checkpoint.h"
#include "Queue.h"
#include "SimpleHash.h"
#include "GenericHashtable.h"
#include <sys/select.h>
#include <sys/types.h>
#include <sys/mman.h>
#include <string.h>
#include <signal.h>
#include <assert.h>
#include <errno.h>
// use POSIX message queue
// for each core, its message queue named as
// /msgqueue_corenum
#include <mqueue.h>
#include <sys/stat.h>

static struct RuntimeHash* locktbl;
struct thread_data {
  int corenum;
  int argc;
  char** argv;
  int numsendobjs;
  int numreceiveobjs;
};
struct thread_data thread_data_array[NUMCORES];
mqd_t mqd[NUMCORES];
static pthread_key_t key;
static pthread_rwlock_t rwlock_tbl;
static pthread_rwlock_t rwlock_init;

#define BAMBOO_NUM_OF_CORE (pthread_getspecific(key))
#define BAMBOO_GET_NUM_OF_CORE() (pthread_getspecific(key))

/* This function updates the flag for object ptr.  It or's the flag
   with the or mask and and's it with the andmask. */

void flagbody(struct ___Object___ *ptr, int flag, struct parameterwrapper ** queues, int length, bool isnew);

void run(void * arg);

#endif // #ifndef RUNTIME_ARCH
