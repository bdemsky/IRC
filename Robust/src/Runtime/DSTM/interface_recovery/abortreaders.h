#ifndef ABORTREADERS_H
#define ABORTREADERS_H
#include "dstm.h"

#define READERSIZE 8

struct readerlist {
  int *array[READERSIZE];
  int numreaders;
  struct readerlist * next;
};

void initreaderlist();
void addtransaction(unsigned int oid);
void removetransaction(unsigned int oidarray[], unsigned int numoids);
void removethistransaction(unsigned int oidarray[], unsigned int numoids);
void removethisreadtransaction(unsigned char* oidverread, unsigned int numoids);
void removetransactionhash();
#endif
