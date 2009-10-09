#include"tm.h"

/* ======================================
 * objstrCreate
 * - create an object store of given size
 * ======================================
 */
objstr_t *objstrCreate(unsigned int size) {
  objstr_t *tmp;
  if((tmp = calloc(1, (sizeof(objstr_t) + size))) == NULL) {
    printf("%s() Calloc error at line %d, %s\n", __func__, __LINE__, __FILE__);
    return NULL;
  }
  tmp->size = size;
  tmp->next = NULL;
  tmp->top = tmp + 1; //points to end of objstr_t structure!
  return tmp;
}

void objstrReset() {
  while(t_cache->next!=NULL) {
    objstr_t *next=t_cache->next;
    t_cache->next=t_reserve;
    t_reserve=t_cache;
    t_cache=next;
  }
  t_cache->top=t_cache+1;
#ifdef STMSTATS
  t_objnumcount=0;
#endif
}

//free entire list, starting at store
void objstrDelete(objstr_t *store) {
  objstr_t *tmp;
  while (store != NULL) {
    tmp = store->next;
    free(store);
    store = tmp;
  }
  return;
}

/* ==============================================
 * objstrAlloc
 * - allocate space in an object store
 * ==============================================
 */
void *objstrAlloc(unsigned int size) {
  void *tmp;
  int i=0;
  objstr_t *store=t_cache;
  if ((size&7)!=0) {
    size+=(8-(size&7));
  }

  for(; i<2; i++) {
    if (OSFREE(store)>=size) {
      tmp=store->top;
      store->top +=size;
      return tmp;
    }
    if ((store=store->next)==NULL)
      break;
  }

  {
    unsigned int newsize=size>DEFAULT_OBJ_STORE_SIZE ? size : DEFAULT_OBJ_STORE_SIZE;
    objstr_t **otmp=&t_reserve;
    objstr_t *ptr;
    while((ptr=*otmp)!=NULL) {
      if (ptr->size>=newsize) {
	//remove from list
	*otmp=ptr->next;
	ptr->next=t_cache;
	t_cache=ptr;
	ptr->top=((char *)(&ptr[1]))+size;
	return &ptr[1];
      }
    }

    objstr_t *os=(objstr_t *)calloc(1,(sizeof(objstr_t) + newsize));
    void *nptr=&os[1];
    os->next=t_cache;
    t_cache=os;
    os->size=newsize;
    os->top=((char *)nptr)+size;
    return nptr;
  }
}
