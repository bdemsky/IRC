#ifndef _THREADNOTIFY_H_
#define _THREADNOTIFY_H_

#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>

#define N_LOADFACTOR 0.5
#define N_HASH_SIZE 20

//Structure to notify object of which other objects/threads are waiting on it
typedef struct threadlist {
	unsigned int threadid;
	unsigned int mid;
	struct threadlist *next;
} threadlist_t;

//Structure for objects involved in wait-notify call
typedef struct notifydata {
	unsigned int numoid;    /* Number of oids on which we are waiting for updated notification */
	unsigned int threadid;  /* The threadid that is waiting for  update notification response*/
	unsigned int *oidarry;  /* Pointer to array of oids that this threadid is waiting on*/
	unsigned short *versionarry;/* Pointer to array of versions of the oids that we are waiting on */
	pthread_cond_t threadcond; /* Cond variable associated with each threadid that needs to be signaled*/
	pthread_mutex_t threadnotify;
}notifydata_t;

typedef struct notifylistnode {
	unsigned int threadid;
	notifydata_t *ndata; 
	struct notifylistnode *next;
} notifylistnode_t;

typedef struct notifyhashtable {
	notifylistnode_t *table; //Points to beginning of hash table
	unsigned int size;
	unsigned int numelements;
	float loadfactor;
	pthread_mutex_t locktable; //Lock for the hashtable
} notifyhashtable_t;

threadlist_t *insNode(threadlist_t *head, unsigned int threadid, unsigned int mid); //Inserts nodes for one object that 
									   //needs to send notification to threads waiting on it
void display(threadlist_t *head);// Displays linked list of nodes for one object
unsigned int notifyhashCreate(unsigned int size, float loadfactor); //returns 1 if hashtable creation is not successful
unsigned int notifyhashFunction(unsigned int tid); //returns index in the hash table 
unsigned int notifyhashInsert(unsigned int tid, notifydata_t *ndata); //returns 1 if insert not successful
notifydata_t *notifyhashSearch(unsigned int tid); //returns pointer to notify data, NULL if not found
unsigned int notifyhashRemove(unsigned int tid); //returns 1 if not successful
unsigned int notifyhashResize(unsigned int newsize);

#endif
