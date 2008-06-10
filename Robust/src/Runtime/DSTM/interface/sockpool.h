#ifndef _SOCKPOOL_H_
#define _SOCKPOOL_H_

#include "dstm.h"
#include "ip.h"

int test_and_set(volatile unsigned int *addr);
void UnLock(volatile unsigned int *addr);

typedef struct socknode {
    int sd;
    unsigned int mid;
    struct socknode *next;
} socknode_t;

typedef struct sockPoolHashTable {
    socknode_t **table;
    socknode_t *inuse;
    unsigned int size;
    volatile unsigned int mylock;
} sockPoolHashTable_t;

sockPoolHashTable_t *createSockPool(sockPoolHashTable_t *, unsigned int);
int getSock(sockPoolHashTable_t *, unsigned int);
int getSock2(sockPoolHashTable_t *, unsigned int);
int getSockWithLock(sockPoolHashTable_t *, unsigned int);
void freeSock(sockPoolHashTable_t *, unsigned int, int);
void freeSockWithLock(sockPoolHashTable_t *, unsigned int, int);
void insToList(sockPoolHashTable_t *, socknode_t *);
void insToListWithLock(sockPoolHashTable_t *, socknode_t *);
int createNewSocket(unsigned int);

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
