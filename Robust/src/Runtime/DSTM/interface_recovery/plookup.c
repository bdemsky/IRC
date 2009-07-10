#include "plookup.h"
#include "ip.h"
extern int classsize[];

//NOTE: "pile" ptr points to the head of the linked list of the machine pile data structures

/* This function creates a new pile data structure to hold
 * obj ids of objects modified or read inside a transaction,
 * no of objects read and no of objects modified
 * that belong to a single machine */

plistnode_t *pCreate(int objects) {
  plistnode_t *pile;

  //Create main structure
  if((pile = calloc(1, sizeof(plistnode_t))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    return NULL;
  }
  if ((pile->oidmod = calloc(objects, sizeof(unsigned int))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    free(pile);
    return NULL;
  }
  if ((pile->oidcreated = calloc(objects, sizeof(unsigned int))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    free(pile);
    free(pile->oidmod);
    return NULL;
  }
  if ((pile->objread = calloc(objects, sizeof(unsigned int) + sizeof(short))) == NULL) {
    printf("Calloc error %s %d\n", __FILE__, __LINE__);
    free(pile);
    free(pile->oidmod);
    free(pile->oidcreated);
    return NULL;
  }

  pile->nummod = pile->numread = pile->numcreated = pile->sum_bytes = pile->mid = 0;
  pile->next = NULL;
  return pile;
}

//Count the number of machine piles
int pCount(plistnode_t *pile) {
  plistnode_t *tmp;
  int pcount = 0;
  tmp = pile;
  while(tmp != NULL) {
    pcount++;
    tmp = tmp->next;
  }
  return pcount;
}

//Make a list of mid's for each machine group
int pListMid(plistnode_t *pile, unsigned int *list) {
  int i = 0;
  plistnode_t *tmp;
  tmp = pile;
	char ip[16];
  while (tmp != NULL) {
    list[i] = tmp->mid;
    i++;
    tmp = tmp->next;
  }
  return 0;
}

//Delete the entire pile
void pDelete(plistnode_t *pile) {
  plistnode_t *next, *tmp;
  tmp = pile;
  while(tmp != NULL) {
    next = tmp->next;
    free(tmp->oidmod);
    free(tmp->oidcreated);
    free(tmp->objread);
    free(tmp);
    tmp = next;
  }
  return;
}
