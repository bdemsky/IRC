#include "prefetch.h"
#include "altprelookup.h"
#include "sockpool.h"
#include "gCollect.h"

extern sockPoolHashTable_t *transPrefetchSockPool;
extern unsigned int myIpAddr;
extern sockPoolHashTable_t *transPResponseSocketPool;
extern pthread_mutex_t prefetchcache_mutex;
extern prehashtable_t pflookup;

//#define LOGTIMES
#ifdef LOGTIMES
extern char bigarray1[6*1024*1024];
extern unsigned int bigarray2[6*1024*1024];
extern unsigned int bigarray3[6*1024*1024];
extern long long bigarray4[6*1024*1024];
extern int bigarray5[6*1024*1024];
extern int bigindex1;
#define LOGTIME(x,y,z,a,b) {\
  int tmp=bigindex1; \
  bigarray1[tmp]=x; \
  bigarray2[tmp]=y; \
  bigarray3[tmp]=z; \
  bigarray4[tmp]=a; \
  bigarray5[tmp]=b; \
  bigindex1++; \
}
#else
#define LOGTIME(x,y,z,a,b)
//log(eventname, oid, type, time, unqiue id)
#endif

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
  LOGTIME('R',oid,numoffset,0,0);
  index = index + (sizeof(short));
  memcpy(node+index, offsets, numoffset * sizeof(short));
  movehead(qnodesize);
}

void *transPrefetchNew() {
  while(1) {
    /* Read from prefetch queue */
    void *node = gettail();

    int count = numavailable();
    /* Check tuples if they are found locally */
    perMcPrefetchList_t* pilehead = processLocal(node,count);

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
    incmulttail(count);
  }
}

perMcPrefetchList_t *processLocal(char *ptr, int numprefetches) {
  int j;
  /* Initialize */
  perMcPrefetchList_t *head = NULL;

  for(j=0;j<numprefetches; j++) {
    unsigned int oid = *(GET_OID(ptr));
    short numoffset = *(GET_NUM_OFFSETS(ptr));
    short *offsetarray = GET_OFFSETS(ptr);
    int top=0;
    unsigned int dfsList[numoffset];
    int offstop=numoffset-2;

    int countInvalidObj=-1;
    int isLastOffset=0;
    if(offstop==0) { //if no offsets 
      isLastOffset=1;
    }

    objheader_t * header = searchObjInv(oid, top, &countInvalidObj, &isLastOffset);
    if (header==NULL) {
      LOGTIME('b',oid,0,0,countInvalidObj);
      //forward prefetch
      if(oid!=0) {
        int machinenum = lhashSearch(oid);
        if(machinenum != myIpAddr) {
          insertPrefetch(machinenum, oid, numoffset, offsetarray, &head);
        }
      }
      //update ptr
      ptr=((char *)&offsetarray[numoffset])+sizeof(int);
      continue;
    }
    dfsList[0]=oid;
    dfsList[1]=0;

    LOGTIME('B',oid,TYPE(header),0,countInvalidObj);

    //Start searching the dfsList
    for(top=0; top>=0;) {
      if(top == offstop) {
        isLastOffset=1;
      }
      oid=getNextOid(header, offsetarray, dfsList, top, &countInvalidObj, &isLastOffset);
      LOGTIME('O',oid,0,0,countInvalidObj);
      if (oid&1) {
        int oldisField=TYPE(header) < NUMCLASSES;
        top+=2;
        dfsList[top]=oid;
        dfsList[top+1]=0;
        header=searchObjInv(oid, top, &countInvalidObj, &isLastOffset);
        if (header==NULL) {
          LOGTIME('c',oid,top,0,countInvalidObj);
          //forward prefetch
          int machinenum = lhashSearch(oid);
          if(machinenum != myIpAddr) {
            if (oldisField&&(dfsList[top-1]!=GET_RANGE(offsetarray[top+1]))) {
              insertPrefetch(machinenum, oid, 2+numoffset-top, &offsetarray[top-2], &head);
            } else {
              insertPrefetch(machinenum, oid, numoffset-top, &offsetarray[top], &head);
            }
          }
        } else if (top<offstop) {
          LOGTIME('C',oid,TYPE(header),0,top);
          //okay to continue going down
          continue;
        }
      } else if (oid==2) {
        LOGTIME('D',oid,0,0,top);
        //send prefetch first
        int objindex=top+2;
        int machinenum = lhashSearch(dfsList[objindex]);
        if(machinenum != myIpAddr) {
          insertPrefetch(machinenum, dfsList[objindex], numoffset-top, &offsetarray[top], &head);
        }
      }
      //oid is 0
      //go backwards until we can increment
      do {
        do {
          top-=2;
          if (top<0) {
            goto tuple;
            //return head;
          }
        } while(dfsList[top+1] == GET_RANGE(offsetarray[top + 3]));

        //we backtracked past the invalid obj...set out countInvalidObj=-1
        if (top<countInvalidObj)
          countInvalidObj=-1;

        header=searchObjInv(dfsList[top], top, &countInvalidObj, NULL);
        //header shouldn't be null unless the object moves away, but allow
        //ourselves the option to just continue on if we lose the object
      } while(header==NULL);
      LOGTIME('F',OID(header),TYPE(header),0,top);
      //increment
      dfsList[top+1]++;
    }
tuple:
    //update ptr
    ptr=((char *)&offsetarray[numoffset])+sizeof(int);
  }
  return head;
}

#define PBUFFERSIZE 16384
//#define PBUFFERSIZE 8192 //Used only for Moldyn benchmark


perMcPrefetchList_t *processRemote(unsigned int oid,  short * offsetarray, int sd, short numoffset, unsigned int mid, struct writestruct *writebuffer) {
  int top;
  unsigned int dfsList[numoffset];
  //char buffer[PBUFFERSIZE];
  //int bufoffset=0;

  /* Initialize */
  perMcPrefetchList_t *head = NULL;

  objheader_t * header = searchObj(oid);

  int offstop=numoffset-2;
  if (header==NULL) {
    LOGTIME('g',oid,0,0,0);
    //forward prefetch
    //int machinenum = lhashSearch(oid);
    //insertPrefetch(machinenum, oid, numoffset, offsetarray, &head);
    return head;
  } else {
    sendOidFound(header, oid, sd, writebuffer);
  }

  dfsList[0]=oid;
  dfsList[1]=0;
  LOGTIME('G',OID(header),TYPE(header),0, 0);

  //Start searching the dfsList
  for(top=0; top>=0;) {
    oid=getNextOid(header, offsetarray, dfsList, top, NULL, NULL);
    if (oid&1) {
      int oldisField=TYPE(header) < NUMCLASSES;
      top+=2;
      dfsList[top]=oid;
      dfsList[top+1]=0;
      header=searchObj(oid);
      if (header==NULL) {
        LOGTIME('h',oid,top,0,0);
        //forward prefetch
        /*
           int machinenum = lhashSearch(oid);
           if (oldisField&&(dfsList[top-1]!=GET_RANGE(offsetarray[top+1]))) {
            insertPrefetch(machinenum, oid, 2+numoffset-top, &offsetarray[top-2], &head);
           } else {
            insertPrefetch(machinenum, oid, numoffset-top, &offsetarray[top], &head);
           }
        */
      } else {
	sendOidFound(header, oid, sd,writebuffer); 
    LOGTIME('H',oid,TYPE(header),0,top);
	if (top<offstop)
	  //okay to continue going down
	  continue;
      }
    } else if (oid==2) {
      LOGTIME('I',oid,top,0,0);
      //send prefetch first
      int objindex=top+2;
      //forward prefetch
      /*
        int machinenum = lhashSearch(dfsList[objindex]);
        insertPrefetch(machinenum, dfsList[objindex], numoffset-top, &offsetarray[top], &head);
      */
    }
    //oid is 0
    //go backwards until we can increment
    do {
      do {
	top-=2;
	if (top<0) {
	  flushResponses(sd, writebuffer);
	  return head;
	}
      } while(dfsList[top+1] == GET_RANGE(offsetarray[top + 3]));

      header=searchObj(dfsList[top]);
      //header shouldn't be null unless the object moves away, but allow
      //ourselves the option to just continue on if we lose the object
    } while(header==NULL);
    LOGTIME('K',OID(header),TYPE(header),0,top);
    //increment
    dfsList[top+1]++;
  }
  flushResponses(sd, writebuffer);
  return head;
}


INLINE objheader_t *searchObj(unsigned int oid) {
  objheader_t *header;
  if ((header = (objheader_t *)mhashSearch(oid)) != NULL) {
    return header;
  } else {
    header = prehashSearch(oid);
    if(header != NULL &&
       (STATUS(header) & DIRTY)) {
      return NULL;
    }
    return header;
  }
}


INLINE objheader_t *searchObjInv(unsigned int oid, int top, int *countInvalidObj, int *isLastOffset) {
  objheader_t *header;
  if ((header = (objheader_t *)mhashSearch(oid)) != NULL) {
    return header;
  } else {
    header = prehashSearch(oid);
    if(header != NULL) {
      if((STATUS(header) & DIRTY) && (countInvalidObj!= NULL)) {
        if ((*countInvalidObj)==-1) {
          *countInvalidObj=top;
        } else {
          return NULL;
        }
      }
      if((STATUS(header) & DIRTY) && isLastOffset)
        return NULL;
    }
    return header;
  }
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
  struct writestruct writebuffer;
  writebuffer.offset=0;


  /* Send TRANS_PREFETCH control message */
  int first=1;
  //control = TRANS_PREFETCH;
  //send_data(sd, &control, sizeof(char));

  /* Send Oids and offsets in pairs */
  tmp = mcpilenode->list;
  while(tmp != NULL) {
    len = sizeof(int) + sizeof(unsigned int) + sizeof(unsigned int) + ((tmp->numoffset) * sizeof(short));
    char oidnoffset[len+5];
    char *buf=oidnoffset;
    if (first) {
      *buf=TRANS_PREFETCH;
      buf++;len++;
      first=0;
    }
    *((int*)buf) = tmp->numoffset;
    buf+=sizeof(int);
    *((unsigned int *)buf) = tmp->oid;
    buf+=sizeof(unsigned int);
#ifdef TRANSSTATS
    sendRemoteReq++;
#endif
    *((unsigned int *)buf) = mid;
    buf += sizeof(unsigned int);
    memcpy(buf, tmp->offsets, (tmp->numoffset)*sizeof(short));
    tmp = tmp->next;
    if(tmp==NULL) {
      *((int*)(&oidnoffset[len]))=-1;
      len+=sizeof(int);
    }
    if(tmp!=NULL)
      send_buf(sd, &writebuffer, oidnoffset, len);
    else
      forcesend_buf(sd, &writebuffer, oidnoffset, len);
      //send_data(sd, oidnoffset, len);
    //tmp = tmp->next;
  }

  /* Send a special char -1 to represent the end of sending oids + offset pair to remote machine */
  //endpair = -1;
  //send_data(sd, &endpair, sizeof(int));
  return;
}

int getRangePrefetchResponse(int sd, struct readstruct * readbuffer) {
  int length = 0;
  recv_data_buf(sd, readbuffer, &length, sizeof(int));
  int size = length - sizeof(int);
  char recvbuffer[size];
#ifdef TRANSSTATS
  getResponse++;
#endif
  recv_data_buf(sd, readbuffer, recvbuffer, size);
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
        if(((objheader_t *)oldptr)->version < ((objheader_t *)ptr)->version) {
          prehashInsert(oid, ptr);
        }
      } else {
	prehashInsert(oid, ptr);
      }
      ptr=(void *)(((unsigned int)ptr)+objsize);
      size-=objsize;
    }

  } else if(control == OBJECT_NOT_FOUND) {
    oid = *((unsigned int *)(recvbuffer + sizeof(char)));
  } else {
    printf("%s() Error: in Decoding the control value %d, %s\n", __func__, __LINE__, __FILE__);
  }
  return 0;
}

int rangePrefetchReq(int acceptfd, struct readstruct * readbuffer) {
  int numoffset, sd = -1;
  unsigned int baseoid, mid = -1;
  oidmidpair_t oidmid;
  struct writestruct writebuffer;

  while (1) {
    recv_data_buf(acceptfd, readbuffer, &numoffset, sizeof(int));
    if(numoffset == -1)
      break;
    recv_data_buf(acceptfd, readbuffer, &oidmid, 2*sizeof(unsigned int));
    baseoid = oidmid.oid;
    if(mid != oidmid.mid) {
      if(mid!= -1) {
        forcesend_buf(sd, &writebuffer, NULL, 0);
        freeSockWithLock(transPResponseSocketPool, mid, sd);
      }
      mid = oidmid.mid;
      sd = getSockWithLock(transPResponseSocketPool, mid);
      writebuffer.offset=0;
    }
    short offsetsarry[numoffset];
    recv_data_buf(acceptfd, readbuffer, offsetsarry, numoffset*sizeof(short));

    perMcPrefetchList_t * pilehead=processRemote(baseoid, offsetsarry, sd, numoffset, mid, &writebuffer);

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
    forcesend_buf(sd,&writebuffer, NULL, 0);
    freeSockWithLock(transPResponseSocketPool, mid, sd);
  return 0;
}


unsigned int getNextOid(objheader_t * header, short * offsetarray, unsigned int *dfsList, int top, int *countInvalidObj, int *isLastOffset) 
{
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
      header=searchObjInv(dfsList[top+2], top, countInvalidObj, isLastOffset);
      if (header==NULL)
	return 2;
    }

    return *((unsigned int *)(((char *)header) + sizeof(objheader_t) + startindex));
  }
}

void flushResponses(int sd, struct writestruct *writebuffer) {
  if ((writebuffer->offset)>WTOP) {
    send_data(sd, writebuffer->buf, writebuffer->offset);
    writebuffer->offset=0;
  }
}

int sendOidFound(objheader_t * header, unsigned int oid, int sd, struct writestruct *writebuffer) {
  //char *sendbuffer;
  int objsize;
  GETSIZE(objsize, header);
  int size  = sizeof(int) + sizeof(char) + sizeof(unsigned int) +sizeof(objheader_t) + objsize;
  char sendbuffer[size+1];
  sendbuffer[0]=TRANS_PREFETCH_RESPONSE;
  int incr = 1;
  *((int *)(sendbuffer + incr)) = size;
  incr += sizeof(int);
  *((char *)(sendbuffer + incr)) = OBJECT_FOUND;
  incr += sizeof(char);
  *((unsigned int *)(sendbuffer+incr)) = oid;
  incr += sizeof(unsigned int);
  memcpy(sendbuffer + incr, header, objsize + sizeof(objheader_t));
  send_buf(sd, writebuffer, sendbuffer, size+1);

  /*
  //TODO: dead code --- stick it around for sometime
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
  if ((*bufoffset)==0) {
    send_data(sd, sendbuffer, size+incr);
  }
  */
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
