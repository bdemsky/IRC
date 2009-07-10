#include "threadnotify.h"

notifyhashtable_t nlookup; //Global hash table

/* This function creates a new node in the linked list of threads waiting
 * for an update notification from a particular object.
 * This takes in the head of the linked list and inserts the new node to it */
threadlist_t *insNode(threadlist_t *head, unsigned int threadid, unsigned int mid) {
  threadlist_t *ptr;
  if(head == NULL) {
    head = malloc(sizeof(threadlist_t));
    head->threadid = threadid;
    head->mid = mid;
    head->next = NULL;
  } else {
    ptr = malloc(sizeof(threadlist_t));
    ptr->threadid = threadid;
    ptr->mid = mid;
    ptr->next = head;
    head = ptr;
  }
  return head;
}

/* This function displays the linked list of threads waiting on update notification
 * from an object */
void display(threadlist_t *head) {
  threadlist_t *ptr;
  if(head == NULL) {
    printf("No thread is waiting\n");
    return;
  } else {
    while(head != NULL) {
      ptr = head;
      printf("The threadid waiting is = %d\n", ptr->threadid);
      printf("The mid on which thread present = %d\n", ptr->mid);
      head = ptr->next;
    }
  }
}

/* This function creates a new hash table that stores a mapping between the threadid and
 * a pointer to the thread notify data */
unsigned int notifyhashCreate(unsigned int size, float loadfactor) {
  notifylistnode_t *nodes = calloc(size, sizeof(notifylistnode_t));
  nlookup.table = nodes;
  nlookup.size = size;
  nlookup.numelements = 0; // Initial number of elements in the hash
  nlookup.loadfactor = loadfactor;
  //Initialize the pthread_mutex variable
  pthread_mutex_init(&nlookup.locktable, NULL);
  return 0;
}

// Assign to tids to bins inside hash table
unsigned int notifyhashFunction(unsigned int tid) {
  return( tid % (nlookup.size));
}

// Insert pointer to the notify data and threadid mapping into the hash table
unsigned int notifyhashInsert(unsigned int tid, notifydata_t *ndata) {
  unsigned int newsize;
  int index;
  notifylistnode_t *ptr, *node, *tmp;
  int isFound = 0;

  if (nlookup.numelements > (nlookup.loadfactor * nlookup.size)) {
    //Resize Table
    newsize = 2 * nlookup.size + 1;
    pthread_mutex_lock(&nlookup.locktable);
    notifyhashResize(newsize);
    pthread_mutex_unlock(&nlookup.locktable);
  }
  ptr = nlookup.table;
  index = notifyhashFunction(tid);
  pthread_mutex_lock(&nlookup.locktable);
  if(ptr[index].next == NULL && ptr[index].threadid == 0) {
    // Insert at the first position in the hashtable
    ptr[index].threadid = tid;
    ptr[index].ndata = ndata;
  } else {
    tmp = &ptr[index];
    while(tmp != NULL) {
      if(tmp->threadid == tid) {
	isFound = 1;
	tmp->ndata = ndata;
      }
      tmp = tmp->next;
    }
    if(!isFound) {
      if ((node = calloc(1, sizeof(notifylistnode_t))) == NULL) {
	printf("Calloc error %s, %d\n", __FILE__, __LINE__);
	pthread_mutex_unlock(&nlookup.locktable);
	return 1;
      }
      node->threadid = tid;
      node->ndata = ndata;
      node->next = ptr[index].next;
      ptr[index].next = node;
    }
  }
  pthread_mutex_unlock(&nlookup.locktable);

  return 0;
}

// Return pointer to thread notify data for a given threadid in the hash table
notifydata_t  *notifyhashSearch(unsigned int tid) {
  // Address of the beginning of hash table
  notifylistnode_t *ptr = nlookup.table;
  int index = notifyhashFunction(tid);
  pthread_mutex_lock(&nlookup.locktable);
  notifylistnode_t * node = &ptr[index];
  while(node != NULL) {
    if(node->threadid == tid) {
      pthread_mutex_unlock(&nlookup.locktable);
      return node->ndata;
    }
    node = node->next;
  }
  pthread_mutex_unlock(&nlookup.locktable);
  return NULL;
}

// Remove an entry from the hash table
unsigned int notifyhashRemove(unsigned int tid) {
  notifylistnode_t *curr, *prev, *node;

  notifylistnode_t *ptr = nlookup.table;
  int index = notifyhashFunction(tid);

  pthread_mutex_lock(&nlookup.locktable);
  for (curr = &ptr[index]; curr != NULL; curr = curr->next) {
    if (curr->threadid == tid) {         // Find a match in the hash table
      nlookup.numelements--;  // Decrement the number of elements in the global hashtable
      if ((curr == &ptr[index]) && (curr->next == NULL)) {  // Delete the first item inside the hashtable with no linked list of notifylistnode_t
	curr->threadid = 0;
	curr->ndata = NULL;
      } else if ((curr == &ptr[index]) && (curr->next != NULL)) { //Delete the first bin item with a linked list of notifylistnode_t  connected
	curr->threadid = curr->next->threadid;
	curr->ndata = curr->next->ndata;
	node = curr->next;
	curr->next = curr->next->next;
	free(node);
      } else {                                          // Regular delete from linked listed
	prev->next = curr->next;
	free(curr);
      }
      pthread_mutex_unlock(&nlookup.locktable);
      return 0;
    }
    prev = curr;
  }
  pthread_mutex_unlock(&nlookup.locktable);
  return 1;
}

// Resize table
unsigned int notifyhashResize(unsigned int newsize) {
  notifylistnode_t *node, *ptr, *curr, *next;   // curr and next keep track of the current and the next notifyhashlistnodes in a linked list
  unsigned int oldsize;
  int isfirst;    // Keeps track of the first element in the notifylistnode_t for each bin in hashtable
  int i,index;
  notifylistnode_t *newnode;

  ptr = nlookup.table;
  oldsize = nlookup.size;

  if((node = calloc(newsize, sizeof(notifylistnode_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return 1;
  }

  nlookup.table = node;                 //Update the global hashtable upon resize()
  nlookup.size = newsize;
  nlookup.numelements = 0;

  for(i = 0; i < oldsize; i++) {                        //Outer loop for each bin in hash table
    curr = &ptr[i];
    isfirst = 1;
    while (curr != NULL) {                      //Inner loop to go through linked lists
      if (curr->threadid == 0) {                //Exit inner loop if there the first element for a given bin/index is NULL
	break;                  //threadid = threadcond =0 for element if not present within the hash table
      }
      next = curr->next;
      index = notifyhashFunction(curr->threadid);
#ifdef DEBUG
      printf("DEBUG(resize) -> index = %d, threadid = %d\n", index, curr->threadid);
#endif
      // Insert into the new table
      if(nlookup.table[index].next == NULL && nlookup.table[index].threadid == 0) {
	nlookup.table[index].threadid = curr->threadid;
	nlookup.table[index].ndata = curr->ndata;
	nlookup.numelements++;
      } else {
	if((newnode = calloc(1, sizeof(notifylistnode_t))) == NULL) {
	  printf("Calloc error %s, %d\n", __FILE__, __LINE__);
	  return 1;
	}
	newnode->threadid = curr->threadid;
	newnode->ndata = curr->ndata;
	newnode->next = nlookup.table[index].next;
	nlookup.table[index].next = newnode;
	nlookup.numelements++;
      }

      //free the linked list of notifylistnode_t if not the first element in the hash table
      if (isfirst != 1) {
	free(curr);
      }

      isfirst = 0;
      curr = next;
    }
  }

  free(ptr);            //Free the memory of the old hash table
  ptr = NULL;
  return 0;
}
