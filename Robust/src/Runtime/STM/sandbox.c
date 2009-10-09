#include "sandbox.h"
#include "tm.h"
#include <stdio.h>
#define likely(x) x
__thread int transaction_check_counter;
__thread jmp_buf aborttrans;
__thread int abortenabled;
__thread int * counter_reset_pointer;

void checkObjects() {
  if (abortenabled&&checktrans()) {
    transaction_check_counter=(*counter_reset_pointer=HIGH_CHECK_FREQUENCY);
    longjmp(aborttrans, 1);
  }
  transaction_check_counter=*counter_reset_pointer;
}

/* Do sandboxing */
void errorhandler(int sig, struct sigcontext ctx) {
  printf("Error\n");
  if (abortenabled&&checktrans())
    longjmp(aborttrans, 1);
  threadhandler(sig, ctx);
}

int checktrans() {
  /* Create info to keep track of objects that can be locked */
  chashlistnode_t *curr = c_list;

  /* Inner loop to traverse the linked list of the cache lookupTable */
  while(likely(curr != NULL)) {
    //if the first bin in hash table is empty
    objheader_t * headeraddr=&((objheader_t *) curr->val)[-1];
    objheader_t *header=(objheader_t *)(((char *)curr->key)-sizeof(objheader_t));
    unsigned int version = headeraddr->version;
    
    if (header->lock==0) {
      return 1;
    }
    CFENCE;
    if (version!=header->version) {
      return 1;
    }
    curr = curr->lnext;
  }
  return 0;
}
