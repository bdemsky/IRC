#ifndef _QUEUE_H_
#define _QUEUE_H_

#include<stdio.h>
#include<stdlib.h>
#include<pthread.h>
#include<string.h>
#include "dstm.h"

void queueInit(void);
void * getmemory(int size);
void movehead(int size);
void * gettail();
void inctail();
void predealloc();
#endif
