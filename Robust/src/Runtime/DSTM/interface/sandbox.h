#ifndef SANDBOX_H
#define SANDBOX_H

#include <signal.h>
extern __thread chashlistnode_t *c_table;
extern __thread unsigned int c_size;
extern __thread unsigned int c_numelements;

typedef struct elem {
  unsigned int oid;
  unsigned short version;
} elem_t;

typedef struct nodeElem {
 unsigned int mid;
 unsigned int numread;
 unsigned int nummod;
 elem_t *oidread;
 elem_t *oidmod;
 struct nodeElem *next;
} nodeElem_t;


int checktrans();
void errorhandler(int sig, struct sigcontext ctx);
nodeElem_t * makehead(unsigned int numelements);
void deletehead(nodeElem_t *head);
nodeElem_t * createList(nodeElem_t *, objheader_t *, unsigned int, unsigned int);

#endif
