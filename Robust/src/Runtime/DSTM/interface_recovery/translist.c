#include "translist.h"

/* creates new trans list and return empty list */
tlist_t* tlistCreate()
{
  tlist_t* transList;
  if((transList = malloc(sizeof(tlist_t))) == NULL) {
    printf("%s -> cannot allocate memory\n",__func__);
    exit(0);
  }
  transList->head = NULL;
  transList->size = 0;

  pthread_mutex_init(&(transList->mutex),NULL);

  return transList;
}

tlist_t* tlistDestroy(tlist_t* transList)
{
  tlist_node_t* walker = transList->head;
  tlist_node_t* tmp;

  while(walker)
  {
    tmp = walker;
    walker = walker->next;
    free(tmp);
  }

  free(transList);

  return NULL;
}

// tlistInsertNode extension
tlist_t* tlistInsertNode2(tlist_t* transList,tlist_node_t* tNode) 
{
  transList = tlistInsertNode(transList,tNode->transid,tNode->decision,tNode->status);
  return transList;
}

// return 0 if success, return -1 if fail
tlist_t* tlistInsertNode(tlist_t* transList,unsigned int transid,char decision,char status) {

//  printf("%s -> ADD transID : %u decision %d  status  %d\n",__func__,transid,decision,status);
  pthread_mutex_lock(&(transList->mutex));
  tlist_node_t* head = transList->head;

  if(head == NULL) {
    if((head = malloc(sizeof(tlist_node_t))) == NULL) {
      printf("%s -> cannot allocate memory\n",__func__);
      exit(0);
    }

    head->transid = transid;
    head->decision = decision;
    head->status = status;
    head->next = NULL;

    //pthread_mutex_lock(&(transList->mutex));
    transList->head = head;
    (transList->size)++;
    transList->flag = 1;
    pthread_mutex_unlock(&(transList->mutex));
    return transList;
  }
  else {
    tlist_node_t* tmp;

    if((tmp = malloc(sizeof(tlist_node_t))) == NULL) {
      printf("%s -> cannot allocate memory\n",__func__);
      exit(0);
    }

    // search end of list
    tmp->transid = transid;
    tmp->decision = decision;
    tmp->status = status;

    tmp->next = transList->head;
    transList->head = tmp;
    (transList->size)++;
    transList->flag = 1;
    pthread_mutex_unlock(&(transList->mutex));
    return transList;
  }
}

// return tlist_t if success, return null if cannot find
tlist_node_t* tlistSearch(tlist_t* transList,unsigned int transid)
{
  pthread_mutex_lock(&(transList->mutex));
  tlist_node_t* ptr = transList->head;

  while(ptr != NULL)
  {
    if(ptr->transid == transid)
      break;

    ptr = ptr->next;
  }
  pthread_mutex_unlock(&(transList->mutex));
  return ptr;
}

tlist_t* tlistRemove(tlist_t* transList,unsigned int transid)
{
//  printf("%s -> REMOVE transID : %u \n",__func__,transid);
  pthread_mutex_lock(&(transList->mutex));

  int flag = -1;
  tlist_node_t* tmp;
  tlist_node_t* ptr = transList->head;
  tlist_node_t* prev = NULL;

  /* if it is head */
  if(transList->head->transid == transid)
  {
    tmp = transList->head;
    transList->head = transList->head->next;
    free(tmp);
    (transList->size)--;
    transList->flag = 1;

    pthread_mutex_unlock(&(transList->mutex));
    return transList;
  }

  /* traverse all nodes */
  ptr = ptr->next;
  prev = transList->head;

  while(ptr != NULL)
  {
    if(ptr->transid == transid)
    {
      prev->next = ptr->next;
      free(ptr);
      (transList->size)--;
      flag = 0;
      transList->flag = 1;
      pthread_mutex_unlock(&(transList->mutex));
      return transList;
    }
    prev = ptr;
    ptr = ptr->next;
  }
    
  pthread_mutex_unlock(&(transList->mutex));
  printf("%s -> remove Fail!\n",__func__);

  return transList;
}

/* creates an array of tlist_node_t */
tlist_node_t* tlistToArray(tlist_t* transList,int* size)
{
  tlist_node_t* array;
  tlist_node_t* walker;

  *size = transList->size;

  if((array = (tlist_node_t*)calloc(transList->size,sizeof(tlist_node_t))) == NULL) {
    printf("%s -> calloc error\n",__func__);
    exit(0);
  }

  walker = transList->head;
  int i = 0;

  while(walker)
  {
    array[i++] = *walker;
    walker = walker->next;
  }

  return array;
}

// print out all info of transaction list
void tlistPrint(tlist_t* transList)
{
  printf("%s -> Enter\n",__func__);

  tlist_node_t* walker = transList->head;

  printf("%s -> Size : %d\n",__func__,transList->size);

  while(walker)
  {
    printf("ID : %u  Decision : %d  status : %d\n",walker->transid,walker->decision,walker->status);
    walker = walker->next;
  }

}

