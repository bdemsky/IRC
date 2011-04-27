#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include "dstm.h"

#define size 1000000


obj_addr_table_t mlut;
int classsize[]={sizeof(int),sizeof(char),sizeof(short), sizeof(void *)};

int main() {
  int i;

  dstm_init();
  create_objstr(size);
  createHash(&mlut, HASH_SIZE, 0.75);

  for(i=0; i< 4; i++) {
    createObject(i);
  }

  createObject(3);
  return 0;
}
