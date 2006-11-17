#include "runtime.h"
#include "structdefs.h"
#include <sys/socket.h>
#include <fcntl.h>
#include <arpa/inet.h>
#include <strings.h>
#include <errno.h>
#include "SimpleHash.h"
#include "GenericHashtable.h"

extern struct RuntimeHash *fdtoobject;

int CALL12(___ServerSocket______createSocket____I, int port, struct ___ServerSocket___ * ___this___, int port) {
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
  RuntimeHashadd(fdtoobject, fd, (int) VAR(___this___));
  addreadfd(fd);
  return fd;
}

int CALL02(___ServerSocket______nativeaccept____L___Socket___,struct ___ServerSocket___ * ___this___, struct ___Socket___ * ___s___) {
  struct sockaddr_in sin;
  unsigned int sinlen=sizeof(sin);
  int fd=VAR(___this___)->___fd___;
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

  RuntimeHashadd(fdtoobject, newfd, (int) VAR(___s___));
  addreadfd(newfd);
  flagorand(VAR(___this___),0,0xFFFFFFFE);
  return newfd;
}


void CALL02(___Socket______nativeWrite_____AR_B, struct ___Socket___ * ___this___, struct ArrayObject * ___b___) {
  int fd=VAR(___this___)->___fd___;
  int length=VAR(___b___)->___length___;
  char * charstr=((char *)& VAR(___b___)->___length___)+sizeof(int);
  while(1) {
    int bytewritten=write(fd, charstr, length);
    if (bytewritten==-1&&errno==EAGAIN)
      continue;

    if (bytewritten!=length) {
      perror("ERROR IN NATIVEWRITE");
    }
    break;
  }
  //  flagorand(VAR(___this___),0,0xFFFFFFFE);
}

int CALL02(___Socket______nativeRead_____AR_B, struct ___Socket___ * ___this___, struct ArrayObject * ___b___) {
  int fd=VAR(___this___)->___fd___;
  int length=VAR(___b___)->___length___;
  char * charstr=((char *)& VAR(___b___)->___length___)+sizeof(int);
  int byteread=read(fd, charstr, length);
  
  if (byteread<0) {
    printf("ERROR IN NATIVEREAD\n");
  }
  flagorand(VAR(___this___),0,0xFFFFFFFE);
  return byteread;
}

void CALL01(___Socket______nativeClose____, struct ___Socket___ * ___this___) {
  int fd=VAR(___this___)->___fd___;
  int data;
  RuntimeHashget(fdtoobject, fd, &data);
  RuntimeHashremove(fdtoobject, fd, data);
  removereadfd(fd);
  close(fd);
  flagorand(VAR(___this___),0,0xFFFFFFFE);
}
