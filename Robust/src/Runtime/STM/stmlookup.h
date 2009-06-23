#ifndef _CLOOKUP_H_
#define _CLOOKUP_H_

#include <stdlib.h>
#include <stdio.h>

#ifndef INTPTR
#ifdef BIT64
#define INTPTR long
#else
#define INTPTR int
#endif
#endif

#define CLOADFACTOR 0.25
#define CHASH_SIZE 1024

#define INLINE    inline __attribute__((always_inline))


typedef struct chashlistnode {
  void * key;
  void * val;     //this can be cast to another type or used to point to a larger structure
  struct chashlistnode *next;
  struct chashlistnode *lnext;
} chashlistnode_t;

typedef struct chashtable {
  chashlistnode_t *table;       // points to beginning of hash table
  unsigned int size;
  unsigned int mask;
  unsigned int numelements;
  unsigned int threshold;
  double loadfactor;
} chashtable_t;

#define NUMCLIST 250
typedef struct clist {
  struct chashlistnode array[NUMCLIST];
  int num;
  struct clist *next;
} cliststruct_t;


void t_chashCreate(unsigned int size, double loadfactor);
void t_chashInsert(void * key, void *val);
void * t_chashSearch(void * key);
unsigned int t_chashResize(unsigned int newsize);
void t_chashDelete();
void t_chashreset();


extern __thread chashlistnode_t *c_table;
extern __thread chashlistnode_t *c_list;
extern __thread unsigned int c_size;
extern __thread unsigned INTPTR c_mask;
extern __thread unsigned int c_numelements;
extern __thread unsigned int c_threshold;
extern __thread double c_loadfactor;
extern __thread cliststruct_t *c_structs;

#ifdef DELAYCOMP
extern __thread chashlistnode_t *dc_c_table;
extern __thread chashlistnode_t *dc_c_list;
extern __thread unsigned int dc_c_size;
extern __thread unsigned INTPTR dc_c_mask;
extern __thread unsigned int dc_c_numelements;
extern __thread unsigned int dc_c_threshold;
extern __thread double dc_c_loadfactor;
extern __thread cliststruct_t *dc_c_structs;

void dc_t_chashCreate(unsigned int size, double loadfactor);
void dc_t_chashInsertOnce(void * key, void *val);
void * dc_t_chashSearch(void * key);
unsigned int dc_t_chashResize(unsigned int newsize);
void dc_t_chashDelete();
void dc_t_chashreset();
#endif

#endif
