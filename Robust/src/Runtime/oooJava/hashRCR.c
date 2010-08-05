#include "HashRCR.h"
#include "strings.h"
#include "tm.h"

//Smallest Object Size with 1 ptr is 32bytes on on 64-bit machine and 24bytes on 32-bit machine
#ifdef BIT64
#define _truncate_ >>5
#else
#define _truncate_ /24
#endif


__thread dchashlistnode_t *dc_c_table;
__thread dchashlistnode_t *dc_c_list;
__thread dcliststruct_t *dc_c_structs;
__thread unsigned int dc_c_size;
__thread unsigned INTPTR dc_c_mask;
__thread unsigned int dc_c_numelements;
__thread unsigned int dc_c_threshold;
__thread double dc_c_loadfactor;

void hashRCRCreate(unsigned int size, double loadfactor) {
  // Allocate space for the hash table

  dc_c_table = calloc(size, sizeof(dchashlistnode_t));
  dc_c_loadfactor = loadfactor;
  dc_c_size = size;
  dc_c_threshold=size*loadfactor;
  dc_c_mask = (size << 4)-1;
  dc_c_structs=calloc(1, sizeof(dcliststruct_t));
  dc_c_numelements = 0; // Initial number of elements in the hash
  dc_c_list=NULL;
}

void hashRCRreset() {
  dchashlistnode_t *ptr = dc_c_table;

  if (dc_c_numelements<(dc_c_size>>4)) {
    dchashlistnode_t *top=&ptr[dc_c_size];
    dchashlistnode_t *tmpptr=dc_c_list;
    while(tmpptr!=NULL) {
      dchashlistnode_t *next=tmpptr->lnext;
      if (tmpptr>=ptr&&tmpptr<top) {
	//zero in list
	tmpptr->key=NULL;
	tmpptr->next=NULL;
      }
      tmpptr=next;
    }
  } else {
    bzero(dc_c_table, sizeof(dchashlistnode_t)*dc_c_size);
  }
  while(dc_c_structs->next!=NULL) {
    dcliststruct_t *next=dc_c_structs->next;
    free(dc_c_structs);
    dc_c_structs=next;
  }
  dc_c_structs->num = 0;
  dc_c_numelements = 0;
  dc_c_list=NULL;
}

//Store objects and their pointers into hash
//1 = add success
//0 = object already exists / Add failed
int hashRCRInsert(void * key) {
  dchashlistnode_t *ptr;

  if (unlikely(key==NULL)) {
    return 0;
  }

  if(unlikely(dc_c_numelements > dc_c_threshold)) {
    //Resize
    unsigned int newsize = dc_c_size << 1;
    hashRCRResize(newsize);
  }
  ptr = &dc_c_table[(((unsigned INTPTR)key _truncate_ )&dc_c_mask)>>4];
  if(likely(ptr->key==0)) {
    ptr->key=key;
    ptr->lnext=dc_c_list;
    dc_c_list=ptr;
    dc_c_numelements++;
  } else { // Insert in the beginning of linked list
    dchashlistnode_t * node;
    dchashlistnode_t *search=ptr;
    
    //make sure it isn't here
    do {
      if(search->key == key) {
	return 0;
      }
      search=search->next;
    } while(search != NULL);

    dc_c_numelements++;    
    if (dc_c_structs->num<NUMDCLIST) {
      node=&dc_c_structs->array[dc_c_structs->num];
      dc_c_structs->num++;
    } else {
      //get new list
      dcliststruct_t *tcl=calloc(1,sizeof(dcliststruct_t));
      tcl->next=dc_c_structs;
      dc_c_structs=tcl;
      node=&tcl->array[0];
      tcl->num=1;
    }
    node->key = key;
    node->next = ptr->next;
    ptr->next=node;
    node->lnext=dc_c_list;
    dc_c_list=node;
  }

  return 1;
}


unsigned int hashRCRResize(unsigned int newsize) {
  dchashlistnode_t *node, *ptr, *curr;    // curr and next keep track of the current and the next chashlistnodes in a linked list
  unsigned int oldsize;
  int isfirst;    // Keeps track of the first element in the chashlistnode_t for each bin in hashtable
  unsigned int i,index;
  unsigned int mask;

  ptr = dc_c_table;
  oldsize = dc_c_size;
  dc_c_list=NULL;

  if((node = calloc(newsize, sizeof(dchashlistnode_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return 1;
  }

  dc_c_table = node;          //Update the global hashtable upon resize()
  dc_c_size = newsize;
  dc_c_threshold = newsize * dc_c_loadfactor;
  mask=dc_c_mask = (newsize << 4)-1;

  for(i = 0; i < oldsize; i++) {                        //Outer loop for each bin in hash table
    curr = &ptr[i];
    isfirst = 1;
    do {                      //Inner loop to go through linked lists
      void * key;
      dchashlistnode_t *tmp,*next;

      if ((key=curr->key) == 0) {             //Exit inner loop if there the first element is 0
	break;                  //key = val =0 for element if not present within the hash table
      }

      index = (((unsigned INTPTR)key _truncate_ ) & mask) >>4;
      tmp=&node[index];
      next = curr->next;
      // Insert into the new table
      if(tmp->key == 0) {
	tmp->key = key;
	tmp->lnext=dc_c_list;
	dc_c_list=tmp;
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
	curr->lnext=dc_c_list;
	dc_c_list=curr;
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
  dcliststruct_t *ptr=dc_c_structs;
  while(ptr!=NULL) {
    dcliststruct_t *next=ptr->next;
    free(ptr);
    ptr=next;
  }
  free(dc_c_table);
  dc_c_table=NULL;
  dc_c_structs=NULL;
  dc_c_list=NULL;
}

// Search for an address for a given Address
INLINE int hashRCRSearch(void * key) {
  //REMOVE HASH FUNCTION CALL TO MAKE SURE IT IS INLINED HERE
  dchashlistnode_t *node = &dc_c_table[(((unsigned INTPTR)key _truncate_) & dc_c_mask)>>4];
  
  do {
    if(node->key == key) {
      return 1;
    }
    node = node->next;
  } while(node != NULL);

  return 0;
}
