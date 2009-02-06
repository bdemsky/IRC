#ifndef ABORTREADERS_H
#define ABORTREADERS_H
#include "dstm.h"

#define READERSIZE 8

struct readerlist {
  struct transrecord *array[READERSIZE];
  int numreaders;
  struct readerlist * next;
};

void initreaderlist();
void addtransaction(unsigned int oid, struct transrecord * trans);
void removetransaction(unsigned int oidarray[], unsigned int numoids);
void removeaborttransaction(unsigned int oidarray[], unsigned int numoids, struct transrecord * trans);

#endif
