#include "hashRCR.h"
#include "strings.h"
#include "tm.h"

//Smallest Object Size with 1 ptr is 32-bits on x64 and 24-bits on x86
//#ifdef BIT64
//#define _truncate_ >>5
//#else
//#define _truncate_ /24
//#endif

//TODO add __thread infront of all
 chashlistnode_t *c_table;
 chashlistnode_t *c_list;
 unsigned int c_size;
 unsigned INTPTR c_mask;
 unsigned int c_numelements;
 unsigned int c_threshold;
 double c_loadfactor;
 cliststruct_t *c_structs;

void hashRCRCreate(unsigned int size, double loadfactor) {
//  chashtable_t *ctable;
//  chashlistnode_t *nodes;
//  int i;

  // Allocate space for the hash table
  c_table = calloc(size, sizeof(chashlistnode_t));
  c_loadfactor = loadfactor;
  c_size = size;
  c_threshold=size*loadfactor;
#ifdef BIT64
  c_mask = ((size << 4)-1)&~(15UL);
#else
  c_mask = ((size << 4)-1)&~15;
#endif
  c_structs=calloc(1, sizeof(cliststruct_t));
  c_numelements = 0; // Initial number of elements in the hash
  c_list=NULL;
}

void hashRCRreset() {
  chashlistnode_t *ptr = c_table;
//  int i;

  if (c_numelements<(c_size>>4)) {
    chashlistnode_t *top=&ptr[c_size];
    chashlistnode_t *tmpptr=c_list;
    while(tmpptr!=NULL) {
      chashlistnode_t *next=tmpptr->lnext;
      if (tmpptr>=ptr&&tmpptr<top) {
	//zero in list
	tmpptr->keyAndVal=NULL;
	tmpptr->next=NULL;
      }
      tmpptr=next;
    }
  } else {
    bzero(c_table, sizeof(chashlistnode_t)*c_size);
  }
  while(c_structs->next!=NULL) {
    cliststruct_t *next=c_structs->next;
    free(c_structs);
    c_structs=next;
  }
  c_structs->num = 0;
  c_numelements = 0;
  c_list=NULL;
}

//Store objects and their pointers into hash

void hashRCRInsert(void * addrIn) {
  chashlistnode_t *ptr;


  if(c_numelements > (c_threshold)) {
    //Resize
    unsigned int newsize = c_size << 1;
    hashRCRResize(newsize);
  }

  ptr = &c_table[(((unsigned INTPTR)addrIn)&c_mask)>>4];
  c_numelements++;

  if(ptr->keyAndVal==0) {
    ptr->keyAndVal=addrIn;
    ptr->lnext=c_list;
    c_list=ptr;
  } else { // Insert in the beginning of linked list
    chashlistnode_t * node;
    if (c_structs->num<NUMCLIST) {
      node=&c_structs->array[c_structs->num];
      c_structs->num++;
    } else {
      //get new list
      cliststruct_t *tcl=calloc(1,sizeof(cliststruct_t));
      tcl->next=c_structs;
      c_structs=tcl;
      node=&tcl->array[0];
      tcl->num=1;
    }
    node->keyAndVal = addrIn;
    node->next = ptr->next;
    ptr->next=node;
    node->lnext=c_list;
    c_list=node;
  }
}

// Search for an address for a given oid
INLINE void * hashRCRSearch(void * key) {
  //REMOVE HASH FUNCTION CALL TO MAKE SURE IT IS INLINED HERE
  chashlistnode_t *node = &c_table[(((unsigned INTPTR)key) & c_mask)>>4];

  do {
    if(node->key == key) {
      return node->val;
    }
    node = node->next;
  } while(node != NULL);

  return NULL;
}

unsigned int hashRCRResize(unsigned int newsize) {
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
  mask=c_mask = (newsize << 4)-1;

  for(i = 0; i < oldsize; i++) {                        //Outer loop for each bin in hash table
    curr = &ptr[i];
    isfirst = 1;
    do {                      //Inner loop to go through linked lists
      void * key;
      chashlistnode_t *tmp,*next;

      if ((key=curr->keyAndVal) == 0) {             //Exit inner loop if there the first element is 0
	break;                  //key = val =0 for element if not present within the hash table
      }
      index = (((unsigned INTPTR)key) & mask) >>4;
      tmp=&node[index];
      next = curr->next;
      // Insert into the new table
      if(tmp->keyAndVal == 0) {
	tmp->keyAndVal = key;
//	tmp->val = curr->val;
	tmp->lnext=c_list;
	c_list=tmp;
      } /*
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
void hashRCRDelete() {
//  int i;
  cliststruct_t *ptr=c_structs;
  while(ptr!=NULL) {
    cliststruct_t *next=ptr->next;
    free(ptr);
    ptr=next;
  }
  free(c_table);
  c_table=NULL;
  c_structs=NULL;
  c_list=NULL;
}
