#ifndef MULTICORE
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#endif
#include <sys/types.h>
#include "structdefs.h"
#include "mem.h"
#include "runtime.h"

void CALL34(___FileOutputStream______nativeWrite____I__AR_B_I_I, int fd, int off, int len, int fd, struct ArrayObject * ___array___, int off, int len) {
#ifdef MULTICORE
#else
  char * string= (((char *)&VAR(___array___)->___length___)+sizeof(int));
  int status=write(fd, &string[off], len);
#endif
}

void CALL11(___FileOutputStream______nativeClose____I, int fd, int fd) {
#ifdef MULTICORE
#else
  close(fd);
#endif
}

void CALL11(___FileOutputStream______nativeFlush____I, int fd, int fd) {
  // not supported in RAW version
#ifdef MULTICORE
#else
  fsync(fd);
#endif
}

int CALL01(___FileOutputStream______nativeOpen_____AR_B, struct ArrayObject * ___filename___) {
#ifdef MULTICORE
  return 0;
#else
  int length=VAR(___filename___)->___length___;
  char* filename= (((char *)&VAR(___filename___)->___length___)+sizeof(int));
  int fd=open(filename, O_WRONLY|O_CREAT|O_TRUNC, S_IRWXU);
  return fd;
#endif
}

int CALL01(___FileOutputStream______nativeAppend_____AR_B, struct ArrayObject * ___filename___) {
#ifdef MULTICORE
  return 0;
#else  
  int length=VAR(___filename___)->___length___;
  char* filename= (((char *)&VAR(___filename___)->___length___)+sizeof(int));
  int fd=open(filename, O_WRONLY|O_CREAT|O_APPEND, S_IRWXU);
  return fd;
#endif
}

int CALL01(___FileInputStream______nativeOpen_____AR_B, struct ArrayObject * ___filename___) {
#ifdef MULTICORE
  return 0;
#else
  int length=VAR(___filename___)->___length___;
  char* filename= (((char *)&VAR(___filename___)->___length___)+sizeof(int));
  int fd=open(filename, O_RDONLY, 0);
  return fd;
#endif
}

void CALL11(___FileInputStream______nativeClose____I, int fd, int fd) {
#ifdef MULTICORE
#else
  close(fd);
#endif
}

int CALL23(___FileInputStream______nativeRead____I__AR_B_I, int fd, int numBytes, int fd, struct ArrayObject * ___array___, int numBytes) {
#ifdef MULTICORE
  return -1;
#else
  int toread=VAR(___array___)->___length___;
  char* string= (((char *)&VAR(___array___)->___length___)+sizeof(int));
  int status;

  if (numBytes<toread)
    toread=numBytes;

  status=read(fd, string, toread);
  return status;
#endif
}

int CALL11(___FileInputStream______nativePeek____I, int fd, int fd) {
#ifdef MULTICORE
  return 0;
#else
  int status;
  char string[1];
  status=read(fd, string, 1);

  if( status <= 0 ) {
    return status;
  }
  lseek(fd, -1, SEEK_CUR);
  return string[0];
#endif
}

long long CALL01(___File______nativeLength_____AR_B, struct ArrayObject * ___pathname___) {
#ifdef MULTICORE
  return 0;
#else
  int length=VAR(___pathname___)->___length___;
  char* filename= (((char *)&VAR(___pathname___)->___length___)+sizeof(int));
  struct stat st;
  stat(filename, &st);
  return st.st_size;
#endif
}
