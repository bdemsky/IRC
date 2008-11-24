#include "prefetch.h"
#include "prelookup.h"

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
    checkIfLocal(node);

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

void checkIfLocal(char *ptr) {
  int siteid = *(GET_SITEID(ptr));
  unsigned int *baseoids = GET_PTR_OID(ptr);
  unsigned int ntuples = *(GET_NTUPLES(ptr));
  unsigned short *endoffsets = GET_PTR_EOFF(ptr, ntuples);
  short *offsets = GET_PTR_ARRYFLD(ptr, ntuples);
  int i, j, k;
  int numLocal = 0;

  prefetchpile_t * head=NULL;

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
          unsigned int * oidarray = NULL; //TODO FILL THIS ARRAY
          int machinenum = lhashSearch(oid);
          insertPile(machinenum, oidarray, numoffset-j, offsets, &head);
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

#if 0
int lookForObjs(unsigned int *oid, short *offset, int *numoids, int *newbase) {
  objheader_t *header;
  if((header = mhashSearch(*oid))!= NULL) {
    //Found on machine
    ;
  } else if((header = prehashSearch(*oid))!=NULL) {
    //Found in prefetch cache
    ;
  } else {
    return 0;
  }

  if(TYPE(header) > NUMCLASSES) {
    int elementsize = classsize[TYPE(header)];
    struct ArrayObject *ao = (struct ArrayObject *) (((char *)header) + sizeof(objheader_t));
    int length = ao->___length___;
        /* Check if array out of bounds */
    if(offset[*newbase] < 0 || offset[*newbase] >= length) {
      //if yes treat the object as found
      (*oid)=0;
      return 1;
    } else {
      if(getOtherOid(header, ao, offset, numoids, newbase))
        return 1;
    }
  } else { //linked list
    //(*oid) = *((unsigned int *)(((char *)header) + sizeof(objheader_t) + offset));
    if(getNext(header, offset, numoids, newbase)) 
      return 1;
    //(*newbase)++;
  }
}

void resolveArrays(unsigned int *arrayOfOids, short *offset, int *numoids, int *newbase) {
  /*
  int i;
  */
}

int getOtherOid(header, ao, offset, numoids, newbase) {
  short range, stride;
  short startindex = offset[*newbase];
  int getnewbaseVal = *newbase + 1;
  if(getnewbaseVal == 0) {
    (*oid) = *((unsigned int *)(((char *)ao) + sizeof(struct ArrayObject) + (elementsize*startindex)));
    (*newbase) = (*newbase) + 2; //skip the immediate offset
    return 1;
  } else if(getnewbaseVal > 0) {
    /* Resolve the oids within a given range */
    (*newbase)++;
    range = GET_RANGE(offset[*newbase]);
    stride = GET_STRIDE((void *)(offset[*newbase]));
    stride = stride + 1; //NOTE 000 => stride = 1, 001 => stride = 2
    int index = 0;
    unsigned int arrayOfOids[range+1];
    if(GET_STRIDEINC(offset[*newbase])) { //-ve stride
      int i;
      for(i = startindex; i <= range; i = i - stride) {
        if(i < 0 || i >= length) {
          //if yes treat the object as found
          (*oid)=0;
          return 1;
        }
        arrayOfOids[index] = *((unsigned int *)(((char *)ao) + sizeof(struct ArrayObject) + (elementsize*i)));
        index++;
      }
    } else { //+ve stride
      int i;
      for(i = startindex; i <= range; i = i + stride) {
        if(i < 0 || i >= length) {
          //if yes treat the object as found
          (*oid)=0;
          return 1;
        }
        arrayOfOids[index] = *((unsigned int *)(((char *)ao) + sizeof(struct ArrayObject) + (elementsize*i)));
        index++;
      }
    }
    //(*oid) = *((unsigned int *)(((char *)ao) + sizeof(struct ArrayObject) + (elementsize*startindex)));
    (*newbase) = (*newbase) + 2; 
    return 1;
  } else {
    ;
  }
}


void checkIfLocal(char *ptr) {
  int siteid = *(GET_SITEID(ptr));
  int ntuples = *(GET_NTUPLES(ptr));
  unsigned int *baseoids = GET_PTR_OID(ptr);
  unsigned short *numoffsets = GET_PTR_EOFF(ptr, ntuples);
  short *offsets = GET_PTR_ARRYFLD(ptr, ntuples);
  prefetchpile_t * head=NULL;
  int numLocal = 0;

  int i ;
  for(i=0; i<ntuples; i++) {
    unsigned short baseindex=(i==0) ? 0 : numoffsets[i -1];
    unsigned short endindex = numoffsets[i];
    unsigned int oid = baseoids[i];
    int numoids = 0;
    if(oid == 0)
      continue;

    //Look up fields locally
    int newbase;
    for(newbase=baseindex; newbase<endindex; ) {
      if(!lookForObjs(&oid, &offsets[newbase], &numoids, &newbase)) {
	break;
      }
      //Ended in a null pointer
      if(oid == 0)
	goto tuple;
    }

    //Add to remote request
    machinenum=lhashSearch(oid);
    // Create an array of oids and offsets
    unsigned int arrayoid[numoids];
    unsigned short arraynumoffset[numoids];
    void *arryfields[numoids];
    for(i = 0; i<numoids; i++) {
      arrayoid[i] = oid;
      arraynumoffset[i] = endindex - newbase;
      arryfields[i] = (void*)(&arryfields[newbase]);
    }
    //insertPile(machinenum, oid, endindex-newbase, &arryfields[newbase], &head);
    insertPile(machinenum, arrayoid, arraynumoffset, arryfields, &head);
tuple:
    ;
  }
}
#endif
