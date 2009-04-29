#include <stdio.h>

void function() {
  static int myInt = 1;
  printf("myInt:%d\n", myInt);
  myInt *= 2;
  printf("myInt:%d\n", myInt);
}

int main(int argc,char *argv[])
{
  function();
  function();
  function();
}
