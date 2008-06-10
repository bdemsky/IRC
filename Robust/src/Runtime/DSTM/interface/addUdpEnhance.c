#include <sys/socket.h>
#include <netinet/in.h>
#include <stdio.h>
#include <string.h>
#include "addUdpEnhance.h"

/************************
 * Global Variables *
 ***********************/
int udpSockFd;

int createUdpSocket() {
  int sockfd;
  struct sockaddr_in clientaddr;
  const int on = 1;

  if((sockfd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)) < 0) {
    perror("socket creation failed");
    return -1;
  }
  if((setsockopt(sockfd, SOL_SOCKET, SO_BROADCAST, &on, sizeof(on))) < 0) {
    perror("setsockopt - SOL_SOCKET");
    return -1;
  }
  return sockfd;
}

int udpInit() {
  int sockfd;
  int setsockflag = 1;
  struct sockaddr_in servaddr;

  //Create Global Udp Socket
  if((udpSockFd = createUdpSocket()) < 0) {
    printf("Error in socket\n");
  }

  sockfd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
  if(sockfd < 0) {
    perror("socket");
    exit(1);
  }

  if(setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &setsockflag, sizeof(setsockflag)) < 0) {
    perror("socket");
    exit(1);
  }

#ifdef MAC 
  if(setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &setsockflag, sizeof(setsockflag)) < 0) {
    perror("socket");
    exit(1);
  }
#endif

  bzero(&servaddr, sizeof(servaddr));
  servaddr.sin_family = AF_INET;
  servaddr.sin_port = htons(UDP_PORT);
  servaddr.sin_addr.s_addr = htonl(INADDR_ANY);

  if(bind(sockfd, (struct sockaddr *)&servaddr, sizeof(servaddr)) < 0) {
    perror("bind");
    exit(1);
  }

  return sockfd;
}

void *udpListenBroadcast(void *sockfd) {
  pthread_t thread_udpBroadcast;
  struct sockaddr_in servaddr;
  char readBuffer[MAX_SIZE];
  socklen_t socklen = sizeof(struct sockaddr);
  int retval;

  memset(readBuffer, 0, MAX_SIZE);
  printf("Listening on port %d, fd = %d\n", UDP_PORT, (int)sockfd);

  while(1) {
    //int bytesRcvd = recvfrom((int)sockfd, readBuffer, 5, 0, NULL, NULL);
    int bytesRcvd = recvfrom((int)sockfd, readBuffer, strlen(readBuffer), 0, (struct sockaddr *)&servaddr, &socklen);
    if(bytesRcvd == 0) {
      break;
    }

    if(bytesRcvd == -1) {
      printf("DEBUG-> Recv Error! \n");
      break;
    }

    short status = *((short *) &readBuffer[0]);
    switch (status) {
      case INVALIDATE_OBJS:
        if((retval = invalidateFromPrefetchCache(readBuffer))!= 0) {
          printf("Error: In invalidateFromPrefetchCache() at %s, %d\n", __FILE__, __LINE__);
          break;
        }
        break;
      default:
        printf("Error: Cannot regcognize the status in file %s, at line %d\n", __FILE__, __LINE__);
    }
  }

closeconnection:
    /* Close connection */
    if(close((int)sockfd) == -1)
      perror("close");
    pthread_exit(NULL);
}

/* Function that sends a broadcast to Invalidate objects that
 * have been currently modified */
int invalidateObj(thread_data_array_t *tdata) {
  struct sockaddr_in clientaddr;
  //TODO Instead of sending "hello" send modified objects
  char writeBuffer[MAX_SIZE];
  //char writeBuffer[] = "hello";
  const int on = 1;

  bzero(&clientaddr, sizeof(clientaddr));
  clientaddr.sin_family = AF_INET;
  clientaddr.sin_port = htons(UDP_PORT);
  clientaddr.sin_addr.s_addr = INADDR_BROADCAST;
  /* Create Udp Message */
  int offset = 0;
  *((short *)&writeBuffer[0]) = INVALIDATE_OBJS;
  offset += sizeof(short);
  *((short *) (writeBuffer+offset)) = (short) (sizeof(unsigned int) * (tdata->buffer->f.nummod));
  offset += sizeof(short);
  int i;
  for(i = 0; i < tdata->buffer->f.nummod; i++) {
    if(offset == MAX_SIZE) {
      if((n = sendto(udpSockFd, (const void *) writeBuffer, strlen(writeBuffer), 0, (const struct sockaddr *)&clientaddr, sizeof(clientaddr))) < 0) {
        perror("sendto error- ");
        printf("DEBUG-> sendto error: errorno %d\n", errno);
        return -1;
      }
      offset = 0;
    }
    /*
    if(offset >= MAX_SIZE) {
      printf("DEBUG-> Large number of objects for one udp message\n");
      return -1;
    }
    */

    *((unsigned int *) (writeBuffer+offset)) = tdata->buffer->oidmod[i];
    offset += sizeof(unsigned int);
  }
  int n;
  if((n = sendto(udpSockFd, (const void *) writeBuffer, strlen(writeBuffer), 0, (const struct sockaddr *)&clientaddr, sizeof(clientaddr))) < 0) {
    perror("sendto error- ");
    printf("DEBUG-> sendto error: errorno %d\n", errno);
    return -1;
  }
  //printf("DEBUG-> Client sending: %d bytes, %s\n", n, writeBuffer);
  return 0;
}

int invalidateFromPrefetchCache(char *buffer) {
  int offset = sizeof(int);
  /* Read objects sent */
  int numObjs = *((short *)(buffer+offset)) / sizeof(unsigned int);
  int i;
  for(i = 0; i < numObjs; i++) {
    unsigned int oid;
    oid = *((unsigned int *)(buffer+offset));
    objheader_t *header;
    /* Lookup Objects in prefetch cache and remove them */
    if((header = prehashSearch(oid)) != NULL) {
      prehashRemove(oid);
    }
    offset += sizeof(unsigned int);
  }
  return 0;
}
