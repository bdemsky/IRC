#include <stdlib.h>
#include <stdio.h>
#include "mlp_runtime.h"
#include "Queue.h"


static struct Queue* issued;


// each core should have a current SESE
static struct SESErecord* current;


void mlpInit() {
  issued  = createQueue();
  current = NULL;
}


struct SESErecord* mlpGetCurrent() {
  return current;
}


void mlpIssue( struct SESErecord* sese ) {
  addNewItem( issued, (void*) sese );
}

void mlpStall( struct SESErecord* sese ) {
  
}

void mlpNotifyExit( struct SESErecord* sese ) {
  
}

/*
isEmpty(queue)
void* getItem(queue)
*/
