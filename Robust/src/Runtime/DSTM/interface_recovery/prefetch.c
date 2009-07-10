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
    perMcPrefetchList_t* pilehead = processLocal(node);

    if (pilehead!=NULL) {

      /* Send  Prefetch Request */
      perMcPrefetchList_t *ptr = pilehead;
      while(ptr != NULL) {
	// Get sock from shared pool
	int sd = getSock2(transPrefetchSockPool, ptr->mid);
	sendRangePrefetchReq(ptr, sd, myIpAddr);
	ptr = ptr->next;
      }

      /* Deallocated pilehead */
      proPrefetchQDealloc(pilehead);
    }
    // Deallocate the prefetch queue pile node
    inctail();
  }
}

perMcPrefetchList_t *processLocal(char *ptr) {
  unsigned int oid = *(GET_OID(ptr));
  short numoffset = *(GET_NUM_OFFSETS(ptr));
  short *offsetarray = GET_OFFSETS(ptr);
  int top;
  unsigned int dfsList[numoffset];
  int offstop=numoffset-2;

  /* Initialize */
  perMcPrefetchList_t *head = NULL;

  objheader_t * header = searchObj(oid);
  if (header==NULL) {
    //forward prefetch
    int machinenum = lhashSearch(oid);
    insertPrefetch(machinenum, oid, numoffset, offsetarray, &head);
    return head;
  }
  dfsList[0]=oid;
  dfsList[1]=0;


  //Start searching the dfsList
  for(top=0; top>=0;) {
    oid=getNextOid(header, offsetarray, dfsList, top);
    if (oid&1) {
      int oldisField=TYPE(header) < NUMCLASSES;
      top+=2;
      dfsList[top]=oid;
      dfsList[top+1]=0;
      header=searchObj(oid);
      if (header==NULL) {
	//forward prefetch
	int machinenum = lhashSearch(oid);
	
	if (oldisField&&(dfsList[top-1]!=GET_RANGE(offsetarray[top+1])))
	  insertPrefetch(machinenum, oid, 2+numoffset-top, &offsetarray[top-2], &head);
	else
	  insertPrefetch(machinenum, oid, numoffset-top, &offsetarray[top], &head);
      } else if (top<offstop)
	//okay to continue going down
	continue;
    } else if (oid==2) {
      //send prefetch first
      int objindex=top+2;
      int machinenum = lhashSearch(dfsList[objindex]);
      insertPrefetch(machinenum, dfsList[objindex], numoffset-top, &offsetarray[top], &head);
    }
    //oid is 0
    //go backwards until we can increment
    do {
      do {
	top-=2;
	if (top<0)
	  return head;
      } while(dfsList[top+1] == GET_RANGE(offsetarray[top + 3]));

      header=searchObj(dfsList[top]);
      //header shouldn't be null unless the object moves away, but allow
      //ourselves the option to just continue on if we lose the object
    } while(header==NULL);
    //increment
    dfsList[top+1]++;
  }
  return head;
}

#define PBUFFERSIZE 16384
//#define PBUFFERSIZE 8192 //Used only for Moldyn benchmark


perMcPrefetchList_t *processRemote(unsigned int oid,  short * offsetarray, int sd, short numoffset) {
  int top;
  unsigned int dfsList[numoffset];
  char buffer[PBUFFERSIZE];
  int bufoffset=0;

  /* Initialize */
  perMcPrefetchList_t *head = NULL;

  objheader_t * header = searchObj(oid);
  int offstop=numoffset-2;
  if (header==NULL) {
    //forward prefetch
    int machinenum = lhashSearch(oid);
    insertPrefetch(machinenum, oid, numoffset, offsetarray, &head);
    return head;
  } else {
    sendOidFound(header, oid, sd, buffer, &bufoffset);
  }

  dfsList[0]=oid;
  dfsList[1]=0;

  //Start searching the dfsList
  for(top=0; top>=0;) {
    oid=getNextOid(header, offsetarray, dfsList, top);
    if (oid&1) {
      int oldisField=TYPE(header) < NUMCLASSES;
      top+=2;
      dfsList[top]=oid;
      dfsList[top+1]=0;
      header=searchObj(oid);
      if (header==NULL) {
	//forward prefetch
	int machinenum = lhashSearch(oid);
	if (oldisField&&(dfsList[top-1]!=GET_RANGE(offsetarray[top+1])))
	  insertPrefetch(machinenum, oid, 2+numoffset-top, &offsetarray[top-2], &head);
	else
	  insertPrefetch(machinenum, oid, numoffset-top, &offsetarray[top], &head);
      } else {
	sendOidFound(header, oid, sd, buffer, &bufoffset);
	if (top<offstop)
	  //okay to continue going down
	  continue;
      }
    } else if (oid==2) {
      //send prefetch first
      int objindex=top+2;
      int machinenum = lhashSearch(dfsList[objindex]);
      insertPrefetch(machinenum, dfsList[objindex], numoffset-top, &offsetarray[top], &head);
    }
    //oid is 0
    //go backwards until we can increment
    do {
      do {
	top-=2;
	if (top<0) {
	  flushResponses(sd, buffer, &bufoffset);
	  return head;
	}
      } while(dfsList[top+1] == GET_RANGE(offsetarray[top + 3]));

      header=searchObj(dfsList[top]);
      //header shouldn't be null unless the object moves away, but allow
      //ourselves the option to just continue on if we lose the object
    } while(header==NULL);
    //increment
    dfsList[top+1]++;
  }
  flushResponses(sd, buffer, &bufoffset);
  return head;
}


INLINE objheader_t *searchObj(unsigned int oid) {
  objheader_t *header;
  if ((header = (objheader_t *)mhashSearch(oid)) != NULL) {
    return header;
  } else
    return prehashSearch(oid);
}

/* Delete perMcPrefetchList_t and everything it points to */
void proPrefetchQDealloc(perMcPrefetchList_t *node) {
  while (node != NULL) {
    perMcPrefetchList_t * prefetchpile_next_ptr = node;
    while(node->list != NULL) {
      //offsets aren't owned by us, so we don't free them.
      objOffsetPile_t * objpile_ptr = node->list;
      node->list = objpile_ptr->next;
      free(objpile_ptr);
    }
    node = prefetchpile_next_ptr->next;
    free(prefetchpile_next_ptr);
  }
}

void insertPrefetch(int mid, unsigned int oid, short numoffset, short *offsets, perMcPrefetchList_t **head) {
  perMcPrefetchList_t *ptr;
  objOffsetPile_t *objnode;
  objOffsetPile_t **tmp;

  char ptr1[50];
  midtoIP(mid, ptr1);
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

void sendRangePrefetchReq(perMcPrefetchList_t *mcpilenode, int sd, unsigned int mid) {
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
    *((unsigned int *)buf) = mid;
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
    size = size - (sizeof(char) + sizeof(unsigned int));
    pthread_mutex_lock(&prefetchcache_mutex);
    void *ptr;
    if((ptr = prefetchobjstrAlloc(size)) == NULL) {
      printf("%s() Error: objstrAlloc error for copying into prefetch cache in line %d at %s\n",
             __func__, __LINE__, __FILE__);
      pthread_mutex_unlock(&prefetchcache_mutex);
      return -1;
    }

    void *tmp=ptr;
    int osize=size;
    pthread_mutex_unlock(&prefetchcache_mutex);

    memcpy(ptr, recvbuffer + sizeof(char) + sizeof(unsigned int), size);

    //ignore oid value...we'll get it from the object

    while(size>0) {
      unsigned int objsize;
      GETSIZE(objsize, ptr);
      STATUS(ptr)=0;
      oid=OID(ptr);
      objsize+=sizeof(objheader_t);
      
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
      ptr=(void *)(((unsigned int)ptr)+objsize);
      size-=objsize;
    }

    pthread_mutex_lock(&pflookup.lock);
    pthread_cond_broadcast(&pflookup.cond);
    pthread_mutex_unlock(&pflookup.lock);
  } else if(control == OBJECT_NOT_FOUND) {
    oid = *((unsigned int *)(recvbuffer + sizeof(char)));
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

    perMcPrefetchList_t * pilehead=processRemote(baseoid, offsetsarry, sd, numoffset);

    if (pilehead!= NULL) {
      perMcPrefetchList_t *ptr = pilehead;
      while(ptr != NULL) {
	// Get sock from shared pool
	int sd = getSock2(transPrefetchSockPool, ptr->mid);
	sendRangePrefetchReq(ptr, sd, mid);
	ptr = ptr->next;
      }

      proPrefetchQDealloc(pilehead);
    }
  }

  //Release socket
  if(mid!=-1)
    freeSockWithLock(transPResponseSocketPool, mid, sd);
  return 0;
}


unsigned int getNextOid(objheader_t * header, short * offsetarray, unsigned int *dfsList, int top) {
  int startindex= offsetarray[top+2];
  int currcount = dfsList[top+1];
  int range = GET_RANGE(offsetarray[top + 3]);

  if(TYPE(header) >= NUMCLASSES) {
    //Array case
    struct ArrayObject *ao = (struct ArrayObject *) (((char *)header) + sizeof(objheader_t));
    int stride = GET_STRIDE(offsetarray[top + 3])+1;
    int length = ao->___length___;
    int currindex;
    //Check direction of stride
    if(GET_STRIDEINC(offsetarray[top + 3])) {
      //Negative
      currindex=startindex-stride*currcount;
      if (currindex<0)
	return 0;

      //Also have to check whether we will eventually index into array
      if (currindex>=length) {
	//Skip to the point that we will index into array
	int delta=(currindex-length-1)/stride+1; //-1, +1 is to make sure that it rounds up
	if ((delta+currcount)>range)
	  return 0;
	currindex-=delta*stride;
      }
    } else {
      //Going positive, compute current index
      currindex=startindex+stride*currcount;
      if(currindex >= length)
	return 0;
    }

    int elementsize = classsize[TYPE(header)];
    return *((unsigned int *)(((char *)ao) + sizeof(struct ArrayObject) + elementsize*currindex));
  } else {
    //handle fields

    if(currcount!=0 & range != 0) {
      //go to the next offset
      header=searchObj(dfsList[top+2]);
      if (header==NULL)
	return 2;
    }

    return *((unsigned int *)(((char *)header) + sizeof(objheader_t) + startindex));
  }
}

void flushResponses(int sd, char * buffer, int * bufoffset) {
  if ((*bufoffset)!=0) {
    send_data(sd, buffer, *bufoffset);
    *bufoffset=0;
  }
}

int sendOidFound(objheader_t * header, unsigned int oid, int sd, char *buffer, int *bufoffset) {
  int incr;
  int objsize;
  GETSIZE(objsize, header);
  int size  = sizeof(objheader_t) + objsize;
  char *sendbuffer;

  if ((incr=(*bufoffset))==0) {
    buffer[incr] = TRANS_PREFETCH_RESPONSE;
    incr+=sizeof(char);
    *((int *)(buffer + incr)) = size+sizeof(int)+sizeof(char)+sizeof(unsigned int);
    incr += sizeof(int);
    *((char *)(buffer + incr)) = OBJECT_FOUND;
    incr += sizeof(char);
    *((unsigned int *)(buffer + incr)) = oid;
    incr += sizeof(unsigned int);
  } else
    *((int *)(buffer+sizeof(char)))+=size;
  
  if ((incr+size)<PBUFFERSIZE) {
    //don't need to allocate, just copy
    sendbuffer=buffer;
    (*bufoffset)=incr+size;
  } else {
    sendbuffer=alloca(size+incr);
    memcpy(sendbuffer, buffer, incr);
    *bufoffset=0;
  }

  memcpy(sendbuffer + incr, header, size);
  if ((*bufoffset)==0)
    send_data(sd, sendbuffer, size+incr);
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
