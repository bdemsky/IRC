#ifndef _TTLIST_H_
#define _TTLIST_H_

#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>

/* for transaction flag */
#define DECISION_LOST -1
#define TRYING_TO_COMMIT 0

/* for machine flag */
#define TRANS_OK     3
#define TRANS_BEFORE 4
#define TRANS_AFTER  5

/*
   Status
   -1 - lost
   0  - trying to commit
   >0 - decision
*/


typedef struct trans_list_node {
  unsigned int transid;
  char decision;
  char status;
  struct trans_list_node *next;
} tlist_node_t;

typedef struct trans_list
{
  tlist_node_t *head;
  int size;
  int flag;
  pthread_mutex_t mutex;
} tlist_t;

// allocate tlist_t, return -1 if memory overflow
tlist_t* tlistCreate();
tlist_t* tlistDestroy(tlist_t*);

// return 0 if success, return -1 if fail
tlist_t* tlistInsertNode(tlist_t* transList,unsigned int transid,char decision,char status);
tlist_t* tlistInsertNode2(tlist_t* transList,tlist_node_t* tNode) ;

// remove node.
// return 0 if success, return -1 if fail
tlist_t* tlistRemove(tlist_t* transList,unsigned int transid);

// return tlist_t if success, return null if cannot find
tlist_node_t* tlistSearch(tlist_t* transList,unsigned int transid);

tlist_node_t* tlistToArray(tlist_t* transList,int* size);

// debugging purpose. print out all list
void tlistPrint(tlist_t* transList);


#endif
