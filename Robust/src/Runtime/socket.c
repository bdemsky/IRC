#include "runtime.h"
#include "structdefs.h"
#include <sys/socket.h>
#include <fcntl.h>
#include <arpa/inet.h>
#include <strings.h>
#include <errno.h>
#include <netdb.h>
#include "SimpleHash.h"
#include "GenericHashtable.h"

extern struct RuntimeHash *fdtoobject;

int CALL23(___Socket______nativeConnect____I__AR_B_I, int ___fd___, int ___port___, int ___fd___, struct ArrayObject * ___address___ ,int ___port___) {
  struct sockaddr_in sin;
  int rc;
  
  bzero(&sin, sizeof(sin));
  sin.sin_family= AF_INET;
  sin.sin_port=htons(___port___);
  sin.sin_addr.s_addr=htonl(*(((char *)&VAR(___address___)->___length___)+sizeof(int)));
  do {
    rc = connect(___fd___, (struct sockaddr *) &sin, sizeof(sin));
  } while (rc<0 && errno==EINTR); /* repeat if interrupted */
  
  if (rc<0) goto error;
  return 0;
  
 error:
  close(___fd___);
  return -1;
}


int CALL12(___Socket______nativeBind_____AR_B_I, int ___port___,  struct ArrayObject * ___address___, int ___port___) {
  int fd;
  int rc;
  socklen_t sa_size;
  struct sockaddr_in sin;
  bzero(&sin, sizeof(sin));
  sin.sin_family= AF_INET;
  sin.sin_port=htons(___port___);
  sin.sin_addr.s_addr=htonl(*(((char *) &VAR(___address___)->___length___)+sizeof(int)));
  
  fd=socket(AF_INET, SOCK_STREAM, 0);
  if (fd<0) {
#ifdef DEBUG
    perror(NULL);
    printf("createSocket error in nativeBind\n");
#endif
#ifdef TASK
    longjmp(error_handler,12);
#else
#ifdef THREADS
    threadexit();
#else
    exit(-1);
#endif
#endif
  }
  
#ifdef TASK
  //Make non-blocking
  fcntl(fd, F_SETFD, 1);
  fcntl(fd, F_SETFL, fcntl(fd, F_GETFL)|O_NONBLOCK);
#endif

  rc = bind(fd, (struct sockaddr *) &sin, sizeof(sin));
  if (rc<0) goto error;

  sa_size = sizeof(sin);
  rc = getsockname(fd, (struct sockaddr *) &sin, &sa_size);
  if (rc<0) goto error;

  return fd;

 error:
  close(fd);
#ifdef DEBUG
  perror(NULL);
  printf("createSocket error #2 in nativeBind\n");
#endif
#ifdef TASK
  longjmp(error_handler,13);
#else
#ifdef THREADS
  threadexit();
#else
  exit(-1);
#endif
#endif
}

struct ArrayObject * CALL01(___InetAddress______getHostByName_____AR_B, struct ___ArrayObject___ * ___hostname___) {
  int length=VAR(___hostname___)->___length___;
  int i,j,n;
  char * str=malloc(length+1);
  struct hostent *h;
  struct ArrayObject * arraybytearray;

  for(i=0;i<length;i++) {
    str[i]=(((char *)&VAR(___hostname___)->___length___)+sizeof(int))[i];
  }
  str[length]=0;
  h=gethostbyname(str);
  free(str);
  
  for (n=0; h->h_addr_list[n]; n++) /* do nothing */ ;
  
#ifdef PRECISE_GC
  arraybytearray=allocate_newarray(___params___,BYTEARRAYARRAYTYPE,n);
#else
  arraybytearray=allocate_newarray(BYTEARRAYARRAYTYPE,n);
#endif
  for(i=0;i<n;i++) {
    struct ArrayObject *bytearray;
#ifdef PRECISE_GC
    {
      int ptrarray[]={1, (int) ___params___, (int)arraybytearray};
      bytearray=allocate_newarray(&ptrarray,BYTEARRAYTYPE,h->h_length);
      arraybytearray=(struct ArrayObject *) ptrarray[2];
    }
#else
    bytearray=allocate_newarray(BYTEARRAYTYPE,h->h_length);
#endif
    ((void **)&((&arraybytearray->___length___)[1]))[i]=bytearray;
    for(j=0;j<h->h_length;j++) {
      ((char *)&((&bytearray->___length___)[1]))[j]=h->h_addr_list[i][j];
    }
  }
  
  return arraybytearray;
}


int CALL12(___ServerSocket______createSocket____I, int port, struct ___ServerSocket___ * ___this___, int port) {
  int fd;

  int n=1;
  struct sockaddr_in sin;

  bzero(&sin, sizeof(sin));
  sin.sin_family = AF_INET;
  sin.sin_port = htons (port);
  sin.sin_addr.s_addr = htonl (INADDR_ANY);
  fd=socket(AF_INET, SOCK_STREAM, 0);
  if (fd<0) {
#ifdef DEBUG
    perror(NULL);
    printf("createSocket error #1\n");
#endif
#ifdef TASK
    longjmp(error_handler,5);
#else
#ifdef THREADS
    threadexit();
#else
    exit(-1);
#endif
#endif
  }

  if (setsockopt (fd, SOL_SOCKET, SO_REUSEADDR, (char *)&n, sizeof (n)) < 0) {
    close(fd);
#ifdef DEBUG
    perror("");
    printf("createSocket error #2\n");
#endif
#ifdef TASK
    longjmp(error_handler,6);
#else
#ifdef THREADS
    threadexit();
#else
    exit(-1);
#endif
#endif
  }

#ifdef TASK
  fcntl(fd, F_SETFD, 1);
  fcntl(fd, F_SETFL, fcntl(fd, F_GETFL)|O_NONBLOCK);
#endif

  /* bind to port */
  if (bind(fd, (struct sockaddr *) &sin, sizeof(sin))<0) { 
    close(fd);
#ifdef DEBUG
    perror("");
    printf("createSocket error #3\n");
#endif
#ifdef TASK
    longjmp(error_handler,7);
#else
#ifdef THREADS
    threadexit();
#else
    exit(-1);
#endif
#endif
  }

  /* listen */
  if (listen(fd, 5)<0) { 
    close (fd);
#ifdef DEBUG
    perror("");
    printf("createSocket error #4\n");
#endif
#ifdef TASK
    longjmp(error_handler,8);
#else
#ifdef THREADS
    threadexit();
#else
    exit(-1);
#endif
#endif
  }

  /* Store the fd/socket object mapping */
#ifdef TASK
  RuntimeHashadd(fdtoobject, fd, (int) VAR(___this___));
  addreadfd(fd);
#endif
  return fd;
}

int CALL02(___ServerSocket______nativeaccept____L___Socket___,struct ___ServerSocket___ * ___this___, struct ___Socket___ * ___s___) {
  struct sockaddr_in sin;
  unsigned int sinlen=sizeof(sin);
  int fd=VAR(___this___)->___fd___;
  int newfd;
#ifdef THREADS
#ifdef PRECISE_GC
  struct listitem *tmp=stopforgc((struct garbagelist *)___params___);
#endif
#endif
  newfd=accept(fd, (struct sockaddr *)&sin, &sinlen);
#ifdef THREADS 
#ifdef PRECISE_GC
  restartaftergc(tmp);
#endif
#endif
  if (newfd<0) { 
#ifdef DEBUG
    perror(NULL);
    printf("acceptSocket error #1\n");
#endif
#ifdef TASK
    longjmp(error_handler,9);
#else
#ifdef THREADS
    threadexit();
#else
    exit(-1);
#endif
#endif
  }
#ifdef TASK
  fcntl(newfd, F_SETFL, fcntl(fd, F_GETFL)|O_NONBLOCK);
  RuntimeHashadd(fdtoobject, newfd, (int) VAR(___s___));
  addreadfd(newfd);
  flagorand(VAR(___this___),0,0xFFFFFFFE);
#endif
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
}

int CALL02(___Socket______nativeRead_____AR_B, struct ___Socket___ * ___this___, struct ArrayObject * ___b___) {
  int fd=VAR(___this___)->___fd___;
  int length=VAR(___b___)->___length___;

  char * charstr=malloc(length);
  
#ifdef THREADS
#ifdef PRECISE_GC
  struct listitem *tmp=stopforgc((struct garbagelist *)___params___);
#endif
#endif
  int byteread=read(fd, charstr, length);
#ifdef THREADS
#ifdef PRECISE_GC
  restartaftergc(tmp);
#endif
#endif

  {
    int i;
    for(i=0;i<byteread;i++) {
      (((char *)& VAR(___b___)->___length___)+sizeof(int))[i]=charstr[i];
    }
    free(charstr);
  }


  if (byteread<0) {
    printf("ERROR IN NATIVEREAD\n");
    return 0;
  }
#ifdef TASK
  flagorand(VAR(___this___),0,0xFFFFFFFE);
#endif
  return byteread;
}

void CALL01(___Socket______nativeClose____, struct ___Socket___ * ___this___) {
  int fd=VAR(___this___)->___fd___;
  int data;
#ifdef TASK
  RuntimeHashget(fdtoobject, fd, &data);
  RuntimeHashremove(fdtoobject, fd, data);
  removereadfd(fd);
  flagorand(VAR(___this___),0,0xFFFFFFFE);
#endif
  close(fd);
}
