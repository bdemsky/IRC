#ifndef RUNTIME
#define RUNTIME
#include<stdlib.h>
#include<stdio.h>


void * allocate_new(int type);
struct ArrayObject * allocate_newarray(int type, int length);
struct ___String___ * NewString(char *str,int length);

void failedboundschk();
void failednullptr();
void flagor(void * ptr, int ormask);
void flagand(void * ptr, int andmask);

#endif
