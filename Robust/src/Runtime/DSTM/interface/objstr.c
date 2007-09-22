#include "dstm.h"

objstr_t *objstrCreate(unsigned int size)
{
	objstr_t *tmp = malloc(sizeof(objstr_t) + size);
	tmp->size = size;
	tmp->top = tmp + 1; //points to end of objstr_t structure!
	return tmp;
}

//free entire list, starting at store
void objstrDelete(objstr_t *store)
{
	objstr_t *tmp;
	while (store != NULL)
	{
		tmp = store->next;
		free(store);
		store = tmp;
	}
	return;
}

void *objstrAlloc(objstr_t *store, unsigned int size)
{
	void *tmp;
	while (1)
	{
		if (((unsigned int)store->top - (unsigned int)store - sizeof(objstr_t) + size) <= store->size)
		{  //store not full
			tmp = store->top;
			store->top += size;
			return tmp;
		}
		//store full
		if (store->next == NULL)
		{  //end of list, all full
			if (size > DEFAULT_OBJ_STORE_SIZE) //in case of large objects
			{
				store->next = (objstr_t *)malloc(sizeof(objstr_t) + size);
				if (store->next == NULL)
					return NULL;
				store = store->next;
				store->size = size;
			}
			else
			{
				store->next = malloc(sizeof(objstr_t) + DEFAULT_OBJ_STORE_SIZE);
				if (store->next == NULL)
					return NULL;
				store = store->next;
				store->size = DEFAULT_OBJ_STORE_SIZE;
			}
			store->top = (void *)((unsigned int)store + sizeof(objstr_t) + size);
			return (void *)((unsigned int)store + sizeof(objstr_t));
		}
		else  //try the next one
			store = store->next;
	}
}

