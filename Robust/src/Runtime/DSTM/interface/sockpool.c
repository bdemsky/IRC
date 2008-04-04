#include "sockpool.h"

sockPoolHashTable_t sockhash;

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

int createSockPool(unsigned int size, float loadfactor) {
    socknode_t **nodelist;
    if ((nodelist = calloc(size, sizeof(socknode_t *))) < 0) {
        printf("Calloc error at %s line %d\n", __FILE__, __LINE__);
        return -1;
    }
    sockhash.table = nodelist;
    sockhash.inuse = NULL;
    sockhash.size = size;
    sockhash.numelements = 0;
    sockhash.loadfactor = loadfactor;
    InitLock(&sockhash.mylock);
    return 0;
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

int getSock(unsigned int mid) {
    int key = mid%(sockhash.size);

    Lock(&sockhash.mylock);
    if (sockhash.table[key] == NULL) {
        UnLock(&sockhash.mylock);
        int sd;
        if((sd = createNewSocket(mid)) != -1) {
            socknode_t *inusenode = calloc(1, sizeof(socknode_t));
            inusenode->mid = mid; 
            inusenode->sd = sd; 
            insToList(inusenode);
            return sd;
        } else {
            return -1;
        }
    }
    UnLock(&sockhash.mylock);
    int midFound = 0;
    Lock(&sockhash.mylock);
    socknode_t *ptr = sockhash.table[key];
    socknode_t *prev = (socknode_t *) &(sockhash.table[key]);
    while (ptr != NULL) {
        if (mid == ptr->mid) {
            midFound = 1;
            int sd = ptr->sd;
            prev = ptr->next;
            UnLock(&sockhash.mylock);
            insToList(ptr);
            return sd;
        }
        prev = ptr;
        ptr = ptr->next;
    }
    UnLock(&sockhash.mylock);
    if(midFound == 0) {
        int sd;
        if((sd = createNewSocket(mid)) != -1) {
            socknode_t *inusenode = calloc(1, sizeof(socknode_t));
            inusenode->mid = mid; 
            inusenode->sd = sd; 
            insToList(inusenode);
            return sd;
        } else {
            return -1;
        }
    }
    return -1;
}

void insToList(socknode_t *inusenode) {
    Lock(&sockhash.mylock);
    inusenode->next = sockhash.inuse;
    sockhash.inuse = inusenode;
    UnLock(&sockhash.mylock);
} 

int freeSock(unsigned int mid, int sd) {
    if(sockhash.inuse != NULL) {
        Lock(&sockhash.mylock);
        socknode_t *ptr = sockhash.inuse; 
        ptr->mid = mid;
        ptr->sd = sd;
        sockhash.inuse = ptr->next;
        int key = mid%(sockhash.size);
        ptr->next = sockhash.table[key];
        sockhash.table[key] = ptr;
        UnLock(&sockhash.mylock);
        return 0;
    }
    return -1;
}
