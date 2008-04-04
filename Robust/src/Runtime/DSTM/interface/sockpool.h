#ifndef _SOCKPOOL_H_
#define _SOCKPOOL_H_

#include "dstm.h"

#define LOADFACTOR 0.5

typedef int SpinLock;

typedef struct socknode {
    int sd;
    unsigned int mid;
    struct socknode *next;
} socknode_t;

typedef struct sockPoolHashTable {
    socknode_t **table;
    socknode_t **inuse;
    unsigned int size;
    unsigned int numelements;
    float loadfactor;
    SpinLock mylock;
} sockPoolHashTable_t;

int createSockPool(unsigned int, float);
int getSock(unsigned int);
int freeSock(unsigned int, int);
int deleteSockpool(sockPoolHashTable_t *);
int insToList(socknode_t *);
int createNewSocket(unsigned int);
int CompareAndSwap(int *, int, int);
void InitLock(SpinLock *);
void Lock (SpinLock *);
void UnLock (SpinLock *);

#endif
