#include "dstm.h"

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

void *objstrAlloc(objstr_t *store, unsigned int size) {
  void *tmp;
  while (1) {
    if (((unsigned int)store->top - (((unsigned int)store) + sizeof(objstr_t)) + size) <= store->size) { //store not full
      tmp = store->top;
      store->top += size;
      return tmp;
    }
    //store full
    if (store->next == NULL) {
      //end of list, all full
      if (size > DEFAULT_OBJ_STORE_SIZE) {
	//in case of large objects
	if((store->next = (objstr_t *)calloc(1,(sizeof(objstr_t) + size))) == NULL) {
	  printf("%s() Calloc error at line %d, %s\n", __func__, __LINE__, __FILE__);
	  return NULL;
	}
	store = store->next;
	store->size = size;
      } else {
	if((store->next = calloc(1,(sizeof(objstr_t) + DEFAULT_OBJ_STORE_SIZE))) == NULL) {
	  printf("%s() Calloc error at line %d, %s\n", __func__, __LINE__, __FILE__);
	  return NULL;
	}
	store = store->next;
	store->size = DEFAULT_OBJ_STORE_SIZE;
      }
      store->top = (void *)(((unsigned int)store) + sizeof(objstr_t) + size);
      return (void *)(((unsigned int)store) + sizeof(objstr_t));
    } else
      store = store->next;
  }
}
