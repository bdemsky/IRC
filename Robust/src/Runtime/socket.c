#include "runtime.h"
#include "structdefs.h"
#ifndef MULTICORE
#include <fcntl.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <strings.h>
#include <netdb.h>
#include <netinet/tcp.h>
#include <errno.h>
#endif
#include "SimpleHash.h"
#include "GenericHashtable.h"
#include "methodheaders.h"

struct RuntimeHash *fdtoobject;

#ifdef D___Socket______nativeConnect____I__AR_B_I
int CALL24(___Socket______nativeConnect____I__AR_B_I, int ___fd___, int ___port___, struct ___Socket___ * ___this___, int ___fd___, struct ArrayObject * ___address___,int ___port___) {
#ifdef MULTICORE
  // not supported in MULTICORE version
  return -1;
#else
  struct sockaddr_in sin;
  int rc;

  bzero(&sin, sizeof(sin));
  sin.sin_family= AF_INET;
  sin.sin_port=htons(___port___);
  sin.sin_addr.s_addr=htonl(*(int *)(((char *)&VAR(___address___)->___length___)+sizeof(int)));
#if defined(THREADS)||defined(DSTM)||defined(STM)
#ifdef PRECISE_GC
  stopforgc((struct garbagelist *)___params___);
#endif
#endif
  do {
    rc = connect(___fd___, (struct sockaddr *) &sin, sizeof(sin));
  } while (rc<0 && errno==EINTR); /* repeat if interrupted */
#if defined(THREADS)||defined(DSTM)||defined(STM)
#ifdef PRECISE_GC
  restartaftergc();
#endif
#endif

  {
  int flag = 1;
  setsockopt(___fd___, IPPROTO_TCP, TCP_NODELAY, (char *) &flag, sizeof(flag));
  }
  if (rc<0) goto error;

#ifdef TASK
  //Make non-blocking
  fcntl(___fd___, F_SETFD, 1);
  fcntl(___fd___, F_SETFL, fcntl(___fd___, F_GETFL)|O_NONBLOCK);
  RuntimeHashadd(fdtoobject, ___fd___, (int) VAR(___this___));
  addreadfd(___fd___);
#endif

  return 0;

error:
  close(___fd___);
  return -1;
#endif
}
#endif

#ifdef TASK
#ifdef D___Socket______nativeBindFD____I
int CALL12(___Socket______nativeBindFD____I, int ___fd___, struct ___Socket___ * ___this___, int ___fd___) {
#ifdef MULTICORE
#else
  if (RuntimeHashcontainskey(fdtoobject, ___fd___))
    RuntimeHashremovekey(fdtoobject, ___fd___);
  RuntimeHashadd(fdtoobject, ___fd___, (int) VAR(___this___));
  addreadfd(___fd___);
#endif
  return 0;
}
#endif
#endif

#ifdef D___Socket______nativeBind_____AR_B_I
int CALL12(___Socket______nativeBind_____AR_B_I, int ___port___,  struct ArrayObject * ___address___, int ___port___) {
#ifdef MULTICORE
  // not supported in MULTICORE version
  return -1;
#else
  int fd;
  int rc;
  socklen_t sa_size;
  struct sockaddr_in sin;
  bzero(&sin, sizeof(sin));
  sin.sin_family= AF_INET;
  sin.sin_port=0;
  sin.sin_addr.s_addr=INADDR_ANY;

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
#endif
}
#endif

#ifdef D___InetAddress______getHostByName_____AR_B
struct ArrayObject * CALL01(___InetAddress______getHostByName_____AR_B, struct ArrayObject * ___hostname___) {
#ifdef MULTICORE
  // not supported in MULTICORE version
  return NULL;
#else
//struct ArrayObject * CALL01(___InetAddress______getHostByName_____AR_B, struct ___ArrayObject___ * ___hostname___) {
  int length=VAR(___hostname___)->___length___;
  int i,j,n;
  char * str=malloc(length+1);
  struct hostent *h;
  struct ArrayObject * arraybytearray;

  for(i=0; i<length; i++) {
    str[i]=(((char *)&VAR(___hostname___)->___length___)+sizeof(int))[i];
  }
  str[length]=0;
  h=gethostbyname(str);
  free(str);

	if (h != NULL) {
		for (n=0; h->h_addr_list[n]; n++) /* do nothing */ ;

#ifdef PRECISE_GC
  arraybytearray=allocate_newarray(___params___,BYTEARRAYARRAYTYPE,n);
#else
  arraybytearray=allocate_newarray(BYTEARRAYARRAYTYPE,n);
#endif
  for(i=0; i<n; i++) {
    struct ArrayObject *bytearray;
#ifdef PRECISE_GC
    {
      INTPTR ptrarray[]={1, (INTPTR) ___params___, (INTPTR)arraybytearray};
      bytearray=allocate_newarray(&ptrarray,BYTEARRAYTYPE,h->h_length);
      arraybytearray=(struct ArrayObject *) ptrarray[2];
    }
#else
    bytearray=allocate_newarray(BYTEARRAYTYPE,h->h_length);
#endif
    ((void **)&((&arraybytearray->___length___)[1]))[i]=bytearray;
    {
      int ha=ntohl(*(int *)h->h_addr_list[i]);
      (&bytearray->___length___)[1]=ha;
    }
  }

  return arraybytearray;
	} else {
		return NULL;
	}
#endif
}
#endif

#ifdef D___ServerSocket______createSocket____I
int CALL12(___ServerSocket______createSocket____I, int port, struct ___ServerSocket___ * ___this___, int port) {
#ifdef MULTICORE
  // not supported in MULTICORE version
  return -1;
#else
  int fd;

  int n=1;
  struct sockaddr_in sin;

  bzero(&sin, sizeof(sin));
  sin.sin_family = AF_INET;
  sin.sin_port = htons(port);
  sin.sin_addr.s_addr = htonl(INADDR_ANY);
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

  if (setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, (char *)&n, sizeof (n)) < 0) {
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

#ifdef MAC
  if (setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &n, sizeof (n)) < 0) {
    perror("socket");
    exit(-1);
  }
#endif

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
    close(fd);
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
#endif
}
#endif

#ifdef D___ServerSocket______nativeaccept____L___Socket___
int CALL02(___ServerSocket______nativeaccept____L___Socket___,struct ___ServerSocket___ * ___this___, struct ___Socket___ * ___s___) {
#ifdef MULTICORE
  // not supported in MULTICORE version
  return -1;
#else
  struct sockaddr_in sin;
  unsigned int sinlen=sizeof(sin);
  int fd=VAR(___this___)->___fd___;
  int newfd;
#if defined(THREADS)||defined(DSTM)||defined(STM)
#ifdef PRECISE_GC
  stopforgc((struct garbagelist *)___params___);
#endif
#endif
  newfd=accept(fd, (struct sockaddr *)&sin, &sinlen);
  int flag = 1;
  setsockopt(newfd, IPPROTO_TCP, TCP_NODELAY, (char *) &flag, sizeof(flag));
#if defined(THREADS)||defined(DSTM)||defined(STM)
#ifdef PRECISE_GC
  restartaftergc();
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
#ifdef MULTICORE
  flagorand(VAR(___this___),0,0xFFFFFFFE,NULL,0);
  enqueueObject(VAR(___this___), NULL, 0);
#else
  flagorand(VAR(___this___),0,0xFFFFFFFE);
  enqueueObject(VAR(___this___));
#endif
#endif
  return newfd;
#endif
}
#endif

#ifdef D___Socket______nativeWrite_____AR_B_I_I
void CALL24(___Socket______nativeWrite_____AR_B_I_I, int offset, int length, struct ___Socket___ * ___this___, struct ArrayObject * ___b___, int offset, int length) {
#ifdef MULTICORE
#else
  int fd=VAR(___this___)->___fd___;
  char * charstr=((char *)&VAR(___b___)->___length___)+sizeof(int)+offset;
  while(1) {
    int offset=0;
    int bytewritten;
    while(length>0) {
      bytewritten=write(fd, &charstr[offset], length);
      if (bytewritten==-1&&errno!=EAGAIN)
	break;
      length-=bytewritten;
      offset+=bytewritten;
    }

    if (length!=0) {
      perror("ERROR IN NATIVEWRITE");
      printf("error=%d remaining bytes %d\n",errno, length);
    }
    break;
  }
#endif
}
#endif


#ifdef D___Socket______nativeRead_____AR_B
int CALL02(___Socket______nativeRead_____AR_B, struct ___Socket___ * ___this___, struct ArrayObject * ___b___) {
#ifdef MULTICORE
  return -1;
#else
  int fd=VAR(___this___)->___fd___;
  int length=VAR(___b___)->___length___;

  char * charstr=malloc(length);

#if defined(THREADS)||defined(DSTM)||defined(STM)
#ifdef PRECISE_GC
  stopforgc((struct garbagelist *)___params___);
#endif
#endif
  int byteread=-1;

  do {
    byteread=read(fd, charstr, length);
  } while(byteread==-1&&errno==EINTR);
#if defined(THREADS)||defined(DSTM)||defined(STM)
#ifdef PRECISE_GC
  restartaftergc();
#endif
#endif

  {
    int i;
    for(i=0; i<byteread; i++) {
      (((char *)&VAR(___b___)->___length___)+sizeof(int))[i]=charstr[i];
    }
    free(charstr);
  }


  if (byteread<0) {
#ifndef MULTICORE
    printf("ERROR IN NATIVEREAD\n");
    perror("");
#endif
  }
#ifdef TASK
#ifdef MULTICORE
  flagorand(VAR(___this___),0,0xFFFFFFFE,NULL,0);
  enqueueObject(VAR(___this___), NULL, 0);
#else
  flagorand(VAR(___this___),0,0xFFFFFFFE);
  enqueueObject(VAR(___this___));
#endif
#endif
  return byteread;
#endif
}
#endif

#ifdef D___Socket______nativeClose____
void CALL01(___Socket______nativeClose____, struct ___Socket___ * ___this___) {
#ifdef MULTICORE
#else
  int fd=VAR(___this___)->___fd___;
  int data;
#ifdef TASK
  RuntimeHashget(fdtoobject, fd, &data);
  RuntimeHashremove(fdtoobject, fd, data);
  removereadfd(fd);
  flagorand(VAR(___this___),0,0xFFFFFFFE);
  enqueueObject(VAR(___this___));
#endif
  close(fd);
#endif
}
#endif
