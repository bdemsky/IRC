#include "stmlookup.h"
#include "strings.h"

__thread chashlistnode_t *c_table;
__thread chashlistnode_t *c_list;
__thread unsigned int c_size;
__thread unsigned INTPTR c_mask;
__thread unsigned int c_numelements;
__thread unsigned int c_threshold;
__thread double c_loadfactor;

void t_chashCreate(unsigned int size, double loadfactor) {
  chashtable_t *ctable;
  chashlistnode_t *nodes;
  int i;

  // Allocate space for the hash table
  

  c_table = calloc(size, sizeof(chashlistnode_t));
  c_loadfactor = loadfactor;
  c_size = size;
  c_threshold=size*loadfactor;
  c_mask = (size << 3)-1;
  c_numelements = 0; // Initial number of elements in the hash
  c_list=NULL;
}

void t_chashreset() {
  chashlistnode_t *ptr = c_table;
  int i;
  if (c_numelements<(c_size>>3)) {
    chashlistnode_t *top=&ptr[c_size];
    chashlistnode_t *tmpptr=c_list;
    while(tmpptr!=NULL) {
      chashlistnode_t *next=tmpptr->lnext;
      if (tmpptr>=ptr&&tmpptr<top) {
	//zero in list
	tmpptr->key=0;
	tmpptr->next=NULL;
      } else {
	free(tmpptr);
      }
      tmpptr=next;
    }
  } else {
    for(i=0 ; i<c_size ; i++) {
      chashlistnode_t * curr = ptr[i].next;
      while(curr!=NULL) {
	chashlistnode_t * next = curr->next;
	free(curr);
	curr=next;
      }
    }
    bzero(c_table, sizeof(chashlistnode_t)*c_size);
  }
  c_numelements = 0;
  c_list=NULL;
}

//Store objects and their pointers into hash
void t_chashInsert(void * key, void *val) {
  chashlistnode_t *ptr;


  if(c_numelements > (c_threshold)) {
    //Resize
    unsigned int newsize = c_size << 1;
    t_chashResize(newsize);
  }

  ptr = &c_table[(((unsigned INTPTR)key)&c_mask)>>3];
  c_numelements++;

  if(ptr->key==0) {
    ptr->key=key;
    ptr->val=val;
    ptr->lnext=c_list;
    c_list=ptr;
  } else { // Insert in the beginning of linked list
    chashlistnode_t * node = calloc(1, sizeof(chashlistnode_t));
    node->key = key;
    node->val = val;
    node->next = ptr->next;
    ptr->next=node;
    node->lnext=c_list;
    c_list=node;
  }
}

// Search for an address for a given oid
INLINE void * t_chashSearch(void * key) {
  //REMOVE HASH FUNCTION CALL TO MAKE SURE IT IS INLINED HERE
  chashlistnode_t *node = &c_table[(((unsigned INTPTR)key) & c_mask)>>3];

  do {
    if(node->key == key) {
      return node->val;
    }
    node = node->next;
  } while(node != NULL);

  return NULL;
}
unsigned int t_chashResize(unsigned int newsize) {
  chashlistnode_t *node, *ptr, *curr;    // curr and next keep track of the current and the next chashlistnodes in a linked list
  unsigned int oldsize;
  int isfirst;    // Keeps track of the first element in the chashlistnode_t for each bin in hashtable
  unsigned int i,index;
  unsigned int mask;
  
  ptr = c_table;
  oldsize = c_size;
  c_list=NULL;

  if((node = calloc(newsize, sizeof(chashlistnode_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return 1;
  }

  c_table = node;          //Update the global hashtable upon resize()
  c_size = newsize;
  c_threshold = newsize * c_loadfactor;
  mask=c_mask = (newsize << 3)-1;

  for(i = 0; i < oldsize; i++) {                        //Outer loop for each bin in hash table
    curr = &ptr[i];
    isfirst = 1;
    do {                      //Inner loop to go through linked lists
      void * key;
      chashlistnode_t *tmp,*next;
      
      if ((key=curr->key) == 0) {             //Exit inner loop if there the first element is 0
	break;                  //key = val =0 for element if not present within the hash table
      }
      next = curr->next;
      index = (((unsigned INTPTR)key) & mask) >>3;
      tmp=&node[index];
      // Insert into the new table
      if(tmp->key == 0) {
	tmp->key = curr->key;
	tmp->val = curr->val;
	tmp->lnext=c_list;
	c_list=tmp;
	if (!isfirst) {
	  free(curr);
	}
      }/*
	 NOTE:  Add this case if you change this...
	 This case currently never happens because of the way things rehash....
	 else if (isfirst) {
	chashlistnode_t *newnode= calloc(1, sizeof(chashlistnode_t));
	newnode->key = curr->key;
	newnode->val = curr->val;
	newnode->next = tmp->next;
	tmp->next=newnode;
	} */
      else {
	curr->next=tmp->next;
	tmp->next=curr;
	curr->lnext=c_list;
	c_list=curr;
      }

      isfirst = 0;
      curr = next;
    } while(curr!=NULL);
  }

  free(ptr);            //Free the memory of the old hash table
  return 0;
}

//Delete the entire hash table
void t_chashDelete() {
  int i;
  chashlistnode_t *ptr = c_table;

  for(i=0 ; i<c_size ; i++) {
    chashlistnode_t * curr = ptr[i].next;
    while(curr!=NULL) {
      chashlistnode_t * next = curr->next;
      free(curr);
      curr=next;
    }
  }
  free(ptr);
  c_table=NULL;
  c_list=NULL;
}
