#include "sockpool.h"

inline int CompareAndSwap(int *a, int oldval, int newval) {
    int temp = *a;
    if (temp == oldval) {
        *a = newval;
        return 1;
    } else 
        return 0;
    return 1;
}

inline void InitLock(SpinLock *s) {
    *s = 0;
}

inline void Lock(SpinLock *s) {
    do {
    } while(CompareAndSwap(s, 1, 1));
}

inline void UnLock(SpinLock *s) {
    *s = 0;
}

sockPoolHashTable_t *createSockPool(sockPoolHashTable_t * sockhash, unsigned int size, float loadfactor) {
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
    sockhash->numelements = 0;
    sockhash->loadfactor = loadfactor;
    InitLock(&sockhash->mylock);

    return sockhash;
}

int createNewSocket(unsigned int mid) {
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
    return sd;
}

int getSock(sockPoolHashTable_t *sockhash, unsigned int mid) {
    int key = mid%(sockhash->size);

    if (sockhash->table[key] == NULL) {
        int sd;
        if((sd = createNewSocket(mid)) != -1) {
            socknode_t *inusenode = calloc(1, sizeof(socknode_t));
            inusenode->mid = mid; 
            inusenode->sd = sd; 
            insToList(sockhash, inusenode);
            return sd;
        } else {
            return -1;
        }
    }

    int midFound = 0;
    socknode_t *ptr = sockhash->table[key];
    socknode_t *prev = (socknode_t *) &(sockhash->table[key]);
    while (ptr != NULL) {
        if (mid == ptr->mid) {
            midFound = 1;
            int sd = ptr->sd;
            prev = ptr->next;
            insToList(sockhash, ptr);
            return sd;
        }
        prev = ptr;
        ptr = ptr->next;
    }

    if(midFound == 0) {
        int sd;
        if((sd = createNewSocket(mid)) != -1) {
            socknode_t *inusenode = calloc(1, sizeof(socknode_t));
            inusenode->mid = mid; 
            inusenode->sd = sd; 
            insToList(sockhash, inusenode);
            return sd;
        } else {
            return -1;
        }
    }
    return -1;
}

int getSockWithLock(sockPoolHashTable_t *sockhash, unsigned int mid) {
    int key = mid%(sockhash->size);

    Lock(&sockhash->mylock);
    if (sockhash->table[key] == NULL) {
        UnLock(&sockhash->mylock);
        int sd;
        if((sd = createNewSocket(mid)) != -1) {
            socknode_t *inusenode = calloc(1, sizeof(socknode_t));
            inusenode->mid = mid; 
            inusenode->sd = sd; 
            insToListWithLock(sockhash, inusenode);
            return sd;
        } else {
            return -1;
        }
    }
    UnLock(&sockhash->mylock);
    int midFound = 0;
    Lock(&sockhash->mylock);
    socknode_t *ptr = sockhash->table[key];
    socknode_t *prev = (socknode_t *) &(sockhash->table[key]);
    while (ptr != NULL) {
        if (mid == ptr->mid) {
            midFound = 1;
            int sd = ptr->sd;
            prev = ptr->next;
            UnLock(&sockhash->mylock);
            insToListWithLock(sockhash, ptr);
            return sd;
        }
        prev = ptr;
        ptr = ptr->next;
    }
    UnLock(&sockhash->mylock);

    if(midFound == 0) {
        int sd;
        if((sd = createNewSocket(mid)) != -1) {
            socknode_t *inusenode = calloc(1, sizeof(socknode_t));
            inusenode->mid = mid; 
            inusenode->sd = sd; 
            insToListWithLock(sockhash, inusenode);
            return sd;
        } else {
            return -1;
        }
    }
    return -1;
}

void insToList(sockPoolHashTable_t *sockhash, socknode_t *inusenode) {
    inusenode->next = sockhash->inuse;
    sockhash->inuse = inusenode;
} 

void insToListWithLock(sockPoolHashTable_t *sockhash, socknode_t *inusenode) {
    Lock(&sockhash->mylock);
    inusenode->next = sockhash->inuse;
    sockhash->inuse = inusenode;
    UnLock(&sockhash->mylock);
} 

int freeSock(sockPoolHashTable_t *sockhash, unsigned int mid, int sd) {
    if(sockhash->inuse != NULL) {
        socknode_t *ptr = sockhash->inuse; 
        ptr->mid = mid;
        ptr->sd = sd;
        sockhash->inuse = ptr->next;
        int key = mid%(sockhash->size);
        ptr->next = sockhash->table[key];
        sockhash->table[key] = ptr;
        return 0;
    }
    return -1;
}

int freeSockWithLock(sockPoolHashTable_t *sockhash, unsigned int mid, int sd) {
    if(sockhash->inuse != NULL) {
        Lock(&sockhash->mylock);
        socknode_t *ptr = sockhash->inuse; 
        ptr->mid = mid;
        ptr->sd = sd;
        sockhash->inuse = ptr->next;
        int key = mid%(sockhash->size);
        ptr->next = sockhash->table[key];
        sockhash->table[key] = ptr;
        UnLock(&sockhash->mylock);
        return 0;
    }
    return -1;
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
