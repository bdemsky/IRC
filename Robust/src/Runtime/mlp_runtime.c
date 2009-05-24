#include <stdlib.h>
#include <stdio.h>
#include "mlp_runtime.h"
#include "Queue.h"


struct Queue* issued;


void mlpInit() {
  issued = createQueue();
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
