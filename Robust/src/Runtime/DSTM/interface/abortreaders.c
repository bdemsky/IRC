#include "clookup.h"
#include "abortreaders.h"

chashtable_t * aborttable;
pthread_mutex_t aborttablelock;
struct readerlist *freelist;

void initreaderlist() {
  pthread_mutex_init(&aborttablelock, NULL);
  aborttable=chashCreate(CHASH_SIZE, CLOADFACTOR);
  freelist=NULL;
}

void addtransaction(unsigned int oid) {
  struct readerlist * rl;
  int i;
  if (pthread_mutex_trylock(&aborttablelock)!=0)
    return;
  rl=(struct readerlist *)chashSearch(aborttable, oid);
  if (rl==NULL) {
    if (freelist==NULL)
      rl=calloc(1,sizeof(struct readerlist ));
    else {
      rl=freelist;
      freelist=rl->next;
      memset(rl,0, sizeof(struct readerlist));
    }
    chashInsert(aborttable, oid, rl);
  }
  while(rl->numreaders==READERSIZE) {
    if (rl->next!=NULL)
      rl=rl->next;
    else {
      rl->next=calloc(1,sizeof(struct readerlist));
      rl=rl->next;
    }
  }
  rl->numreaders++;
  for(i=0; i<READERSIZE; i++) {
    if (rl->array[i]==NULL) {
      rl->array[i]=&t_abort;
      pthread_mutex_unlock(&aborttablelock);
      return;
    }
  }
  pthread_mutex_unlock(&aborttablelock);
  printf("ERROR in addtransaction\n");
}

void removetransaction(unsigned int oidarray[], unsigned int numoids) {
  int i,j;
  pthread_mutex_lock(&aborttablelock);
  for(i=0; i<numoids; i++) {
    unsigned int oid=oidarray[i];
    struct readerlist *rl=chashRemove2(aborttable, oid);
    struct readerlist *tmp;
    if (rl==NULL)
      continue;
    do {
      int count=rl->numreaders;
      int j;
      for(j=0; count; j++) {
	int *t_abort=rl->array[j];
	if (t_abort!=NULL) {
	  *t_abort=1; //It's okay to set our own abort flag...it is
	  //too late to abort us
	  count--;
	}
      }
      tmp=rl;
      rl=rl->next;
      tmp->next=freelist;
      freelist=tmp;
    } while(rl!=NULL);
  }
  pthread_mutex_unlock(&aborttablelock);
}

void removethisreadtransaction(unsigned char* oidverread, unsigned int numoids) {
  int i,j;
  pthread_mutex_lock(&aborttablelock);
  for(i=0; i<numoids; i++) {
    unsigned int oid=*((unsigned int *)oidverread);
    struct readerlist * rl=chashSearch(aborttable, oid);
    struct readerlist *first=rl;
    oidverread+=(sizeof(unsigned int)+sizeof(unsigned short));
    while(rl!=NULL) {
      for(j=0; j<READERSIZE; j++) {
	if (rl->array[j]==&t_abort) {
	  rl->array[j]=NULL;
	  if ((--rl->numreaders)==0) {
	    if (first==rl) {
	      chashRemove2(aborttable, oid);
	      if (rl->next!=NULL)
		chashInsert(aborttable, oid, rl->next);
	      rl->next=freelist;
	      freelist=rl;
	    } else {
	      first->next=rl->next;
	      rl->next=freelist;
	      freelist=rl;
	    }
	  }
	  goto nextitem;
	}
      }
      first=rl;
      rl=rl->next;
    }
nextitem:
    ;
  }
  pthread_mutex_unlock(&aborttablelock);
}

void removetransactionhash() {
  chashlistnode_t *ptr=c_table;
  int i,j;
  pthread_mutex_lock(&aborttablelock);
  for(i=0; i<c_size; i++) {
    chashlistnode_t *curr=&ptr[i];
    do {
      unsigned int oid=curr->key;
      if (oid==0)
	break;
      struct readerlist * rl=chashSearch(aborttable, oid);
      struct readerlist *first=rl;
      while(rl!=NULL) {
	for(j=0; j<READERSIZE; j++) {
	  if (rl->array[j]==&t_abort) {
	    rl->array[j]=NULL;
	    if ((--rl->numreaders)==0) {
	      if (first==rl) {
		chashRemove2(aborttable, oid);
		if (rl->next!=NULL)
		  chashInsert(aborttable, oid, rl->next);
		rl->next=freelist;
		freelist=rl;
	      } else {
		first->next=rl->next;
		rl->next=freelist;
		freelist=rl;
	      }
	    }
	    goto nextitem;
	  }
	}
	first=rl;
	rl=rl->next;
      }
nextitem:
      curr=curr->next;
    } while(curr!=NULL);
  }
  pthread_mutex_unlock(&aborttablelock);
}


void removethistransaction(unsigned int oidarray[], unsigned int numoids) {
  int i,j;
  pthread_mutex_lock(&aborttablelock);
  for(i=0; i<numoids; i++) {
    unsigned int oid=oidarray[i];
    struct readerlist * rl=chashSearch(aborttable, oid);

    struct readerlist *first=rl;
    while(rl!=NULL) {
      for(j=0; j<READERSIZE; j++) {
	if (rl->array[j]==&t_abort) {
	  rl->array[j]=NULL;
	  if ((--rl->numreaders)==0) {
	    if (first==rl) {
	      chashRemove2(aborttable, oid);
	      if (rl->next!=NULL)
		chashInsert(aborttable, oid, rl->next);
	      rl->next=freelist;
	      freelist=rl;
	    } else {
	      first->next=rl->next;
	      rl->next=freelist;
	      freelist=rl;
	    }
	  }
	  goto nextitem;
	}
      }
      first=rl;
      rl=rl->next;
    }
nextitem:
    ;
  }
  pthread_mutex_unlock(&aborttablelock);
}

