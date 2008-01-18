#ifndef _THREADNOTIFY_H_
#define _THREADNOTIFY_H_

#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>

#define N_LOADFACTOR 0.75
#define N_HASH_SIZE 20

//Structure to notify object of which other objects/threads are waiting on it
typedef struct threadlist {
	unsigned int threadid;
	unsigned int mid;
	struct threadlist *next;
} threadlist_t;

typedef struct notifylistnode {
	unsigned int threadid;
	pthread_cond_t threadcond;
	struct notifylistnode *next;
} notifylistnode_t;

typedef struct notifyhashtable {
	notifylistnode_t *table; //points to beginning of hash table
	unsigned int size;
	unsigned int numelements;
	float loadfactor;
	pthread_mutex_t locktable;
} notifyhashtable_t;

void insNode(threadlist_t *head, unsigned int threadid, unsigned int mid);
void display(threadlist_t *head);
unsigned int notifyhashCreate(unsigned int size, float loadfactor);
unsigned int notifyhashFunction(unsigned int tid);
unsigned notifyhashInsert(unsigned int tid, pthread_cond_t threadcond);
pthread_cond_t notifyhashSearch(unsigned int tid); //returns val, NULL if not found
unsigned int notifyhashRemove(unsigned int tid); //returns -1 if not found
unsigned int notifyhashResize(unsigned int newsize);

#endif
