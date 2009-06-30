#include "stmlookup.h"
#include "strings.h"

__thread chashlistnode_t *c_table;
__thread chashlistnode_t *c_list;
__thread unsigned int c_size;
__thread unsigned INTPTR c_mask;
__thread unsigned int c_numelements;
__thread unsigned int c_threshold;
__thread double c_loadfactor;
__thread cliststruct_t *c_structs;

#ifdef DELAYCOMP
__thread chashlistnode_t *dc_c_table;
__thread chashlistnode_t *dc_c_list;
__thread unsigned int dc_c_size;
__thread unsigned INTPTR dc_c_mask;
__thread unsigned int dc_c_numelements;
__thread unsigned int dc_c_threshold;
__thread double dc_c_loadfactor;
__thread cliststruct_t *dc_c_structs;

void dc_t_chashCreate(unsigned int size, double loadfactor) {
  chashtable_t *ctable;
  chashlistnode_t *nodes;
  int i;

  // Allocate space for the hash table

  dc_c_table = calloc(size, sizeof(chashlistnode_t));
  dc_c_loadfactor = loadfactor;
  dc_c_size = size;
  dc_c_threshold=size*loadfactor;
  dc_c_mask = (size << 4)-1;
  dc_c_structs=calloc(1, sizeof(cliststruct_t));
  dc_c_numelements = 0; // Initial number of elements in the hash
  dc_c_list=NULL;
}

void dc_t_chashreset() {
  chashlistnode_t *ptr = dc_c_table;
  int i;

  if (dc_c_numelements<(dc_c_size>>4)) {
    chashlistnode_t *top=&ptr[dc_c_size];
    chashlistnode_t *tmpptr=dc_c_list;
    while(tmpptr!=NULL) {
      chashlistnode_t *next=tmpptr->lnext;
      if (tmpptr>=ptr&&tmpptr<top) {
	//zero in list
	tmpptr->key=0;
	tmpptr->next=NULL;
      }
      tmpptr=next;
    }
  } else {
    bzero(dc_c_table, sizeof(chashlistnode_t)*dc_c_size);
  }
  while(dc_c_structs->next!=NULL) {
    cliststruct_t *next=dc_c_structs->next;
    free(dc_c_structs);
    dc_c_structs=next;
  }
  dc_c_structs->num = 0;
  dc_c_numelements = 0;
  dc_c_list=NULL;
}

//Store objects and their pointers into hash
void dc_t_chashInsertOnce(void * key, void *val) {
  chashlistnode_t *ptr;

  if (key==NULL)
    return;

  if(dc_c_numelements > (dc_c_threshold)) {
    //Resize
    unsigned int newsize = dc_c_size << 1;
    dc_t_chashResize(newsize);
  }

  ptr = &dc_c_table[(((unsigned INTPTR)key)&dc_c_mask)>>4];

  if(ptr->key==0) {
    ptr->key=key;
    ptr->val=val;
    ptr->lnext=dc_c_list;
    dc_c_list=ptr;
    dc_c_numelements++;
  } else { // Insert in the beginning of linked list
    chashlistnode_t * node;
    chashlistnode_t *search=ptr;
    
    //make sure it isn't here
    do {
      if(search->key == key) {
	return;
      }
      search=search->next;
    } while(search != NULL);

    dc_c_numelements++;    
    if (dc_c_structs->num<NUMCLIST) {
      node=&dc_c_structs->array[dc_c_structs->num];
      dc_c_structs->num++;
    } else {
      //get new list
      cliststruct_t *tcl=calloc(1,sizeof(cliststruct_t));
      tcl->next=dc_c_structs;
      dc_c_structs=tcl;
      node=&tcl->array[0];
      tcl->num=1;
    }
    node->key = key;
    node->val = val;
    node->next = ptr->next;
    ptr->next=node;
    node->lnext=dc_c_list;
    dc_c_list=node;
  }
}

unsigned int dc_t_chashResize(unsigned int newsize) {
  chashlistnode_t *node, *ptr, *curr;    // curr and next keep track of the current and the next chashlistnodes in a linked list
  unsigned int oldsize;
  int isfirst;    // Keeps track of the first element in the chashlistnode_t for each bin in hashtable
  unsigned int i,index;
  unsigned int mask;

  ptr = dc_c_table;
  oldsize = dc_c_size;
  dc_c_list=NULL;

  if((node = calloc(newsize, sizeof(chashlistnode_t))) == NULL) {
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
      chashlistnode_t *tmp,*next;

      if ((key=curr->key) == 0) {             //Exit inner loop if there the first element is 0
	break;                  //key = val =0 for element if not present within the hash table
      }
      index = (((unsigned INTPTR)key) & mask) >>4;
      tmp=&node[index];
      next = curr->next;
      // Insert into the new table
      if(tmp->key == 0) {
	tmp->key = key;
	tmp->val = curr->val;
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
void dc_t_chashDelete() {
  int i;
  cliststruct_t *ptr=dc_c_structs;
  while(ptr!=NULL) {
    cliststruct_t *next=ptr->next;
    free(ptr);
    ptr=next;
  }
  free(dc_c_table);
  dc_c_table=NULL;
  dc_c_structs=NULL;
  dc_c_list=NULL;
}

// Search for an address for a given oid
INLINE void * dc_t_chashSearch(void * key) {
  //REMOVE HASH FUNCTION CALL TO MAKE SURE IT IS INLINED HERE
  chashlistnode_t *node = &dc_c_table[(((unsigned INTPTR)key) & dc_c_mask)>>4];
  
  do {
    if(node->key == key) {
      return node->val;
    }
    node = node->next;
  } while(node != NULL);

  return NULL;
}

#endif

void t_chashCreate(unsigned int size, double loadfactor) {
  chashtable_t *ctable;
  chashlistnode_t *nodes;
  int i;

  // Allocate space for the hash table


  c_table = calloc(size, sizeof(chashlistnode_t));
  c_loadfactor = loadfactor;
  c_size = size;
  c_threshold=size*loadfactor;
  c_mask = (size << 4)-1;
  c_structs=calloc(1, sizeof(cliststruct_t));
  c_numelements = 0; // Initial number of elements in the hash
  c_list=NULL;
}

void t_chashreset() {
  chashlistnode_t *ptr = c_table;
  int i;

  if (c_numelements<(c_size>>4)) {
    chashlistnode_t *top=&ptr[c_size];
    chashlistnode_t *tmpptr=c_list;
    while(tmpptr!=NULL) {
      chashlistnode_t *next=tmpptr->lnext;
      if (tmpptr>=ptr&&tmpptr<top) {
	//zero in list
	tmpptr->key=0;
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
void t_chashInsert(void * key, void *val) {
  chashlistnode_t *ptr;


  if(c_numelements > (c_threshold)) {
    //Resize
    unsigned int newsize = c_size << 1;
    t_chashResize(newsize);
  }

  ptr = &c_table[(((unsigned INTPTR)key)&c_mask)>>4];
  c_numelements++;

  if(ptr->key==0) {
    ptr->key=key;
    ptr->val=val;
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
  chashlistnode_t *node = &c_table[(((unsigned INTPTR)key) & c_mask)>>4];

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
  mask=c_mask = (newsize << 4)-1;

  for(i = 0; i < oldsize; i++) {                        //Outer loop for each bin in hash table
    curr = &ptr[i];
    isfirst = 1;
    do {                      //Inner loop to go through linked lists
      void * key;
      chashlistnode_t *tmp,*next;

      if ((key=curr->key) == 0) {             //Exit inner loop if there the first element is 0
	break;                  //key = val =0 for element if not present within the hash table
      }
      index = (((unsigned INTPTR)key) & mask) >>4;
      tmp=&node[index];
      next = curr->next;
      // Insert into the new table
      if(tmp->key == 0) {
	tmp->key = key;
	tmp->val = curr->val;
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
void t_chashDelete() {
  int i;
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
