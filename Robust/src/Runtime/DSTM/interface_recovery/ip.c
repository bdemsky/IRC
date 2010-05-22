#include <stdio.h>
#include <stdlib.h>
#include "ip.h"
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>
#include <netinet/in.h>
#include <string.h>
#include <sys/ioctl.h>
#include <net/if.h>

#define LISTEN_PORT 2156

unsigned int iptoMid(char *addr) {
  ip_t i;
  unsigned int mid;

  sscanf(addr, "%d.%d.%d.%d", &i.a, &i.b, &i.c, &i.d);
  mid = (i.a << 24) | (i.b << 16) | (i.c << 8) | i.d;
  fflush(stdout);
  return mid;
}

void midtoIP(unsigned int mid, char *ptr) {
  ip_t i;

  i.a = (mid & 0xff000000) >> 24;
  i.b = (mid & 0x00ff0000) >> 16;
  i.c = (mid & 0x0000ff00) >> 8;
  i.d = mid & 0x000000ff;
  sprintf(ptr, "%d.%d.%d.%d", i.a, i.b, i.c, i.d);
  return;
}

int checkServer(int mid, char *machineip) {
  int tmpsd;
  struct sockaddr_in serv_addr;
  char m[20];

  strncpy(m, machineip, strlen(machineip));
  // Foreach machine you want to transact with
  // check if its up and running
  if ((tmpsd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
    perror("");
    return(-1);
  }
  bzero((char*) &serv_addr, sizeof(serv_addr));
  serv_addr.sin_family = AF_INET;
  serv_addr.sin_port = htons(LISTEN_PORT);
  midtoIP(mid, m);
  m[15] = '\0';
  serv_addr.sin_addr.s_addr = inet_addr(m);
  while (connect(tmpsd, (struct sockaddr *) &serv_addr, sizeof(struct sockaddr)) < 0) {
    sleep(1);
  }
  close(tmpsd);
  return 0;
}

unsigned int getMyIpAddr(const char *interfaceStr) {
  int sock;
  struct ifreq interfaceInfo;
  struct sockaddr_in *myAddr = (struct sockaddr_in *)&interfaceInfo.ifr_addr;

  memset(&interfaceInfo, 0, sizeof(struct ifreq));

  if((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
    perror("getMyIpAddr():socket()");
    return 1;
  }

  strcpy(interfaceInfo.ifr_name, interfaceStr);
  myAddr->sin_family = AF_INET;

  if(ioctl(sock, SIOCGIFADDR, &interfaceInfo) != 0) {
    perror("getMyIpAddr():ioctl()");
    return 1;
  }

  close(sock);

  return ntohl(myAddr->sin_addr.s_addr);
}

/*
   main() {
        unsigned int mid;
        ip_t i;
        char ip[16];

        memset(ip, 0, 16);
        mid = iptoMid("192.10.0.1");
        printf("mid = %x\n", mid);
        midtoIP(mid, ip);
        ip[15] = '\0';
        printf("%s\n",ip);
   }
 */
