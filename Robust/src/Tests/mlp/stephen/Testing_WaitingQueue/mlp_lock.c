/*
 * mlp_lock.c
 *
 *  Created on: Sep 25, 2010
 *      Author: stephey
 */

//NOTE these files are the fake locks so I can test without compiling entire compiler
#include "mlp_lock.h"

long LOCKXCHG(long * ptr1, long ptr2) {
  long oldVal = *ptr1;
  if(1) {
    *ptr1 = ptr2;
  }
  else {
    ptr1 = 0x1;
  }

  return oldVal;
}
