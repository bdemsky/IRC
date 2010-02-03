#include "sanbox.h"
#include "dstm.h"
#include "runtime.h"

/* Do sandboxing */
void errorhandler(int sig, struct sigcontext ctx) {
  //TODO modify this
  if (sig == SIGSEGV) //Invalid memory segment access
      printf("Got signal %d, faulty address is %p, "
                 "from %p\n", sig, ctx.cr2, ctx.eip);
   else
      printf("Got signal %d\n", sig);

  threadhandler(sig, ctx);
}

int checktrans() {
 /* Create info to keep track of numelements */ 
  unsigned int size = c_size;
  chashlistnode_t *curr = c_table;
  int i;
  nodeElem_t *head=NULL;

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
        printf("Error: No such machine %s, %d\n", __FILE__, __LINE__);
        return 0;
      }
      head = createList(head, headeraddr, machinenum, c_numelements);
      curr = curr->next;
    }
  }
  /* Send oid and versions for checking */
  verify();

  //free head
  deletehead(head);
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
        tmp->oidmod[tmp->nummod]->oid = OID(headeraddr);
        tmp->oidmod[tmp->nummod]->version = headeraddr->version;
        tmp->nummod++;
      } else {
        tmp->oidread[tmp->numread]->oid = OID(headeraddr);
        tmp->oidread[tmp->numread]->version = headeraddr->version;
        tmp->numread++;
      }
      found = 1;
      break;
    }
    tmp = tmp->next;
  }
  //Add oid for any new machine
  if (!found) {
    ptr = makehead(c_numelements);
    if((ptr = makehead(c_numelements)) == NULL) {
      return NULL;
    }
    ptr->mid = mid;
    if (STATUS(headeraddr) & DIRTY) {
      ptr->oidmod[tmp->nummod]->oid = OID(headeraddr);
      ptr->oidmod[tmp->nummod]->version = headeraddr->version;
      ptr->nummod++;
    } else {
      ptr->oidread[tmp->numread]->oid = OID(headeraddr);
      ptr->oidread[tmp->numread]->version = headeraddr->version;
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
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return NULL;
  }
  if ((head->oidmod = calloc(numelements, sizeof(elem_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    free(head);
    return NULL;
  }
  if ((head->oidread = calloc(numelements, sizeof(elem_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    free(head);
    free(head->oidmod);
    return NULL;
  }
  head->mid = 0;
  head->nummod = head->numread = 0;
  head->next = NULL;
  return head;
}

//Delete the entire list
void pDelete(nodeElem_t *head) {
  nodeElem_t *next, *tmp;
  tmp = head;
  while(tmp != NULL) {
    next = tmp->next;
    free(tmp->oidmod);
    free(tmp->oidread);
    free(tmp);
    tmp = next;
  }
  return;
}

void verify() {
}
