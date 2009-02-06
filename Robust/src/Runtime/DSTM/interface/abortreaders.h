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
void removethistransaction(unsigned int oidarray[], unsigned int numoids, struct transrecord * trans);
void removethisreadtransaction(unsigned char* oidverread, unsigned int numoids, struct transrecord * trans);
void removetransactionhash(chashtable_t *table, struct transrecord *trans);
#endif
