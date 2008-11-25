#include "prefetch.h"
#include "prelookup.h"
#include "sockpool.h"

extern sockPoolHashTable_t *transPrefetchSockPool;
extern unsigned int myIpAddr;

/* Steps for the new prefetch call */
// Function for new prefetch call
void rangePrefetch(int prefetchsiteid, int ntuples, unsigned int *baseoids, 
    unsigned short *numoffsets, short *offsets) {
  // a[0][1] - a[3][1] = a.0.3
  // a.f.h   = a.f.h
  // a.f.next.h = a.f.0.next.0.h
  // a.f.next.next.h  = a.f.next.2.h
  /* Allocate memory in prefetch queue and push the block there */
  int qnodesize = 2*sizeof(int)+ ntuples *(sizeof(unsigned int) + sizeof(unsigned short)) + numoffsets[ntuples -1] * sizeof(short);
  char *node = (char *) getmemory(qnodesize);
  int top = numoffsets[ntuples -1];

  if(node == NULL)
    return;

  int index = 0;
  *((int *)(node)) = prefetchsiteid;
  *((int *)(node + sizeof(int))) = ntuples;
  index = 2 * sizeof(int);
  memcpy(node+index, baseoids, ntuples * sizeof(unsigned int));
  index = index + ntuples *(sizeof(unsigned int));
  memcpy(node+index, numoffsets, ntuples * sizeof(unsigned short));
  index = index + ntuples *(sizeof(unsigned short));
  memcpy(node+index, offsets, top * sizeof(short));

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

perMcPrefetchList_t*  checkIfLocal(char *ptr) {
  int siteid = *(GET_SITEID(ptr));
  unsigned int *baseoids = GET_PTR_OID(ptr);
  unsigned int ntuples = *(GET_NTUPLES(ptr));
  unsigned short *endoffsets = GET_PTR_EOFF(ptr, ntuples);
  short *offsets = GET_PTR_ARRYFLD(ptr, ntuples);
  int i, j, k;
  int numLocal = 0;

  perMcPrefetchList_t * head=NULL;

  // Iterate for each object
  for (i = 0; i < ntuples; i++) {
    int numoffset = (i == 0) ? endoffsets[0] : (endoffsets[i] - endoffsets[i-1]);
    int sizetmpObjSet = numoffset >> 1; 
    unsigned short tmpobjset[sizetmpObjSet];
    int l;
    for (l = 0; l < sizetmpObjSet; l++) {
      tmpobjset[l] = GET_RANGE(offsets[2*l+1]);
    }
    int maxChldOids = getsize(tmpobjset, sizetmpObjSet)+1;
    unsigned int chldOffstFrmBase[maxChldOids];
    chldOffstFrmBase[0] = baseoids[i];
    int tovisit = 0, visited = -1;
    // Iterate for each element of offsets
    for (j = 0; j < numoffset; j++) {
      // Iterate over each element to be visited
      while (visited != tovisit) {
        if(chldOffstFrmBase[visited+1] == 0) {
          visited++;
          continue;
        }

        if (!isOidAvail(chldOffstFrmBase[visited+1])) { 
          // Add to remote requests 
          unsigned int oid = chldOffstFrmBase[visited+1];
          int machinenum = lhashSearch(oid);
          //TODO Group a bunch of oids to send in one prefetch request
          insertPrefetch(machinenum, oid, numoffset-j, offsets, &head);
          break;
        } else {
          // iterate over each offset
          int retval;
          if((retval = lookForObjs(chldOffstFrmBase, offsets, &j, 
              &visited, &tovisit)) == 0) {
            printf("%s() Error: Object not found %s at line %d\n", 
                __func__, __FILE__, __LINE__); 
          }
        }
        visited++;
      } 
    } // end iterate for each element of offsets

    //Entire prefetch found locally
    if(j == numoffset) {
      numLocal++;
      goto tuple;
    }
tuple:
    ;
  } // end iterate for each object

  /* handle dynamic prefetching */
  handleDynPrefetching(numLocal, ntuples, siteid);
  return head;
}

int isOidAvail(unsigned int oid) {
  objheader_t * header;
  if((header=(objheader_t *)mhashSearch(oid))!=NULL) {
    //Found on machine
    return 1;
  } else if ((header=(objheader_t *)prehashSearch(oid))!=NULL) {
    return 1;
  } else {
    return 0;
  }
}

int lookForObjs(int *chldOffstFrmBase, short *offsets, 
    int *index, int *visited, int *tovisit) {
  objheader_t *header;
  unsigned int oid = chldOffstFrmBase[*visited+1];
  if((header = (objheader_t *)mhashSearch(oid))!= NULL) {
    //Found on machine
    ;
  } else if((header = (objheader_t *)prehashSearch(oid))!=NULL) {
    //Found in prefetch cache
    ;
  } else {
    printf("DEBUG->%s()THIS SHOULD NOR HAPPEN\n", __func__);
    return 0;
  }

  if(TYPE(header) > NUMCLASSES) {
    int elementsize = classsize[TYPE(header)];
    struct ArrayObject *ao = (struct ArrayObject *) (((char *)header) + sizeof(objheader_t));
    int length = ao->___length___;
    /* Check if array out of bounds */
    int startindex = offsets[*index];
    int range = GET_RANGE(offsets[(*index)+1]);
    if(range > 0 && range < length) {
      short stride = GET_STRIDE(offsets[(*index)+1]);
      stride = stride + 1; //NOTE  bit pattern 000 => stride = 1, 001 => stride = 2
      int i;
      //check is stride +ve or negative
      if(GET_STRIDEINC(offsets[(*index)]+1)) { //-ve stride
        for(i = startindex; i <= range+1; i = i - stride) {
          unsigned int oid = 0;
          if((i < 0 || i >= length)) {
            //if yes treat the object as found
            oid = 0;
            continue;
          } else {
            // compute new object
            oid = *((unsigned int *)(((char *)ao) + sizeof(struct ArrayObject) + (elementsize*i)));
          }
          // add new object
          chldOffstFrmBase[*tovisit] = oid;
          *tovisit = *tovisit + 1;
        }
      } else { //+ve stride
        for(i = startindex; i <= range; i = i + stride) {
          unsigned int oid = 0;
          if(i < 0 || i >= length) {
            //if yes treat the object as found
            oid = 0;
            continue;
          } else {
            // compute new object
            oid = *((unsigned int *)(((char *)ao) + sizeof(struct ArrayObject) + (elementsize*i)));
          }
          // add new object
          chldOffstFrmBase[*tovisit] = oid;
          *tovisit = *tovisit + 1;
        }
      }
    } else if(range == 0) {
      if(startindex >=0 || startindex < length) {
        unsigned int oid = *((unsigned int *)(((char *)ao) + sizeof(struct ArrayObject) + (elementsize*startindex)));
        // add new object
        chldOffstFrmBase[*tovisit] = oid;
        *tovisit = *tovisit + 1;
      }
    }
  } else { //linked list
    int startindex = offsets[*index];
    int range = GET_RANGE(offsets[(*index)+1]);
    unsigned int oid;
    if(range == 0) {
      oid = *((unsigned int *)(((char *)header) + sizeof(objheader_t) + startindex));
      // add new object
      chldOffstFrmBase[*tovisit] = oid;
      *tovisit = *tovisit + 1;
    } else {
      int i;
      for(i = 0; i < range; i++) {
        oid = *((unsigned int *)(((char *)header) + sizeof(objheader_t) + startindex));
        // add new object
        chldOffstFrmBase[*tovisit] = oid;
        *tovisit = *tovisit + 1;
      }
    }
  }
  *index = *index + 2;
  return 1;
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
