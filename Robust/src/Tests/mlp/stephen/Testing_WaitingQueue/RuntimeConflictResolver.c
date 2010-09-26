/*
 * RuntimeConflictResolver.c
 *
 *  Created on: Sep 25, 2010
 *      Author: stephey
 */

#include <stdio.h>
#include "RuntimeConflictResolver.h"

void traverse1(void * inVar) {
  printf("Traverser 1 Ran\n");
}
void traverse2(void * inVar){
  printf("Traverser 2 Ran\n");
}
void traverse3(void * inVar){
  printf("Traverser 3 Ran\n");
}

int traverse(void * startingPtr, int traverserID) {
//  printf("Traverser %u Ran\n", traverserID);

  return 2;
}
