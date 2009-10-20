#include "sandbox.h"
#include "tm.h"
#include <stdio.h>
#include "methodheaders.h"
#include "runtime.h"
__thread int transaction_check_counter;
__thread jmp_buf aborttrans;
__thread int abortenabled;
__thread int * counter_reset_pointer;
#ifdef DELAYCOMP
#include "delaycomp.h"
#endif

void checkObjects() {
  if (abortenabled&&checktrans()) {
    printf("Loop Abort\n");
    transaction_check_counter=(*counter_reset_pointer=HIGH_CHECK_FREQUENCY);
#ifdef TRANSSTATS
    numTransAbort++;
#endif
    freenewobjs();
    objstrReset();
    t_chashreset();
#ifdef READSET
    rd_t_chashreset();
#endif
#ifdef DELAYCOMP
    dc_t_chashreset();
    ptrstack.count=0;
    primstack.count=0;
    branchstack.count=0;
#ifdef STMARRAY
    arraystack.count=0;
#endif
#endif
    _longjmp(aborttrans, 1);
  }
  transaction_check_counter=*counter_reset_pointer;
}

#ifdef D___System______Assert____Z
CALL11(___System______Assert____Z, int ___status___, int ___status___) {
  if (!___status___) {
    if (abortenabled&&checktrans()) {
#ifdef TRANSSTATS
      numTransAbort++;
#endif
      freenewobjs();
      objstrReset();
      t_chashreset();
#ifdef READSET
      rd_t_chashreset();
#endif
#ifdef DELAYCOMP
      dc_t_chashreset();
      ptrstack.count=0;
      primstack.count=0;
      branchstack.count=0;
#ifdef STMARRAY
      arraystack.count=0;
#endif
#endif
      _longjmp(aborttrans, 1);
    }
    printf("Assertion violation\n");
    *((int *)(NULL)); //force stack trace error                         
  }
}
#endif

/* Do sandboxing */
void errorhandler(int sig, struct sigcontext ctx) {
  //  printf("Error\n");
  if (abortenabled&&checktrans()) {
    sigset_t toclear;
    sigemptyset(&toclear);
    sigaddset(&toclear, sig);
    sigprocmask(SIG_UNBLOCK, &toclear,NULL); 
#ifdef TRANSSTATS
    numTransAbort++;
#endif
    freenewobjs();
    objstrReset();
    t_chashreset();
#ifdef READSET
    rd_t_chashreset();
#endif
#ifdef DELAYCOMP
    dc_t_chashreset();
    ptrstack.count=0;
    primstack.count=0;
    branchstack.count=0;
#ifdef STMARRAY
    arraystack.count=0;
#endif
#endif
    _longjmp(aborttrans, 1);
  }
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
