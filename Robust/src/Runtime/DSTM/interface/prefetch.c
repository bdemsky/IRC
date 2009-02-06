#include "prefetch.h"
#include "prelookup.h"
#include "sockpool.h"
#include "gCollect.h"

extern sockPoolHashTable_t *transPrefetchSockPool;
extern unsigned int myIpAddr;
extern sockPoolHashTable_t *transPResponseSocketPool;
extern pthread_mutex_t prefetchcache_mutex;
extern prehashtable_t pflookup;

// Function for new prefetch call
void rangePrefetch(unsigned int oid, short numoffset, short *offsets) {
  /* Allocate memory in prefetch queue and push the block there */
  int qnodesize = sizeof(unsigned int) + sizeof(unsigned short) + numoffset * sizeof(short);
  char *node = (char *) getmemory(qnodesize);
  int i;

  if(node == NULL)
    return;

  int index = 0;
  ((unsigned int *)node)[0] = oid;
  index = index + (sizeof(unsigned int));
  *((short *)(node+index)) = numoffset;
  index = index + (sizeof(short));
  memcpy(node+index, offsets, numoffset * sizeof(short));

  movehead(qnodesize);
}

void *transPrefetchNew() {
  while(1) {
    /* Read from prefetch queue */
    void *node = gettail();

    /* Check tuples if they are found locally */
    perMcPrefetchList_t* pilehead = checkIfLocal(node);

    if (pilehead!=NULL) {
      // Get sock from shared pool
      int sd = getSock2(transPrefetchSockPool, pilehead->mid);

      /* Send  Prefetch Request */
      perMcPrefetchList_t *ptr = pilehead;
      while(ptr != NULL) {
	sendRangePrefetchReq(ptr, sd);
	ptr = ptr->next;
      }

      /* Deallocated pilehead */
      proPrefetchQDealloc(pilehead);
    }
    // Deallocate the prefetch queue pile node
    inctail();
  }
}

int getsize(short *ptr, int n) {
  int sum = 0, newsum, i;
  for (i = n-1; i >= 0; i--) {
    newsum = (1 + ptr[i])+((1 + ptr[i])*sum);
    sum = newsum;
  }
  return sum;
}


perMcPrefetchList_t *checkIfLocal(char *ptr) {
  unsigned int oid = *(GET_OID(ptr));
  short numoffset = *(GET_NUM_OFFSETS(ptr));
  short *offsetarray = GET_OFFSETS(ptr);
  int depth=0, top=0;
  unsigned int dfsList[numoffset];
  oidAtDepth_t odep;

  /* Initialize */
  perMcPrefetchList_t *head = NULL;
  odep.oid = 0;
  odep.depth = depth;
  int i;
  for(i = 0; i<numoffset; i++) {
    dfsList[i] = 0;
  }

  //Start searching the dfsList
  while(top >= 0) {
    int retval;
    if((retval = getNextOid(offsetarray, dfsList, &top, &depth, &odep, oid)) != 0) {
      printf("%s() Error: Getting new oid at %s, %d\n", __func__, __FILE__, __LINE__);
      return NULL;
    }
    dfsList[top] = odep.oid;
    dfsList[top+1] = 0;
labelL1:
    ;
    objheader_t *objhead = searchObj(dfsList[top]);
    if(objhead == NULL) { //null oid or oid not found
      // Not found
      int machinenum = lhashSearch(dfsList[top]);
      insertPrefetch(machinenum, dfsList[top], numoffset-(depth), &offsetarray[depth], &head);
      //go up the tree
      while((dfsList[top+1] == *(offsetarray + depth + 1)) && (depth >= 0)) {
	if(top == depth) {
	  top -= 2;
	  depth -= 2;
	} else {
	  depth -= 2;
	}
      }
      //return if no more paths to explore
      if(top < 0 || depth < 0) {
	return head;
      }
      //If more paths to explore, proceed down the tree
      dfsList[top+1]++;
      int prev = top - 2;
      objheader_t *header;
      header = searchObj(dfsList[prev]);
      if(header == NULL) {
	printf("%s() Error Object not found at %s , %d\n", __func__, __FILE__, __LINE__);
	return NULL;
      } else {
	//if Array
	if(TYPE(header) > NUMCLASSES) {
	  dfsList[top] = getNextArrayOid(offsetarray, dfsList, &top, &depth);
	} else { //linked list
	  dfsList[top] = getNextPointerOid(offsetarray, dfsList, &top, &depth);
	}
	goto labelL1;
      }
    } else { // increment and go down the tree
      //Increment top
      top += 2;
      depth += 2;
      if(depth >= numoffset) { //reached the end of the path
	top -= 2;
	depth -= 2;
	//go up the tree
	while((dfsList[top + 1] == *(offsetarray + depth + 1)) && (depth >= 0)) {
	  if(top == depth) {
	    top -= 2;
	    depth -= 2;
	  } else
	    depth -= 2;
	}
	//return if no more paths to explore
	if(top < 0 || depth < 0) {
	  return head;
	}
	//If more paths to explore, go down the tree
	dfsList[top + 1]++;
	int prev = top - 2;
	objheader_t * header;
	header = searchObj(dfsList[prev]);
	if(header == NULL) {
	  printf("%s() Error Object not found at %s , %d\n", __func__, __FILE__, __LINE__);
	  return NULL;
	} else {
	  //if Array
	  if(TYPE(header) > NUMCLASSES) {
	    dfsList[top] = getNextArrayOid(offsetarray, dfsList, &top, &depth);
	  } else { //linked list
	    dfsList[top] = getNextPointerOid(offsetarray, dfsList, &top, &depth);
	  }
	  goto labelL1;
	}
      } else
	continue;
    }
  } //end of while
  return head;
}

objheader_t *searchObj(unsigned int oid) {
  objheader_t *header = NULL;

  if ((header = (objheader_t *)mhashSearch(oid)) != NULL) {
    return header;
  } else if ((header = (objheader_t *) prehashSearch(oid)) != NULL) {
    return header;
  } else {
    //printf("Error: Cannot find header %s, %d\n", __func__, __LINE__);
  }
  return NULL;
}

/* Delete perMcPrefetchList_t and everything it points to */
void proPrefetchQDealloc(perMcPrefetchList_t *node) {
  perMcPrefetchList_t *prefetchpile_ptr;
  perMcPrefetchList_t *prefetchpile_next_ptr;
  objOffsetPile_t *objpile_ptr;
  objOffsetPile_t *objpile_next_ptr;

  prefetchpile_ptr = node;
  while (prefetchpile_ptr != NULL) {
    prefetchpile_next_ptr = prefetchpile_ptr;
    while(prefetchpile_ptr->list != NULL) {
      //offsets aren't owned by us, so we don't free them.
      objpile_ptr = prefetchpile_ptr->list;
      prefetchpile_ptr->list = objpile_ptr->next;
      free(objpile_ptr);
    }
    prefetchpile_ptr = prefetchpile_next_ptr->next;
    free(prefetchpile_next_ptr);
  }
}

void insertPrefetch(int mid, unsigned int oid, short numoffset, short *offsets, perMcPrefetchList_t **head) {
  perMcPrefetchList_t *ptr;
  objOffsetPile_t *objnode;
  objOffsetPile_t **tmp;

  //Loop through the machines
  for(; 1; head=&((*head)->next)) {
    int tmid;
    if ((*head)==NULL||(tmid=(*head)->mid)>mid) {
      perMcPrefetchList_t * tmp = (perMcPrefetchList_t *) malloc(sizeof(perMcPrefetchList_t));
      tmp->mid = mid;
      objnode =  malloc(sizeof(objOffsetPile_t));
      objnode->offsets = offsets;
      objnode->oid = oid;
      objnode->numoffset = numoffset;
      objnode->next = NULL;
      tmp->list = objnode;
      tmp->next = *head;
      *head=tmp;
      return;
    }

    //keep looking
    if (tmid < mid)
      continue;

    //found mid list
    for(tmp=&((*head)->list); 1; tmp=&((*tmp)->next)) {
      int toid;
      int matchstatus;

      if ((*tmp)==NULL||((toid=(*tmp)->oid)>oid)) {
	objnode = (objOffsetPile_t *) malloc(sizeof(objOffsetPile_t));
	objnode->offsets = offsets;
	objnode->oid = oid;
	objnode->numoffset = numoffset;
	objnode->next = *tmp;
	*tmp = objnode;
	return;
      }
      if (toid < oid)
	continue;

      /* Fill list DS */
      int i;
      int onumoffset=(*tmp)->numoffset;
      short * ooffset=(*tmp)->offsets;

      for(i=0; i<numoffset; i++) {
	if (i>onumoffset) {
	  //We've matched, let's just extend the current prefetch
	  (*tmp)->numoffset=numoffset;
	  (*tmp)->offsets=offsets;
	  return;
	}
	if (ooffset[i]<offsets[i]) {
	  goto oidloop;
	} else if (ooffset[i]>offsets[i]) {
	  //Place item before the current one
	  objnode = (objOffsetPile_t *) malloc(sizeof(objOffsetPile_t));
	  objnode->offsets = offsets;
	  objnode->oid = oid;
	  objnode->numoffset = numoffset;
	  objnode->next = *tmp;
	  *tmp = objnode;
	  return;
	}
      }
      //if we get to the end, we're already covered by this prefetch
      return;
oidloop:
      ;
    }
  }
}

void sendRangePrefetchReq(perMcPrefetchList_t *mcpilenode, int sd) {
  int len, endpair;
  char control;
  objOffsetPile_t *tmp;

  /* Send TRANS_PREFETCH control message */
  control = TRANS_PREFETCH;
  send_data(sd, &control, sizeof(char));

  /* Send Oids and offsets in pairs */
  tmp = mcpilenode->list;
  while(tmp != NULL) {
    len = sizeof(int) + sizeof(unsigned int) + sizeof(unsigned int) + ((tmp->numoffset) * sizeof(short));
    char oidnoffset[len];
    char *buf=oidnoffset;
    *((int*)buf) = tmp->numoffset;
    buf+=sizeof(int);
    *((unsigned int *)buf) = tmp->oid;
    buf+=sizeof(unsigned int);
    *((unsigned int *)buf) = myIpAddr;
    buf += sizeof(unsigned int);
    memcpy(buf, tmp->offsets, (tmp->numoffset)*sizeof(short));
    send_data(sd, oidnoffset, len);
    tmp = tmp->next;
  }

  /* Send a special char -1 to represent the end of sending oids + offset pair to remote machine */
  endpair = -1;
  send_data(sd, &endpair, sizeof(int));
  return;
}

int getRangePrefetchResponse(int sd) {
  int length = 0;
  recv_data(sd, &length, sizeof(int));
  int size = length - sizeof(int);
  char recvbuffer[size];
  recv_data(sd, recvbuffer, size);
  char control = *((char *) recvbuffer);
  unsigned int oid;
  if(control == OBJECT_FOUND) {
    oid = *((unsigned int *)(recvbuffer + sizeof(char)));
    size = size - (sizeof(char) + sizeof(unsigned int));
    pthread_mutex_lock(&prefetchcache_mutex);
    void *ptr;
    if((ptr = prefetchobjstrAlloc(size)) == NULL) {
      printf("%s() Error: objstrAlloc error for copying into prefetch cache in line %d at %s\n",
             __func__, __LINE__, __FILE__);
      pthread_mutex_unlock(&prefetchcache_mutex);
      return -1;
    }
    pthread_mutex_unlock(&prefetchcache_mutex);
    memcpy(ptr, recvbuffer + sizeof(char) + sizeof(unsigned int), size);
    STATUS(ptr)=0;

    /* Insert into prefetch hash lookup table */
    void * oldptr;
    if((oldptr = prehashSearch(oid)) != NULL) {
      if(((objheader_t *)oldptr)->version <= ((objheader_t *)ptr)->version) {
	prehashRemove(oid);
	prehashInsert(oid, ptr);
      }
    } else {
      prehashInsert(oid, ptr);
    }
    pthread_mutex_lock(&pflookup.lock);
    pthread_cond_broadcast(&pflookup.cond);
    pthread_mutex_unlock(&pflookup.lock);
  } else if(control == OBJECT_NOT_FOUND) {
    oid = *((unsigned int *)(recvbuffer + sizeof(char)));
    //printf("%s() Error: OBJ NOT FOUND.. THIS SHOULD NOT HAPPEN\n", __func__);
  } else {
    printf("%s() Error: in Decoding the control value %d, %s\n", __func__, __LINE__, __FILE__);
  }
  return 0;
}

int rangePrefetchReq(int acceptfd) {
  int numoffset, sd = -1;
  unsigned int baseoid, mid = -1;
  oidmidpair_t oidmid;

  while (1) {
    recv_data(acceptfd, &numoffset, sizeof(int));
    if(numoffset == -1)
      break;
    recv_data(acceptfd, &oidmid, 2*sizeof(unsigned int));
    baseoid = oidmid.oid;
    if(mid != oidmid.mid) {
      if(mid!= -1)
	freeSockWithLock(transPResponseSocketPool, mid, sd);
      mid = oidmid.mid;
      sd = getSockWithLock(transPResponseSocketPool, mid);
    }
    short offsetsarry[numoffset];
    recv_data(acceptfd, offsetsarry, numoffset*sizeof(short));
    int retval;
    if((retval = dfsOffsetTree(baseoid, offsetsarry, sd, numoffset)) != 0) {
      printf("%s() Error: in dfsOffsetTree() at line %d in %s()\n",
             __func__, __LINE__, __FILE__);
      return -1;
    }
  }

  //Release socket
  if(mid!=-1)
    freeSockWithLock(transPResponseSocketPool, mid, sd);
  return 0;
}

int dfsOffsetTree(unsigned int baseoid, short * offsetarray, int sd, int numoffset) {
  int depth=0, top=0;
  unsigned int dfsList[numoffset];
  oidAtDepth_t odep;

  /* Initialize */
  odep.oid = 0;
  odep.depth = depth;
  int i;
  for(i = 0; i<numoffset; i++) {
    dfsList[i] = 0;
  }

  //Start searching the dfsList
  while(top >= 0) {
    int retval;
    if((retval = getNextOid(offsetarray, dfsList, &top, &depth, &odep, baseoid)) != 0) {
      printf("%s() Error: Getting new oid at %s, %d\n", __func__, __FILE__, __LINE__);
      return -1;
    }
    dfsList[top] = odep.oid;
    dfsList[top+1] = 0;
labelL1:
    ;
    objheader_t *objhead = searchObj(dfsList[top]);
    if(objhead == NULL) { //null oid or oid not found
      int retval;
      if((retval = sendOidNotFound(dfsList[top], sd)) != 0) {
	printf("%s() Error in sendOidNotFound() at line %d in %s()\n", __func__, __LINE__, __FILE__);
	return -1;
      }
      //go up the tree
      while((dfsList[top+1] == *(offsetarray + depth + 1)) && (depth >= 0)) {
	if(top == depth) {
	  top -= 2;
	  depth -= 2;
	} else {
	  depth -= 2;
	}
      }
      //return if no more paths to explore
      if(top < 0 || depth < 0) {
	return 0;
      }
      //If more paths to explore, proceed down the tree
      dfsList[top+1]++;
      int prev = top - 2;
      objheader_t *header;
      header = searchObj(dfsList[prev]);
      if(header == NULL) {
	printf("%s() Error Object not found at %s , %d\n", __func__, __FILE__, __LINE__);
	return -1;
	//return 0;
      } else {
	//if Array
	if(TYPE(header) > NUMCLASSES) {
	  dfsList[top] = getNextArrayOid(offsetarray, dfsList, &top, &depth);
	} else { //linked list
	  dfsList[top] = getNextPointerOid(offsetarray, dfsList, &top, &depth);
	}
	goto labelL1;
      }
    } else { // increment and go down the tree
      if((retval = sendOidFound(OID(objhead), sd)) != 0) {
	printf("%s() Error in sendOidFound() at line %d in %s()\n", __func__, __LINE__, __FILE__);
	return -1;
      }
      //Increment top
      top += 2;
      depth += 2;
      if(depth >= numoffset) { //reached the end of the path
	top -= 2;
	depth -= 2;
	//go up the tree
	while((dfsList[top + 1] == *(offsetarray + depth + 1)) && (depth >= 0)) {
	  if(top == depth) {
	    top -= 2;
	    depth -= 2;
	  } else
	    depth -= 2;
	}
	//return if no more paths to explore
	if(top < 0 || depth < 0) {
	  return 0;
	}
	//If more paths to explore, go down the tree
	dfsList[top + 1]++;
	int prev = top - 2;
	objheader_t * header;
	header = searchObj(dfsList[prev]);
	if(header == NULL) {
	  printf("%s() Error Object not found at %s , %d\n", __func__, __FILE__, __LINE__);
	  return -1;
	  //return 0;
	} else {
	  //if Array
	  if(TYPE(header) > NUMCLASSES) {
	    dfsList[top] = getNextArrayOid(offsetarray, dfsList, &top, &depth);
	  } else { //linked list
	    dfsList[top] = getNextPointerOid(offsetarray, dfsList, &top, &depth);
	  }
	  goto labelL1;
	}
      } else
	continue;
    }
  } //end of while
  return 0;
}

int getNextOid(short * offsetarray, unsigned int *dfsList, int *top, int *depth, oidAtDepth_t *odep, unsigned int baseoid) {
  if(*top == 0) {
    odep->oid = baseoid;
    odep->depth = 0;
  } else {
    int prev = (*top) - 2;
    unsigned int oid = *(dfsList+prev);
    objheader_t * header = searchObj(oid);
    if(header == NULL) {
      odep->oid = 0;
      odep->depth = 0;
      return 0;
    } else {
      int range = GET_RANGE(*(offsetarray+(*depth) + 1));
      short stride = GET_STRIDE(*(offsetarray+(*depth) + 1));
      stride++; //Note bit pattern 000 => stride = 1 etc
      //if Array
      if(TYPE(header) > NUMCLASSES) {
	int elementsize = classsize[TYPE(header)];
	struct ArrayObject *ao = (struct ArrayObject *) (((char *)header) + sizeof(objheader_t));
	int length = ao->___length___;
	//check is stride is +ve or -ve
	int sign;
	if(GET_STRIDEINC(*(offsetarray+ (*depth) + 1))) {
	  sign = -1;
	} else {
	  sign = 1;
	}
	int startelement = *(offsetarray + (*depth));
	if(startelement < 0 || startelement >=length) {
	  printf("%s() Error: Offset out of range at %d\n", __func__, __LINE__);
	  odep->oid = 0;
	  odep->depth = 0;
	  return 0;
	}
	int index = *(dfsList+(*top)+1);
	odep->oid = *((unsigned int *)(((char *)ao) + sizeof(struct ArrayObject) \
	                               + (elementsize * (startelement + (sign*stride*index)))));
	odep->depth = *(depth);
      } else { //linked list
	int dep;
	int startelement;
	if(range > 0) { //go to the next offset
	  startelement = *((int *)(offsetarray + (*depth) + 2));
	  dep = *depth + 2;
	} else if(range == 0) {
	  startelement = *((int *)(offsetarray + (*depth)));
	  dep = *depth;
	} else { //range < 0
	  odep->oid = 0;
	  odep->depth = 0;
	  return 0;
	}
	odep->oid = *((unsigned int *)(((char *)header) + sizeof(objheader_t) + startelement));
	odep->depth = dep;
      }
    }
  }
  return 0;
}

unsigned int getNextArrayOid(short *offsetarray, unsigned int *dfsList, int *top, int* depth) {
  int prev = (*top) - 2;
  unsigned int oid = *(dfsList + prev);
  objheader_t *header = searchObj(oid);
  if(header == NULL) {
    printf("%s() Error: Object not found at %s , %d\n", __func__, __FILE__, __LINE__);
    return 0;
  } else {
    short stride = GET_STRIDE(*(offsetarray+(*depth) + 1));
    stride++; //Note bit pattern 000 => stride = 1 etc
    //check is stride is +ve or -ve
    int sign;
    if(GET_STRIDEINC(*(offsetarray+ (*depth) + 1))) {
      sign = -1;
    } else {
      sign = 1;
    }
    int elementsize = classsize[TYPE(header)];
    struct ArrayObject *ao = (struct ArrayObject *) (((char *)header) + sizeof(objheader_t));
    int length = ao->___length___;
    int startelement = *(offsetarray + (*depth));
    if(startelement < 0 || startelement >=length) {
      printf("%s() Error: Offset out of range at %d\n", __func__, __LINE__);
      return 0;
    }
    int index = *(dfsList + *top + 1);
    oid = *((unsigned int *)(((char *)ao) + sizeof(struct ArrayObject) \
                             + (elementsize * (startelement + (sign*stride*index)))));
  }
  return oid;
}

unsigned int getNextPointerOid(short *offsetarray, unsigned int *dfsList, int *top, int* depth) {
  int prev;
  if(*(dfsList + *top + 1) > 1) { //tells which offset to calculate the oid from
    prev = *top;
  } else {
    prev = *top - 2;
  }
  unsigned int oid = *(dfsList + prev);
  objheader_t *header = searchObj(oid);
  if(header == NULL) {
    printf("%s() Error: Object not found at %s , %d\n", __func__, __FILE__, __LINE__);
    return 0;
  } else {
    int startelement = *(offsetarray + *depth);
    oid = *((unsigned int *)(((char *)header) + sizeof(objheader_t) + startelement));
    //TODO add optimization for checking if this oid has already not been found
  }
  return oid;
}

int sendOidFound(unsigned int oid, int sd) {
  objheader_t *header;
  if((header = (objheader_t *) mhashSearch(oid)) != NULL) {
    ;
  } else if((header = (objheader_t *) prehashSearch(oid))!=NULL) {
    ;
  } else {
    printf("%s() Error: THIS SHOULD NOT HAPPEN at line %d in %s()\n", __func__, __LINE__, __FILE__);
    return -1;
  }

  int incr = 0;
  int objsize;
  GETSIZE(objsize, header);
  int size  = sizeof(int) + sizeof(char) + sizeof(unsigned int) + sizeof(objheader_t) + objsize;
  char sendbuffer[size];
  *((int *)(sendbuffer + incr)) = size;
  incr += sizeof(int);
  *((char *)(sendbuffer + incr)) = OBJECT_FOUND;
  incr += sizeof(char);
  *((unsigned int *)(sendbuffer + incr)) = oid;
  incr += sizeof(unsigned int);
  memcpy(sendbuffer + incr, header, objsize + sizeof(objheader_t));

  char control = TRANS_PREFETCH_RESPONSE;
  sendPrefetchResponse(sd, &control, sendbuffer, &size);
  return 0;
}

int sendOidNotFound(unsigned int oid, int sd) {
  int size  = sizeof(int) + sizeof(char) + sizeof(unsigned int);
  char sendbuffer[size];
  *((int *)sendbuffer) = size;
  *((char *)(sendbuffer + sizeof(int))) = OBJECT_NOT_FOUND;
  *((unsigned int *)(sendbuffer + sizeof(int) + sizeof(unsigned int))) = oid;
  char control = TRANS_PREFETCH_RESPONSE;
  sendPrefetchResponse(sd, &control, sendbuffer, &size);
  return 0;
}
