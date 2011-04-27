#include <sys/socket.h>
#include <netinet/in.h>
#include <stdio.h>
#include <string.h>
#include <math.h>
#include <netinet/tcp.h>
#include "addUdpEnhance.h"
#include "altprelookup.h"
#ifdef ABORTREADERS
#include "abortreaders.h"
#endif

/************************
 * Global Variables *
 ***********************/
int udpSockFd;
extern unsigned int myIpAddr;

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

/* Function that listens for udp broadcast messages */
void *udpListenBroadcast(void *sockfd) {
  pthread_t thread_udpBroadcast;
  struct sockaddr_in servaddr;
  socklen_t socklen = sizeof(struct sockaddr);
  char readBuffer[MAX_SIZE];
  int retval;

  printf("Listening on port %d, fd = %d\n", UDP_PORT, (int)sockfd);

  while(1) {
    int bytesRcvd = recvfrom((int)sockfd, readBuffer, sizeof(readBuffer), 0, (struct sockaddr *)&servaddr, &socklen);
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

  /* Close connection */
  if(close((int)sockfd) == -1)
    perror("close");
  pthread_exit(NULL);
}

/* Function that invalidate objects that
 * have been currently modified
 * returns -1 on error and 0 on success */
int invalidateObj(trans_req_data_t *tdata, int pilecount, char finalresponse, int *socklist) {
  struct timeval start, end;
  struct sockaddr_in clientaddr;
  int retval;
  int i;
  int nummod=0;
  for(i=0; i<pilecount; i++) {
    nummod+=tdata[i].f.nummod;
  }
  bzero(&clientaddr, sizeof(clientaddr));
  clientaddr.sin_family = AF_INET;
  clientaddr.sin_port = htons(UDP_PORT);
  clientaddr.sin_addr.s_addr = INADDR_BROADCAST;
  int maxObjsPerMsg = (MAX_SIZE - 2*sizeof(unsigned int))/sizeof(unsigned int);
  /* send single udp msg */
  if((retval = sendUdpMsg(tdata, pilecount, nummod, &clientaddr, finalresponse, socklist)) < 0) {
    printf("%s() error in sending udp message at %s, %d\n", __func__, __FILE__, __LINE__);
    return -1;
  }
  return 0;
}

/* Function sends a udp broadcast, also distinguishes
 * msg size to be sent based on the total number of objects modified
 * returns -1 on error and 0 on success */
int sendUdpMsg(trans_req_data_t *tdata, int pilecount, int nummod, struct sockaddr_in *clientaddr, char finalresponse, int *socklist) {
  char writeBuffer[MAX_SIZE];
  int maxObjsPerMsg = (MAX_SIZE - 2*sizeof(unsigned int))/sizeof(unsigned int);
  int offset = 0;
  int i=0,j=0;

  *((short *)&writeBuffer[0]) = INVALIDATE_OBJS; //control msg
  offset += sizeof(short);
  *((unsigned int *)(writeBuffer+offset)) = myIpAddr; //mid sending invalidation
  offset += sizeof(unsigned int);

  while(nummod>0) {
    int numtosend=nummod>maxObjsPerMsg ? maxObjsPerMsg : nummod;
    int localoffset=offset;
    int sentmsgs=0;
    *((short *)(writeBuffer+offset)) = (short) (sizeof(unsigned int) * numtosend);
    localoffset += sizeof(short);

    for(; j < pilecount; j++) {
      for(; i < tdata[j].f.nummod; i++) {
        *((unsigned int *) (writeBuffer+localoffset)) = tdata[j].oidmod[i];  //copy objects
        localoffset += sizeof(unsigned int);
        if ((++sentmsgs)==numtosend) {
          i++;
          goto send;
        }
      }
      i=0;
    }
send:
    if(sendto(udpSockFd, (const void *) writeBuffer, localoffset, 0, (const struct sockaddr *)clientaddr, sizeof(struct sockaddr_in)) < 0) {
      perror("sendto error- ");
      printf("DEBUG-> sendto error: errorno %d\n", errno);
      return -1;
    }
    nummod= nummod - numtosend;
  }
  return 0;
}

/* Function searches given oid in prefetch cache and invalidates obj from cache
 * returns -1 on error and 0 on success */
int invalidateFromPrefetchCache(char *buffer) {
  int offset = sizeof(short);
  /* Read mid from msg */
  unsigned int mid = *((unsigned int *)(buffer+offset));
  offset += sizeof(unsigned int);
  //Invalidate only if broadcast if from different machine
  if(mid != myIpAddr) {
    /* Read objects sent */
    int numObjsRecv = *((short *)(buffer+offset)) / sizeof(unsigned int);
    offset += sizeof(short);
    int i;
#ifdef ABORTREADERS
    removetransaction((unsigned int *)(buffer+offset), numObjsRecv);
#endif
    for(i = 0; i < numObjsRecv; i++) {
      unsigned int oid;
      oid = *((unsigned int *)(buffer+offset));
      objheader_t *header;
      /* Lookup Objects in prefetch cache and remove them */
      if(((header = prehashSearch(oid)) != NULL)) {
        //Keep invalid objects
        STATUS(header)=DIRTY;
        //prehashRemove(oid);
      }
      offset += sizeof(unsigned int);
    }
  }
  return 0;
}

