#ifndef _ADDUDPENHANCE_H
#define _ADDUDPENHANCE_H

#include "dstm.h"


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
int invalidateObj(thread_data_array_t *);
int invalidateFromPrefetchCache(char *); 
int sendUdpMsg(thread_data_array_t *, struct sockaddr_in *, int);
#endif
