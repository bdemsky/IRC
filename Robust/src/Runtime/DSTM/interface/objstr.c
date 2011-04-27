#include "dstm.h"
#include "gCollect.h"

#define OSUSED(x) (((unsigned int)(x)->top)-((unsigned int) (x+1)))
#define OSFREE(x) ((x)->size-OSUSED(x))

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

void *objstrAlloc(objstr_t **osptr, unsigned int size) {
  void *tmp;
  int i=0;
  objstr_t *store=*osptr;
  if ((size&7)!=0) {
    size+=(8-(size&7));
  }

  for(; i<3; i++) {
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
    objstr_t *os=(objstr_t *)calloc(1,(sizeof(objstr_t) + newsize));
    void *ptr=&os[1];
    os->next=*osptr;
    (*osptr)=os;
    os->size=newsize;
    os->top=((char *)ptr)+size;
    return ptr;
  }
}
