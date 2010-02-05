#ifndef SANDBOX_H
#define SANDBOX_H

#include <setjmp.h>
#include <signal.h>
#include "dstm.h"
#include "altmlookup.h"
#include <execinfo.h>
#include "readstruct.h"
#include "dsmlock.h"

extern __thread chashlistnode_t *c_table;
extern __thread unsigned int c_size;
extern __thread unsigned int c_numelements;
extern __thread jmp_buf aborttrans;
extern __thread int abortenabled;
extern __thread int* counter_reset_pointer;
extern __thread int transaction_check_counter;


/* Global Variables */
#define CHECK_OBJECTS   51
#define OBJ_INCONSISTENT 52
#define OBJ_CONSISTENT 53
#define LOW_CHECK_FREQUENCY 1000000
#define HIGH_CHECK_FREQUENCY 100000
int numNode; //variable to keep track of the length of the linked list of objects

typedef struct elem {
  unsigned int oid;
  unsigned short version;
} elem_t;

typedef struct nodeElem {
  unsigned int mid;
  unsigned int numread;
  unsigned int nummod;
  elem_t *objread;
  elem_t *objmod;
  struct nodeElem *next;
} nodeElem_t;

typedef struct objData {
  char control;
  unsigned int numread;
  unsigned int nummod;
} objData_t;


int checktrans();
void errorhandler(int sig, struct sigcontext ctx);
nodeElem_t * makehead(unsigned int numelements);
void deletehead(nodeElem_t *head);
nodeElem_t * createList(nodeElem_t *, objheader_t *, unsigned int, unsigned int);
int verify(nodeElem_t *pile);
void print_trace();
void checkObjVersion(struct readstruct*, int, unsigned int, unsigned int);

#endif
