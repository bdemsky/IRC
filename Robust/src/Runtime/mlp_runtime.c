#include <stdlib.h>
#include <stdio.h>
#include "mlp_runtime.h"


struct SESE* root;


void mlpIssue();


struct SESE* mlpInit() {
  return root;
}


void mlpEnqueue( struct SESE* sese ) {
  printf( "mlp enqueue\n" );
}

void mlpBlock( struct SESE* sese ) {

}

void mlpNotifyExit( struct SESE* sese ) {
  printf( "mlp notify exit\n" );
}

void mlpIssue() {

}
