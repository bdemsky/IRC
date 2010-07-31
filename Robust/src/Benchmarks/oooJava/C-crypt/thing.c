#include <stdlib.h>
#include <stdio.h>

int main() {

  int z0 = 200000;
  int z1 = 198765;
  int x = (z0 >> 9) | (z1 << 7) & 0xFFFF;

  printf( "x = %d\n", x );

  return 0;
}
