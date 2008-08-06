#include "sockpool.h"
#include <netinet/tcp.h>

#if defined(__i386__)
inline int test_and_set(volatile unsigned int *addr) {
    int oldval;
    /* Note: the "xchg" instruction does not need a "lock" prefix */
    __asm__ __volatile__("xchgl %0, %1"
        : "=r"(oldval), "=m"(*(addr))
        : "0"(1), "m"(*(addr)));
    return oldval;
}
inline void UnLock(volatile unsigned int *addr) {
    int oldval;
    /* Note: the "xchg" instruction does not need a "lock" prefix */
    __asm__ __volatile__("xchgl %0, %1"
        : "=r"(oldval), "=m"(*(addr))
        : "0"(0), "m"(*(addr)));
}
#elif
#   error need implementation of test_and_set
#endif

#define MAXSPINS 100

inline void Lock(volatile unsigned int *s) {
  while(test_and_set(s)) {
    int i=0;
    while(*s) {
      if (i++>MAXSPINS) {
	sched_yield();
	i=0;
      }
    }
  }
}

sockPoolHashTable_t *createSockPool(sockPoolHashTable_t * sockhash, unsigned int size) {
  if((sockhash = calloc(1, sizeof(sockPoolHashTable_t))) == NULL) {
    printf("Calloc error at %s line %d\n", __FILE__, __LINE__);
    return NULL;
  }
  
  socknode_t **nodelist;
  if ((nodelist = calloc(size, sizeof(socknode_t *))) < 0) {
    printf("Calloc error at %s line %d\n", __FILE__, __LINE__);
    free(sockhash);
    return NULL;
  }
  
  sockhash->table = nodelist;
  sockhash->inuse = NULL;
  sockhash->size = size;
  sockhash->mylock=0;
  
  return sockhash;
}

int createNewSocket(unsigned int mid) {
  int sd;
  int flag=1;
  if((sd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
    printf("%s() Error: In creating socket at %s, %d\n", __func__, __FILE__, __LINE__);
    return -1;
  }
  setsockopt(sd, IPPROTO_TCP, TCP_NODELAY, (char *) &flag, sizeof(flag));
  struct sockaddr_in remoteAddr;
  bzero(&remoteAddr, sizeof(remoteAddr));
  remoteAddr.sin_family = AF_INET;
  remoteAddr.sin_port = htons(LISTEN_PORT);
  remoteAddr.sin_addr.s_addr = htonl(mid);
  if(connect(sd, (struct sockaddr *)&remoteAddr, sizeof(remoteAddr)) < 0) {
    perror("socket connect: ");
    printf("%s(): Error %d connecting to %s:%d\n", __func__, errno, inet_ntoa(remoteAddr.sin_addr), LISTEN_PORT);
    close(sd);
    return -1;
  }
  return sd;
}

int getSockWithLock(sockPoolHashTable_t *sockhash, unsigned int mid) {
  socknode_t **ptr;
  int key = mid%(sockhash->size);
  int sd;
  
  Lock(&sockhash->mylock);
  ptr=&(sockhash->table[key]);
  
  while(*ptr!=NULL) {
    if (mid == (*ptr)->mid) {
      socknode_t *tmp=*ptr;
      sd = tmp->sd;
      *ptr=tmp->next;
      tmp->next=sockhash->inuse;
      sockhash->inuse=tmp;
      UnLock(&sockhash->mylock);
      return sd;
    }
    ptr=&((*ptr)->next);
  }
  UnLock(&sockhash->mylock);
  if((sd = createNewSocket(mid)) != -1) {
    socknode_t *inusenode = calloc(1, sizeof(socknode_t));
    inusenode->sd = sd;
    inusenode->mid = mid;
    insToListWithLock(sockhash, inusenode);
    return sd;
  } else {
    return -1;
  }
}

int getSock(sockPoolHashTable_t *sockhash, unsigned int mid) {
  socknode_t **ptr;
  int key = mid%(sockhash->size);
  int sd;
  
  ptr=&(sockhash->table[key]);
  
  while(*ptr!=NULL) {
    if (mid == (*ptr)->mid) {
      socknode_t *tmp=*ptr;
      sd = tmp->sd;
      *ptr=tmp->next;
      tmp->next=sockhash->inuse;
      sockhash->inuse=tmp;
      return sd;
    }
    ptr=&((*ptr)->next);
  }
  if((sd = createNewSocket(mid)) != -1) {
    socknode_t *inusenode = calloc(1, sizeof(socknode_t));
    inusenode->next=sockhash->inuse;
    sockhash->inuse=inusenode;
    return sd;
  } else {
    return -1;
  }
}

int getSock2(sockPoolHashTable_t *sockhash, unsigned int mid) {
  socknode_t **ptr;
  int key = mid%(sockhash->size);
  int sd;
  
  ptr=&(sockhash->table[key]);
  
  while(*ptr!=NULL) {
    if (mid == (*ptr)->mid) {
      return (*ptr)->sd;
    }
    ptr=&((*ptr)->next);
  }
  if((sd = createNewSocket(mid)) != -1) {
    *ptr=calloc(1, sizeof(socknode_t));
    (*ptr)->mid=mid;
    (*ptr)->sd=sd;
    return sd;
  } else {
    return -1;
  }
}

/*socket pool with multiple TR threads asking to connect to same machine  */
int getSock2WithLock(sockPoolHashTable_t *sockhash, unsigned int mid) {
  socknode_t **ptr;
  int key = mid%(sockhash->size);
  int sd;
  
  Lock(&sockhash->mylock);
  ptr=&(sockhash->table[key]);
  while(*ptr!=NULL) {
    if (mid == (*ptr)->mid) {
      UnLock(&sockhash->mylock);
      return (*ptr)->sd;
    }
    ptr=&((*ptr)->next);
  }
  UnLock(&sockhash->mylock);
  if((sd = createNewSocket(mid)) != -1) {
    socknode_t *inusenode = calloc(1, sizeof(socknode_t));
    inusenode->sd = sd;
    inusenode->mid = mid;
    addSockWithLock(sockhash, inusenode);
    return sd;
  } else {
    return -1;
  }
}

void addSockWithLock(sockPoolHashTable_t *sockhash, socknode_t *ptr) {
  int key = ptr->mid%(sockhash->size);
  Lock(&sockhash->mylock);
  ptr->next = sockhash->table[key];
  sockhash->table[key] = ptr;
  UnLock(&sockhash->mylock);
}

void insToListWithLock(sockPoolHashTable_t *sockhash, socknode_t *inusenode) {
    Lock(&sockhash->mylock);
    inusenode->next = sockhash->inuse;
    sockhash->inuse = inusenode;
    UnLock(&sockhash->mylock);
} 

void freeSock(sockPoolHashTable_t *sockhash, unsigned int mid, int sd) {
    int key = mid%(sockhash->size);
    socknode_t *ptr = sockhash->inuse; 
    sockhash->inuse = ptr->next;
    ptr->mid = mid;
    ptr->sd = sd;
    ptr->next = sockhash->table[key];
    sockhash->table[key] = ptr;
}

void freeSockWithLock(sockPoolHashTable_t *sockhash, unsigned int mid, int sd) {
  int key = mid%(sockhash->size);
  socknode_t *ptr;
  Lock(&sockhash->mylock);
  ptr = sockhash->inuse; 
  sockhash->inuse = ptr->next;
  ptr->mid = mid;
  ptr->sd = sd;
  ptr->next = sockhash->table[key];
  sockhash->table[key] = ptr;
  UnLock(&sockhash->mylock);
}

#if 0
/ ***************************************/
* Array Implementation for socket resuse 
* ***************************************/

int num_machines;

sock_pool_t *initSockPool(unsigned int *mid, int machines) {
    sock_pool_t *sockpool;
    num_machines = machines;
    if ((sockpool = calloc(num_machines, sizeof(sock_pool_t))) < 0) {
        printf("%s(), Calloc error at %s, line %d\n", __func__, __FILE__, __LINE__);
        return NULL;
    }
    int i;
    for (i = 0; i < num_machines; i++) {
        if ((sockpool[i].sd = calloc(MAX_CONN_PER_MACHINE, sizeof(int))) < 0) {
            printf("%s(), Calloc error at %s, line %d\n", __func__, __FILE__, __LINE__);
            return NULL;
        }
        if ((sockpool[i].inuse = calloc(MAX_CONN_PER_MACHINE, sizeof(char))) < 0) {
            printf("%s(), Calloc error at %s, line %d\n", __func__, __FILE__, __LINE__);
            return NULL;
        }
        sockpool[i].mid = mid[i];
        int j;
        for(j = 0; j < MAX_CONN_PER_MACHINE; j++) {
            sockpool[i].sd[j] = -1;
        }
    }

    return sockpool;
}

int getSock(sock_pool_t *sockpool, unsigned int mid) {
    int i;
    for (i = 0; i < num_machines; i++) {
        if (sockpool[i].mid == mid) {
            int j;
            for (j = 0; j < MAX_CONN_PER_MACHINE; j++) {
                if (sockpool[i].sd[j] != -1 && (sockpool[i].inuse[j] == 0)) {
                    sockpool[i].inuse[j] = 1;
                    return sockpool[i].sd[j];
                }
                if (sockpool[i].sd[j] == -1) {
                    //Open Connection
                    int sd;
                    if((sd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
                        printf("%s() Error: In creating socket at %s, %d\n", __func__, __FILE__, __LINE__);
                        return -1;
                    }
                    struct sockaddr_in remoteAddr;
                    bzero(&remoteAddr, sizeof(remoteAddr));
                    remoteAddr.sin_family = AF_INET;
                    remoteAddr.sin_port = htons(LISTEN_PORT);
                    remoteAddr.sin_addr.s_addr = htonl(mid);

                    if(connect(sd, (struct sockaddr *)&remoteAddr, sizeof(remoteAddr)) < 0) {
                        printf("%s(): Error %d connecting to %s:%d\n", __func__, errno, inet_ntoa(remoteAddr.sin_addr), LISTEN_PORT);
                        close(sd);
                        return -1;
                    }
                    sockpool[i].sd[j] = sd;
                    sockpool[i].inuse[j] = 1;
                    return sockpool[i].sd[j];
                }
            }
            printf("%s()->Error: Less number of MAX_CONN_PER_MACHINE\n", __func__);
            return -1;
        }
    }
    printf("%s()-> Error: Machine id not found\n", __func__);

    return -1;
}

int freeSock(sock_pool_t *sockpool, int sd) {
    int i;
    for (i = 0; i < num_machines; i++) {
        int j;
        for (j = 0; j < MAX_CONN_PER_MACHINE; j++) {
            if (sockpool[i].sd[j] == sd) {
                sockpool[i].inuse[j] = 0;
                return 0;
            }
        }
    }
    printf("%s() Error: Illegal socket descriptor %d\n", __func__, sd);

    return -1;
}

#endif
