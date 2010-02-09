#include "sandbox.h"
#include "runtime.h"
#include "methodheaders.h"
__thread int transaction_check_counter;
__thread jmp_buf aborttrans;
__thread int abortenabled;
__thread int * counter_reset_pointer;
extern unsigned int myIpAddr;
extern sockPoolHashTable_t *transRequestSockPool;

/* Do sandboxing */
void errorhandler(int sig, struct sigcontext ctx) {
  if (abortenabled&&checktrans()) {
    sigset_t toclear;
    sigemptyset(&toclear);
    sigaddset(&toclear, sig);
    sigprocmask(SIG_UNBLOCK, &toclear,NULL); 
#ifdef TRANSSTATS
    numTransAbort++;
#endif
    objstrDelete(t_cache);
    t_chashDelete();
    _longjmp(aborttrans, 1);
  }
  printf("Error in System at %s, %s(), %d\n", __FILE__, __func__, __LINE__);
  print_trace();
  threadhandler(sig, ctx);
}


/* 
 * returns 0 when read set objects are consistent
 * returns 1 when objects are inconsistent
 */
int checktrans() {
 /* Create info to keep track of numelements */ 
  unsigned int size = c_size;
  chashlistnode_t *ptr = c_table;
  int i;
  nodeElem_t *head=NULL;

  numNode = 0;
  for(i = 0; i< size; i++) {
    chashlistnode_t *curr = &ptr[i];
    /* Inner loop to traverse the linked list of the cache lookupTable */
    while(curr != NULL) {
      if (curr->key == 0)
        break;
      objheader_t *headeraddr=(objheader_t*) curr->val;
      unsigned int machinenum;
      if (STATUS(headeraddr) & NEW || (mhashSearch(curr->key) != NULL)) {
        machinenum = myIpAddr;
      } else if ((machinenum = lhashSearch(curr->key)) == 0) {
        printf("Error: No such machine %s, %d\n", __func__, __LINE__);
        return 1;
      }
      if(machinenum != myIpAddr)
        head = createList(head, headeraddr, machinenum, c_numelements);
      curr = curr->next;
    }
  }
  /* Send oid and versions for checking */
  int retval=-1;
  if(head != NULL) {
    retval = verify(head);
  }

  if(retval == 1) { //consistent objects
    /* free head */
    deletehead(head);
    return 0;
  }

  if(retval == 0) {
    /* free head */
    deletehead(head);
    return 1; //return 1 when objects are inconsistent
  }

  return 0; 
}

nodeElem_t * createList(nodeElem_t *head, objheader_t *headeraddr, unsigned int mid,
    unsigned int c_numelements) {

  nodeElem_t *ptr, *tmp;
  int found = 0, offset = 0;
  tmp = head;

  while(tmp != NULL) {
    if(tmp->mid == mid) {
      if (STATUS(headeraddr) & DIRTY) {
        offset = (sizeof(unsigned int) + sizeof(short)) * tmp->nummod;
        *((unsigned int *)(((char *)tmp->objmod) + offset))=OID(headeraddr);
        offset += sizeof(unsigned int);
        *((unsigned short *)(((char *)tmp->objmod) + offset)) = headeraddr->version;
        tmp->nummod++;
      } else {
        offset = (sizeof(unsigned int) + sizeof(short)) * tmp->numread;
        *((unsigned int *)(((char *)tmp->objread) + offset))=OID(headeraddr);
        offset += sizeof(unsigned int);
        *((unsigned short *)(((char *)tmp->objread) + offset)) = headeraddr->version;
        tmp->numread++;
      }
      found = 1;
      break;
    }
    tmp = tmp->next;
  }
  //Add oid for any new machine
  if (!found) {
    numNode++;
    ptr = makehead(c_numelements);
    if((ptr = makehead(c_numelements)) == NULL) {
      printf("Error in Allocating memory %s, %d\n", __func__, __LINE__);
      return NULL;
    }
    ptr->mid = mid;
    if (STATUS(headeraddr) & DIRTY) {
      offset = (sizeof(unsigned int) + sizeof(short)) * ptr->nummod;
      *((unsigned int *)(((char *)ptr->objmod) + offset))=OID(headeraddr);
      offset += sizeof(unsigned int);
      *((unsigned short *)(((char *)ptr->objmod) + offset)) = headeraddr->version;
      ptr->nummod++;
    } else {
      offset = (sizeof(unsigned int) + sizeof(short)) * ptr->numread;
      *((unsigned int *)(((char *)ptr->objread) + offset))=OID(headeraddr);
      offset += sizeof(unsigned int);
      *((unsigned short *)(((char *)ptr->objread) + offset)) = headeraddr->version;
      ptr->numread++;
    }
    ptr->next = head;
    head = ptr;
  }
  return head;
}

nodeElem_t * makehead(unsigned int numelements) {
  nodeElem_t *head;
  //Create the first element 
  if((head = calloc(1, sizeof(nodeElem_t))) == NULL) {
    printf("Calloc error %s %d\n", __func__, __LINE__);
    return NULL;
  }
  
  if ((head->objmod = calloc(numelements,sizeof(unsigned int) + sizeof(unsigned short))) == NULL) {
    printf("Calloc error %s %d\n", __func__, __LINE__);
    free(head);
    return NULL;
  }

  if ((head->objread = calloc(numelements,sizeof(unsigned int) + sizeof(unsigned short))) == NULL) {
    printf("Calloc error %s %d\n", __func__, __LINE__);
    free(head);
    free(head->objmod);
    return NULL;
  }

  head->mid = 0;
  head->nummod = head->numread = 0;
  head->next = NULL;
  return head;
}

//Delete the entire list
void deletehead(nodeElem_t *head) {
  nodeElem_t *next, *tmp;
  tmp = head;
  while(tmp != NULL) {
    next = tmp->next;
    free(tmp->objmod);
    free(tmp->objread);
    free(tmp);
    tmp = next;
  }
  return;
}


/* returns 0 => Inconsistent Objects found, abort transaction */
/* returns 1 => consistent objects found, error in system */
/* Process the linked list of objects */
int verify(nodeElem_t *pile) {
  /* create and initialize an array of sockets and reply receiving buffer */
  int sock[numNode];
  char getReplyCtrl[numNode];
  int i;
  for(i=0; i<numNode; i++) {
    sock[i] = 0;
    getReplyCtrl[i] = 0;
  }

  /* send objects for consistency check to remote machine */
  objData_t tosend[numNode];
  int pilecount = 0;
  while(pile != NULL) {
    /* send total bytes */
    tosend[pilecount].control = CHECK_OBJECTS;  
    tosend[pilecount].numread = pile->numread;  
    tosend[pilecount].nummod = pile->nummod;  
    int sd = 0;
    if((sd = getSock2WithLock(transRequestSockPool, pile->mid)) < 0) {
      printf("Error: Getting a socket descriptor at %s(), %s(), %d\n", __FILE__, __func__, __LINE__);
      exit(-1);
    }
    sock[pilecount] = sd;

    /* Send starting information of data */
    send_data(sd, &(tosend[pilecount]), sizeof(objData_t));

    int size;
    /* Send objetcs that are read */
    {
      size=(sizeof(unsigned int)+sizeof(unsigned short)) * pile->numread;
      send_data(sd, (char *)pile->objread, size);
    }

    /* Send objects that are modified */
    {
      size=(sizeof(unsigned int)+sizeof(unsigned short)) * pile->nummod;
      send_data(sd, (char *)pile->objmod, size);
    }
    pilecount++;
    pile = pile->next;
  }// end of pile processing

  int checkObj = 0;
  int countConsistent = 0;

  /* Recv replies from remote machines */
  for(i = 0; i<numNode; i++) {
    int sd = sock[i];
    if(sd != 0) {
      char control;
      recv_data(sd, &control, sizeof(char));
      getReplyCtrl[i] = control;
      if(control == OBJ_INCONSISTENT) { /* Inconsistent */
        checkObj = 1;
        break;
      }
      countConsistent++;
    }
  }

  /* Decide final response */
  if(checkObj) {
    printf("Inconsistent Object-> Abort Transaction\n");
    return 0;
  }

  if(countConsistent == numNode) {
    return 1;
  }

  return -1;
}

void checkObjects() {
  if (abortenabled&&checktrans()) {
    printf("Loop Abort\n");
    transaction_check_counter=(*counter_reset_pointer=HIGH_CHECK_FREQUENCY);
#ifdef TRANSSTATS
    numTransAbort++;
#endif
    objstrDelete(t_cache);
    t_chashDelete();
    _longjmp(aborttrans, 1);
  }
  transaction_check_counter=*counter_reset_pointer;
}

/* Obtain a backtrace and print it to stdout */
void print_trace() {
  void *array[100];
  size_t size;
  char ** strings;
  size_t i;

  size = backtrace(array, 100);
  strings = backtrace_symbols(array, size);

  printf ("Obtained %zd stack frames.\n", size);
  for (i = 0; i < size; i++)
    printf ("%s\n", strings[i]);
  free (strings);
}

void checkObjVersion(struct readstruct * readbuffer, int sd, unsigned int numread, unsigned int nummod) {

  int v_match=0;

  /* Recv objects read with versions */
  int size=(sizeof(unsigned int)+sizeof(unsigned short)) * numread;
  char objread[size];
  if(numread != 0) {
    recv_data_buf(sd, readbuffer, objread, size);
  }

  /* Recv objects modified with versions */
  size=(sizeof(unsigned int)+sizeof(unsigned short)) * nummod;
  char objmod[size];
  if(nummod != 0) {
    recv_data_buf(sd, readbuffer, objmod, size);
  }

  int i;
  char control;
  for(i=0; i<numread; i++) {
    size = sizeof(unsigned int)+sizeof(unsigned short);
    size *= i;
    unsigned int oid = *((unsigned int *)(objread + size));
    size += sizeof(unsigned int);
    unsigned short version = *((unsigned short *)(objread + size));
    objheader_t *header;
    if((header = mhashSearch(oid)) == NULL) {    /* Obj not found */
      control = OBJ_INCONSISTENT;
      send_data(sd, &control, sizeof(char));
      return;
    } else {
      if(is_write_locked(STATUSPTR(header))) { //object write locked
        control = OBJ_INCONSISTENT;
        send_data(sd, &control, sizeof(char));
        return;
      }
      CFENCE;
      //compare versions
      if(version == header->version)
        v_match++;
      else {
        control = OBJ_INCONSISTENT;
        send_data(sd, &control, sizeof(char));
        return;
      }
    }
  } // end of objects read 

  for(i=0; i<nummod; i++) {
    //unsigned int oid = objmod[i].oid;
    //unsigned short version = objmod[i].version;
    size = sizeof(unsigned int)+sizeof(unsigned short);
    size *= i;
    unsigned int oid = *((unsigned int *)(objmod + size));
    size += sizeof(unsigned int);
    unsigned short version = *((unsigned short *)(objmod + size));
    objheader_t *header;
    if((header = mhashSearch(oid)) == NULL) {    /* Obj not found */
      control = OBJ_INCONSISTENT;
      send_data(sd, &control, sizeof(char));
      return;
    } else {
      if(is_write_locked(STATUSPTR(header))) { //object write locked
        control = OBJ_INCONSISTENT;
        send_data(sd, &control, sizeof(char));
        return;
      }
      //compare versions
      if(version == header->version)
        v_match++;
      else {
        control = OBJ_INCONSISTENT;
        send_data(sd, &control, sizeof(char));
        return;
      }
    }
  } // end of objects modified

  if(v_match = (numread + nummod)) {
    control = OBJ_CONSISTENT;
    send_data(sd, &control, sizeof(char));
  }
  return;
}
