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
  // a[0][1] - a[3][1] = a.0.3
  // a.f.h   = a.f.h
  // a.f.next.h = a.f.0.next.0.h
  // a.f.next.next.h  = a.f.next.2.h
  /* Allocate memory in prefetch queue and push the block there */
  int qnodesize = sizeof(unsigned int) + sizeof(unsigned short) + numoffset * sizeof(short);
  char *node = (char *) getmemory(qnodesize);
  //printf("DEBUG-> %s() oid = %d, numoffset = %d\n", __func__, oid, numoffset);

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
   //printf("DEBUG-> %s() pilehead = %x\n", __func__, pilehead);

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
  short numoffsets = *(GET_NUM_OFFSETS(ptr));
  short *offsets = GET_OFFSETS(ptr);
  unsigned int dfsList[1000];
  int top = -1;
  unsigned int index = 0;

  perMcPrefetchList_t *head = NULL;
  // Insert the oid in DFS L
  dfsList[0] = oid;
  ++top;

  unsigned int node_oid;
  // While DFS L is not empty
  while (top != -1) {
    if(top >= 1000) {
      printf("Error: dfsList size is inadequate %s, %d\n", __func__, __LINE__);
      exit(-1);
    }
    node_oid = dfsList[top];
    --top;
    //printf("%s() DEBUG -> DFS traversal: oid = %d\n", __func__, node_oid);
    // Check if this oid is not local
    if (!checkoid(node_oid)) {
      // Not found 
      int machinenum = lhashSearch(node_oid);
      insertPrefetch(machinenum, node_oid, numoffsets-index, &offsets[index], &head);
    } else { //object is local
      objheader_t *header = searchObj(node_oid);
      if (header == NULL) {
        return NULL;
      }
      // Check if the object is array type 
      if (TYPE(header) > NUMCLASSES) {
        int elementsize = classsize[TYPE(header)];
        struct ArrayObject *ao = (struct ArrayObject *) (((char *)header) + sizeof(objheader_t));
        int length = ao->___length___;
        int startelement, range;
        if(index < numoffsets) {
          startelement = offsets[index];
          range = GET_RANGE(offsets[index+1]);
        } else {
          goto check;
        }
        if (range > length) {
          printf("Error: Illegal range= %d when length = %d at %s %d\n", range, length, __func__, __LINE__);
          return NULL;
        }
        if (range > 0) {
          short stride = GET_STRIDE(offsets[index+1]);
          stride++; // Note bit pattern 000 => stride = 1 etc...
          // Check if stride is +ve or -ve
          int sign;
          if (GET_STRIDEINC(offsets[index+1])) { // -ve stride
            sign = -1;
          } else {
            sign = 1;
          }
          int i;
          //printf("DEBUG-> %s() stride = %d, sign = %d\n", __func__, stride, sign);
          for (i = 0; i <= range; i++) {
            oid = *((unsigned int *)(((char *)ao) + sizeof(struct ArrayObject) + \
                  (elementsize * (startelement + (sign*stride*i)))));
            //printf("DEBUG-> %s() Array oid = %d, range = %d\n", __func__, oid, range);
            ++top;
            dfsList[top] = oid;
          }
        } else if (range == 0) { // for range == 0
          oid = *((unsigned int *)(((char *)ao) + sizeof(struct ArrayObject) \
                + (elementsize*startelement)));
          //printf("DEBUG-> %s() Array oid = %d, range = %d\n", __func__, oid, range);
          ++top;
          dfsList[top] = oid;
        } else {
          printf("Error: Illegal range -ve %s %d\n", __func__, __LINE__);
          return NULL;
        }
        if(index < numoffsets)
          index += 2; // Point to the next offset
      } else { // The object is linked list
        int startelement, range;
        if(index < numoffsets) {
          startelement = offsets[index];
          range = GET_RANGE(offsets[index+1]);
        } else {
          goto check;
        }
        oid = *((unsigned int *)(((char *)header) + sizeof(objheader_t) + startelement));
        //printf("DEBUG-> %s() First element of linked list oid = %d, range = %d, index = %d\n", __func__, oid, range, index);
        ++top;
        dfsList[top] = oid;
        int i;
        if (range > 0) {
          for (i = 0; i < range; i++) {
            if (checkoid(oid)) {
              header = searchObj(oid);
              if (header == NULL) {
                return NULL;
              }
              oid = *((unsigned int *)(((char *)header) + sizeof(objheader_t) + startelement));
              //printf("DEBUG-> %s()linked list oid = %d, range = %d\n", __func__, oid, range);
              ++top;
              dfsList[top] = oid;
            } else { //Send Object not found
              //Update range
              offsets[index+1] = (offsets[index+1] & 0x0fff) -1;
              break;
            } 
          }
        } else if(range == 0) {
          ;
        } else {
          printf("Error: Illegal range -ve %s %d\n", __func__, __LINE__);
          return NULL;
        }
        if(i == range && index < numoffsets)
          index += 2; // Point to the next offset
      } // end if check object type
    }
check: 
    //Oid found locally
    ;
  }//end of while
  return head;
}

objheader_t *searchObj(unsigned int oid) {
  objheader_t *header = NULL;

  if ((header = (objheader_t *)mhashSearch(oid)) != NULL) {
    return header;
  } else if ((header = (objheader_t *) prehashSearch(oid)) != NULL) {
    return header;
  } else {
    printf("Error: Cannot find header %s, %d\n", __func__, __LINE__);
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
  //printf("DEBUG-> %s() oid = %d, numoffset = %d\n", __func__, oid, numoffset);

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
    //printf("DEBUG->%s() tmp->oid = %d, tmp->numoffset = %d\n", __func__, tmp->oid, tmp->numoffset);
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
    //printf("DEBUG->%s() Getting oid = %d from remote machine\n", __func__, oid);
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
  unsigned int oid, mid = -1;
  oidmidpair_t oidmid;

  while (1) {
    recv_data(acceptfd, &numoffset, sizeof(int));
    if(numoffset == -1)
      break;
    recv_data(acceptfd, &oidmid, 2*sizeof(unsigned int));
    oid = oidmid.oid;
    //printf("DEBUG-> %s() starting oid = %d\n", __func__, oid);
    if(mid != oidmid.mid) {
      if(mid!= -1)
        freeSockWithLock(transPResponseSocketPool, mid, sd);
      mid = oidmid.mid;
      sd = getSockWithLock(transPResponseSocketPool, mid);
    }

    short offsetsarry[numoffset];
    recv_data(acceptfd, offsetsarry, numoffset*sizeof(short));

    /* Obj not found */
    objheader_t *header;
    if((header = (objheader_t *)mhashSearch(oid)) == NULL) {
      int size = sizeof(int) + sizeof(char) + sizeof(unsigned int);
      char sendbuffer[size];
      *((int *) sendbuffer) = size;
      *((char *)(sendbuffer + sizeof(int))) = OBJECT_NOT_FOUND;
      *((unsigned int *)(sendbuffer + sizeof(int) + sizeof(unsigned int))) = oid;
      char control = TRANS_PREFETCH_RESPONSE;
      sendPrefetchResponse(sd, &control, sendbuffer, &size);
      break;
    } else { //Obj found
      int retval;
      if((retval = sendOidFound(oid, sd)) != 0) {
        printf("%s() Error in sendOidFound() at line %d in %s()\n", __func__, __LINE__, __FILE__);
        return -1;
      }
      if((retval = processOidFound(header, offsetsarry, 0, sd, numoffset)) != 0) {
        printf("%s() Error: in processOidFound() at line %d in %s()\n",
            __func__, __LINE__, __FILE__);
        return -1;
      }
    }
  }
  //Release socket
  if(mid!=-1)
    freeSockWithLock(transPResponseSocketPool, mid, sd);
  return 0;
}

int processOidFound(objheader_t *header, short * offsetsarry, int index, int sd, int numoffset) {
  unsigned int dfsList[1000];
  int top = -1;
  dfsList[0] = OID(header);
  ++top;

  while(top != -1) {
    if(top >= 1000) {
      printf("Error: dfssList size is inadequate %s, %d\n", __func__, __LINE__);
      exit(-1);
    }
    int node_oid = dfsList[top];
    --top;
    printf("DEBUG-> %s() DFS traversal oid = %d\n", __func__, node_oid);
    fflush(stdout);
    //Check if oid is local
    if(!checkoid(node_oid)) {
      int retval;
      if((retval = sendOidNotFound(node_oid, sd)) != 0) {
        printf("%s() Error in sendOidNotFound() at line %d in %s()\n", __func__, __LINE__, __FILE__);
        return -1;
      }
    } else { //Oid is local
      objheader_t *objhead = searchObj(node_oid);
      //printf("DEBUG->%s() objhead = %x, oid = %d\n", __func__, objhead, OID(objhead));
      if(objhead == NULL) {
        printf("Object header is NULL Should not happen at %s, %d\n", __func__, __LINE__);
        return -1;
      }
      int retval;
      if((retval = sendOidFound(OID(objhead), sd)) != 0) {
        printf("%s() Error in sendOidFound() at line %d in %s()\n", __func__, __LINE__, __FILE__);
        return -1;
      }
      //Array type
      if(TYPE(objhead) > NUMCLASSES) {
        int elementsize = classsize[TYPE(objhead)];
        struct ArrayObject *ao = (struct ArrayObject *) (((char *)objhead) + sizeof(objheader_t));
        int length = ao->___length___;
        int startelement, range;
        if(index < numoffset) {
          startelement = offsetsarry[index];
          range = GET_RANGE(offsetsarry[index+1]);
        } else {
          goto end;
        }
        if(range > length) {
          printf("Error: Illegal range = %d when length = %d at %s %d\n", range, length, __func__, __LINE__);
          return -1;
        }
        if(range > 0) {
          short stride = GET_STRIDE(offsetsarry[index+1]);
          stride++; //Note bit pattern 000 => stride = 1 etc
          //check is stride is +ve or -ve
          int sign;
          if(GET_STRIDEINC(offsetsarry[index+1])) {
            sign = -1;
          } else {
            sign = 1;
          }
          int i;
          //printf("DEBUG-> %s() stride = %d, sign = %d\n", __func__, stride, sign);
          for(i = 0; i<=range; i++) {
            unsigned int oid = *((unsigned int *)(((char *)ao) + sizeof(struct ArrayObject) \
                  + (elementsize * (startelement + (sign*stride*i)))));
            //printf("DEBUG-> %s() Array oid = %d, range = %d\n", __func__, oid, range);
            ++top;
            dfsList[top] = oid;
          }
        } else if (range == 0) { //for range == 0
          unsigned int oid = *((unsigned int *)(((char *)ao) + sizeof(struct ArrayObject) \
                + (elementsize*startelement)));
          //printf("DEBUG-> %s() Array oid = %d, range = %d\n", __func__, oid, range);
          ++top;
          dfsList[top] = oid;
        } else {
          printf("Error: Illegal range = %d at %s, %d\n", 
              range, __func__, __LINE__);
          return -1;
        }
        if(index < numoffset)
          index += 2;// Point to the next offset
      } else { //Linked list
        int startelement, range;
        if(index < numoffset) {
          startelement = offsetsarry[index];
          range = GET_RANGE(offsetsarry[index + 1]);
        } else {
          goto end;
        }
        unsigned int oid = *((unsigned int *)(((char *)objhead) + sizeof(objheader_t) 
              + startelement)); 
        //printf("DEBUG-> %s() First linked list element oid = %d, range = %d\n", __func__, oid, range);
        ++top;
        dfsList[top] = oid;
        int i;
        if (range > 0) {
          for(i = 0; i < range; i++) {
            if(checkoid(oid)) {
              objheader_t *head = searchObj(oid);
              if(head == NULL)
                return -1;
              oid = *((unsigned int *)(((char *)head) + sizeof(objheader_t) +
                    startelement));
              //printf("DEBUG-> %s() linked list oid = %d\n", __func__, oid); 
              ++top;
              dfsList[top] = oid;
            }else {
              break;
            }
          }
        } else if(range == 0) {
          ;
        } else {
          printf("Error: Illegal range -ve %s %d\n", __func__, __LINE__);
          return -1;
        }
        if(i == range && index < numoffset)
          index += 2;
      }
    } 
end:
    //Process next oid in the dfs List
    ;
  }//end of while
  return 0;
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

