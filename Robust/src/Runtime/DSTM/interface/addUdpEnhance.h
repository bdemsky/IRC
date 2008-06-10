#ifndef _UDP_H
#define _UDP_H

#include "dstm.h"


/*******************************
 * Udp Message structures
 ******************************/
#define INVALIDATE_OBJS 101

/*************************
 * Global constants
 ************************/
#define MAX_SIZE  4000

/********************************
 *  Function Prototypes
 *******************************/
int createUdpSocket();
int udpInit();
void *udpListenBroadcast(void *);
int invalidateObj(thread_data_array_t *);
int invalidateFromPrefetchCache(char *); 
#endif
