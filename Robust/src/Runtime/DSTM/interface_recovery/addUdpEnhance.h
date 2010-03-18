#ifndef _ADDUDPENHANCE_H
#define _ADDUDPENHANCE_H

#include "dstm.h"
#include "altmlookup.h"


/*******************************
 * Udp Message structures
 ******************************/
#define INVALIDATE_OBJS 101

/*************************
 * Global constants
 ************************/
#define MAX_SIZE  2000

/********************************
 *  Function Prototypes
 *******************************/
int createUdpSocket();
int udpInit();
void *udpListenBroadcast(void *);
int invalidateObj(trans_req_data_t *, int, char, int*);
int invalidateFromPrefetchCache(char *);
int sendUdpMsg(trans_req_data_t *, int, int, struct sockaddr_in *, char, int*);
#endif
