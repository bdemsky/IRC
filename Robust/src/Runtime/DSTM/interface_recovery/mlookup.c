#include "mlookup.h"

mhashtable_t mlookup;   //Global hash table

// Creates a machine lookup table with size =" size"
unsigned int mhashCreate(unsigned int size, double loadfactor) {
  mhashlistnode_t *nodes;
  // Allocate space for the hash table
  if((nodes = calloc(size, sizeof(mhashlistnode_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return 1;
  }

  mlookup.table = nodes;
  mlookup.size = size;
  mlookup.threshold=size*loadfactor;
  mlookup.mask = (size << 1) -1;
  mlookup.numelements = 0;       // Initial number of elements in the hash
  mlookup.loadfactor = loadfactor;
  //Initialize the pthread_mutex variable
  pthread_mutex_init(&mlookup.locktable, NULL);
  return 0;
}

// Assign to keys to bins inside hash table
unsigned int mhashFunction(unsigned int key) {
  return( key & mlookup.mask) >>1;
}

// Insert value and key mapping into the hash table
void mhashInsert(unsigned int key, void *val) {
  mhashlistnode_t *ptr, *node;

  pthread_mutex_lock(&mlookup.locktable);
  if (mlookup.numelements > mlookup.threshold) {
    //Resize Table
    unsigned int newsize = mlookup.size << 1;
    mhashResize(newsize);
  }

  ptr = &mlookup.table[(key & mlookup.mask) >>1];
  mlookup.numelements++;


  if(ptr->key ==0) {
    ptr->key=key;
    ptr->val=val;
    ptr->next = NULL;
  } else {                              // Insert in the beginning of linked list
    node = calloc(1, sizeof(mhashlistnode_t));
    node->key = key;
    node->val = val;
    node->next = ptr->next;
    ptr->next=node;
  }
  pthread_mutex_unlock(&mlookup.locktable);
}

// Return val for a given key in the hash table
void *mhashSearch(unsigned int key) {
  int index;
  mhashlistnode_t *node;
  pthread_mutex_lock(&mlookup.locktable);
  node = &mlookup.table[(key & mlookup.mask)>>1];
  do {
    if(node->key == key) {
      void * tmp=node->val;
      pthread_mutex_unlock(&mlookup.locktable);
      return tmp;
    }
    node = node->next;
  } while (node!=NULL);

  pthread_mutex_unlock(&mlookup.locktable);
  return NULL;
}

// Remove an entry from the hash table
unsigned int mhashRemove(unsigned int key) {
  int index;
  mhashlistnode_t *curr, *prev;
  mhashlistnode_t *ptr, *node;

  pthread_mutex_lock(&mlookup.locktable);
  ptr = mlookup.table;
  index = mhashFunction(key);
  curr = &ptr[index];
  for (; curr != NULL; curr = curr->next) {
    if (curr->key == key) {                     // Find a match in the hash table
      mlookup.numelements--;                    // Decrement the number of elements in the global hashtable
      if ((curr == &ptr[index]) && (curr->next == NULL)) {                    // Delete the first item inside the hashtable with no linked list of mhashlistnode_t
	curr->key = 0;
	curr->val = NULL;
      } else if ((curr == &ptr[index]) && (curr->next != NULL)) {                   //Delete the first item with a linked list of mhashlistnode_t  connected
	curr->key = curr->next->key;
	curr->val = curr->next->val;
	node = curr->next;
	curr->next = curr->next->next;
	free(node);
      } else {                                                                  // Regular delete from linked listed
	prev->next = curr->next;
	free(curr);
      }
      pthread_mutex_unlock(&mlookup.locktable);
      return 0;
    }
    prev = curr;
  }
  pthread_mutex_unlock(&mlookup.locktable);
  return 1;
}



// Resize table
unsigned int mhashResize(unsigned int newsize) {
  mhashlistnode_t *node, *ptr, *curr;            // curr and next keep track of the current and the next mhashlistnodes in a linked list
  unsigned int oldsize;
  int isfirst;          // Keeps track of the first element in the mhashlistnode_t for each bin in hashtable
  unsigned int i,index;
  unsigned int mask;

  ptr = mlookup.table;
  oldsize = mlookup.size;

  if((node = calloc(newsize, sizeof(mhashlistnode_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return 1;
  }

  mlookup.table = node;                 //Update the global hashtable upon resize()
  mlookup.size = newsize;
  mlookup.threshold=newsize*mlookup.loadfactor;
  mask=mlookup.mask = (newsize << 1)-1;

  for(i = 0; i < oldsize; i++) {                        //Outer loop for each bin in hash table
    curr = &ptr[i];
    isfirst = 1;
    do {
      unsigned int key;
      mhashlistnode_t *tmp,*next;

      if ((key=curr->key) == 0) {                             //Exit inner loop if there the first element for a given bin/index is NULL
	break;                                          //key = val =0 for element if not present within the hash table
      }
      next = curr->next;
      index = (key & mask) >>1;
      tmp=&mlookup.table[index];

      // Insert into the new table
      if(tmp->key ==0) {
	tmp->key=curr->key;
	tmp->val=curr->val;
	if (!isfirst)
	  free(curr);
      } /*

         NOTE:  Add this case if you change this...                                                        
         This case currently never happens because of the way things rehash....                            
else if (isfirst) {
	mhashlistnode_t *newnode = calloc(1, sizeof(mhashlistnode_t));
	newnode->key = curr->key;
	newnode->val = curr->val;
	newnode->next = tmp->next;
	tmp->next=newnode;
	} */
      else {
	curr->next=tmp->next;
	tmp->next=curr;
      }
      isfirst = 0;
      curr = next;
    } while(curr!=NULL);
  }

  free(ptr);                    //Free the memory of the old hash table
  return 0;
}

unsigned int *mhashGetKeys(unsigned int *numKeys) {
  unsigned int *keys;
  int i, keyindex;
  mhashlistnode_t *curr;

  pthread_mutex_lock(&mlookup.locktable);

  *numKeys = mlookup.numelements;
  keys = calloc(*numKeys, sizeof(unsigned int));

  keyindex = 0;
  for (i = 0; i < mlookup.size; i++) {
    if (mlookup.table[i].key != 0) {
      curr = &mlookup.table[i];

      while (curr != NULL) {
      	keys[keyindex++] = curr->key;
      	curr = curr->next;
      }
    }
  }

  if (keyindex != *numKeys)
    printf("mhashGetKeys(): WARNING: incorrect mlookup.numelements value!\n");

  pthread_mutex_unlock(&mlookup.locktable);
  return keys;
}

#ifdef RECOVERY
void* mhashGetDuplicate(int *dupeSize, int backup) { 
#ifdef DEBUG
	printf("%s-> Start\n", __func__); 
#endif
	unsigned int numdupe = 0;
	void* dPtr;

  unsigned int *oidsdupe;

  if((oidsdupe = (unsigned int*) calloc(mlookup.size,unsigned int)) == NULL) {
    printf("%s %s(): %d -> callock error\n",__FILE__,__func__,__LINE__);
    exit(-1);
  }
  
  int size = 0, tempsize = 0, i = 0;
	objheader_t *header;

	mhashlistnode_t *node;
//	go through object store;
//	track sizes, oids, and num
//  printf("%s -> Before mutex lock\n",__func__);
	pthread_mutex_lock(&mlookup.locktable); 
//  printf("%s -> After mutex lock\n",__func__);

  size =0;
  tempsize =0;

  for(i = 0; i < mlookup.size; i++) {
		if (mlookup.table[i].key != 0) {
			node = &mlookup.table[i];
			while(node != NULL) { // no nodes 
//        printf("%s -> node : %d node->val : %d \n",__func__,node,node->val);

				header = (objheader_t *)node->val;
				if((header->isBackup && backup) || (!header->isBackup && !backup)) {
					oidsdupe[numdupe++] = OID(header);
					GETSIZE(tempsize, header);
					size += tempsize + sizeof(objheader_t);

          if(header->notifylist != NULL) {
            //      number of nodes     +       actual size of array
            size += (sizeof(unsigned int) + (getListSize(header->notifylist) * sizeof(threadlist_t)));
          }
				}
				node = node->next;
			}
		}
	}
//  printf("%s -> size = %d\n",__func__,size);

  pthread_mutex_unlock(&mlookup.locktable);

	//i got sizes, oids, and num now
  //

	if((dPtr =(void*) malloc(sizeof(unsigned int)+sizeof(int)+ size)) == NULL) {
		printf("malloc error for modified objects %s, %d\n", __FILE__, __LINE__);
		return;
	}

//	for each oid in oiddupe[] get object and format

	void* ptr = dPtr;
  *((unsigned int *)(ptr)) = numdupe;
	ptr += sizeof(unsigned int);
  *((int *)(ptr)) = size;
	ptr += sizeof(int);

	for(i = 0; i < numdupe; i++) {
    header = mhashSearch(oidsdupe[i]);

		GETSIZE(tempsize, header);
		tempsize += sizeof(objheader_t);
		memcpy(ptr, header, tempsize); //*ptr = header maybe wont work, use memcopy instead probably

		if(header->isBackup && backup) {
      ((objheader_t*)ptr)->isBackup = 0;
    }else if(!(header->isBackup) && !backup) {
      ((objheader_t*)ptr)->isBackup = 1;
    }
    else {
      printf("%s -> ERROR\n",__func__);
      exit(0);
    }

		ptr += tempsize;

    if(header->notifylist != NULL) {
      unsigned int listSize;
      /* get duplicate array of threadlist */
      threadlist_t *threadArray;
      listSize = convertToArray(header->notifylist,&threadArray);

      memcpy(ptr, &listSize,sizeof(unsigned int));
      ptr += sizeof(unsigned int);

      memcpy(ptr, threadArray, (sizeof(threadlist_t) * listSize));
      ptr += (sizeof(threadlist_t) * listSize);  
      free(threadArray);
    }
	}
#ifdef DEBUG
	printf("%s-> End\n", __func__);
#endif

  free(oidsdupe);

  //          number of oid       size    + data array 
  *dupeSize = (sizeof(unsigned int) + sizeof(int) + size);

  return dPtr; 
}

int mhashGetThreadObjects(unsigned int** oidArray,unsigned int** midArray,unsigned int** threadidArray)
{
	printf("%s-> Start\n", __func__); 
	unsigned int oidArr[mlookup.numelements];
  unsigned int midArr[mlookup.numelements];
  unsigned int threadidArr[mlookup.numelements];
  unsigned int* hashkeys;
  unsigned int numKeys;
	objheader_t *header;
  int i;

  int size =0;
	mhashlistnode_t *node;
//	go through object store;
//	track sizes, oids, and num

  hashkeys = mhashGetKeys(&numKeys);
  printf("%s -> numKeys : %d\n",__func__,numKeys);

  threadlist_t* t;
  threadlist_t* tmp;

  for(i = 0; i < numKeys; i++) {
    header = (objheader_t*)mhashSearch(hashkeys[i]);
    pthread_mutex_lock(&mlookup.locktable);

    if(header->isBackup && header->notifylist != NULL) {
        
      t = header->notifylist;

      while(t) {
        oidArr[size] = OID(header);
        midArr[size] = t->mid;
        threadidArr[size++] = t->threadid;
        tmp = t;
        t = t->next;
        free(tmp);
      }

      header->notifylist = NULL;
    }
    pthread_mutex_unlock(&mlookup.locktable);
  }

  free(hashkeys);

  printf("%s -> end copying    Size : %d\n",__func__,size);

  if(size > 0) {
    *oidArray = (unsigned int*) calloc(size, sizeof(unsigned int));
    *midArray = (unsigned int*) calloc(size, sizeof(unsigned int));
    *threadidArray = (unsigned int*) calloc(size, sizeof(unsigned int));

    for(i = 0; i < size; i++) {
      (*oidArray)[i] = oidArr[i];
      (*midArray)[i] = midArr[i];
      (*threadidArray)[i] = threadidArr[i];
    }
  }

  printf("%s -> End\n",__func__);

  return size;

}
#endif
