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

void addtransaction(unsigned int oid, struct transrecord * trans) {
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
    chashInsert(rl, oid, rl);
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
  for(i=0;i<READERSIZE;i++) {
    if (rl->array[i]==NULL) {
      rl->array[i]=trans;
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
  for(i=0;i<numoids;i++) {
    unsigned int oid=oidarray[i];
    struct readerlist *rl=chashRemove2(table, oid);
    struct readerlist *tmp;
    do {
      count=rl->numreaders;
      for(int j=0;count;j++) {
	struct transrecord *trans=rl->array[j];
	if (trans!=NULL) {
	  trans->abort=1;//It's okay to set our own abort flag...it is
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

void removeaborttransaction(unsigned int oidarray[], unsigned int numoids, struct transrecord * trans) {
  int i,j;
  pthread_mutex_lock(&aborttablelock);
  for(i=0;i<numoids;i++) {
    unsigned int oid=oidarray[i];
    struct readerlist * rl=chashSearch(aborttable, oid);
    
    struct readerlist *first=rl;
    while(1) {
      for(j=0;j<READERSIZE;j++) {
	if (rl->array[j]==trans) {
	  rl->array[j]=NULL;
	  if ((--rl->numreaders)==0) {
	    if (first==rl) {
	      chashRemove2(table, oid);
	      if (rl->next!=NULL) 
		chashInsert(table, oid, rl->next);
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
  }
  pthread_mutex_unlock(&aborttablelock);
}

