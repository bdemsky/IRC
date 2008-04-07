#ifndef _SOCKPOOL_H_
#define _SOCKPOOL_H_

#include "dstm.h"

typedef int SpinLock;
typedef struct socknode {
    int sd;
    unsigned int mid;
    struct socknode *next;
} socknode_t;

typedef struct sockPoolHashTable {
    socknode_t **table;
    socknode_t *inuse;
    unsigned int size;
    unsigned int numelements;
    float loadfactor;
    SpinLock mylock;
} sockPoolHashTable_t;

sockPoolHashTable_t *createSockPool(sockPoolHashTable_t *, unsigned int, float);
int getSock(sockPoolHashTable_t *, unsigned int);
int getSockWithLock(sockPoolHashTable_t *, unsigned int);
int freeSock(sockPoolHashTable_t *, unsigned int, int);
int freeSockWithLock(sockPoolHashTable_t *, unsigned int, int);
void insToList(sockPoolHashTable_t *, socknode_t *);
void insToListWithLock(sockPoolHashTable_t *, socknode_t *);
int createNewSocket(unsigned int);
int CompareAndSwap(int *, int, int);
void InitLock(SpinLock *);
void Lock (SpinLock *);
void UnLock (SpinLock *);

#if 0
/************************************************
 * Array Implementation data structures 
 ***********************************************/
#define MAX_CONN_PER_MACHINE    10
typedef struct sock_pool {
    unsigned int mid;
    int *sd;
    char *inuse;
} sock_pool_t;

sock_pool_t *initSockPool(unsigned int *, int);
int getSock(sock_pool_t *, unsigned int);
int freeSock(sock_pool_t *, int);
#endif

#endif
