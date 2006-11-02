#include "runtime.h"
#include "structdefs.h"
#include <sys/socket.h>
#include <fcntl.h>
#include <arpa/inet.h>
#include <strings.h>
#include "SimpleHash.h"
#include "GenericHashtable.h"
extern struct RuntimeHash *fdtoobject;

int ___ServerSocket______createSocket____I(struct ___ServerSocket___ * sock, int port) {
  int fd;

  int n=1;
  struct sockaddr_in sin;

  bzero (&sin, sizeof (sin));
  sin.sin_family = AF_INET;
  sin.sin_port = htons (port);
  sin.sin_addr.s_addr = htonl (INADDR_ANY);
  fd=socket(AF_INET, SOCK_STREAM, 0);
  if (fd<0) {
#ifdef DEBUG
    perror(NULL);
    printf("createSocket error #1\n");
#endif
    longjmp(error_handler,5);
  }

  if (setsockopt (fd, SOL_SOCKET, SO_REUSEADDR, (char *)&n, sizeof (n)) < 0) {
    close(fd);
#ifdef DEBUG
    perror(NULL);
    printf("createSocket error #2\n");
#endif
    longjmp(error_handler, 6);
  }
  fcntl(fd, F_SETFD, 1);
  fcntl(fd, F_SETFL, fcntl(fd, F_GETFL)|O_NONBLOCK);

  /* bind to port */
  if (bind(fd, (struct sockaddr *) &sin, sizeof(sin))<0) { 
    close (fd);
#ifdef DEBUG
    perror(NULL);
    printf("createSocket error #3\n");
#endif
    longjmp(error_handler, 7);
  }

  /* listen */
  if (listen(fd, 5)<0) { 
    close (fd);
#ifdef DEBUG
    perror(NULL);
    printf("createSocket error #4\n");
#endif
    longjmp(error_handler, 8);
  }

  /* Store the fd/socket object mapping */
  RuntimeHashadd(fdtoobject, fd, (int) sock);
  addreadfd(fd);
  return fd;
}

int ___ServerSocket______nativeaccept____L___Socket___(struct ___ServerSocket___ * serversock, struct ___Socket___ * sock) {
  struct sockaddr_in sin;
  unsigned int sinlen=sizeof(sin);
  int fd=serversock->___fd___;
  int newfd;
  newfd=accept(fd, (struct sockaddr *)&sin, &sinlen);


  if (newfd<0) { 
#ifdef DEBUG
    perror(NULL);
    printf("acceptSocket error #1\n");
#endif
    longjmp(error_handler, 9);
  }
  fcntl(newfd, F_SETFL, fcntl(fd, F_GETFL)|O_NONBLOCK);

  RuntimeHashadd(fdtoobject, newfd, (int) sock);
  addreadfd(newfd);
  flagorand(serversock,0,0xFFFFFFFE);
  return newfd;
}


void ___Socket______nativeWrite_____AR_B(struct ___Socket___ * sock, struct ArrayObject * ao) {
  int fd=sock->___fd___;
  int length=ao->___length___;
  char * charstr=((char *)& ao->___length___)+sizeof(int);
  int bytewritten=write(fd, charstr, length);
  if (bytewritten!=length) {
    printf("ERROR IN NATIVEWRITE\n");
  }
  flagorand(sock,0,0xFFFFFFFE);
}

int ___Socket______nativeRead_____AR_B(struct ___Socket___ * sock, struct ArrayObject * ao) {
  int fd=sock->___fd___;
  int length=ao->___length___;
  char * charstr=((char *)& ao->___length___)+sizeof(int);
  int byteread=read(fd, charstr, length);
  
  if (byteread<0) {
    printf("ERROR IN NATIVEREAD\n");
  }
  flagorand(sock,0,0xFFFFFFFE);
  return byteread;
}

void ___Socket______nativeClose____(struct ___Socket___ * sock) {
  int fd=sock->___fd___;
  int data;
  RuntimeHashget(fdtoobject, fd, &data);
  RuntimeHashremove(fdtoobject, fd, data);
  removereadfd(fd);
  close(fd);
  flagorand(sock,0,0xFFFFFFFE);
}
