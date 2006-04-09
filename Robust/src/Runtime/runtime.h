#ifndef RUNTIME
#define RUNTIME
#include<stdlib.h>
#include<stdio.h>


void * allocate_new(int type);
void * allocate_newarray(int type, int length);

#endif
