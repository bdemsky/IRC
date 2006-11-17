#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/uio.h>
#include <unistd.h>
#include "structdefs.h"
#include "mem.h"
#include "runtime.h"

void CALL12(___FileOutputStream______nativeWrite____I__AR_B, int fd, int fd, struct ArrayObject * ___array___) {
  int length=VAR(___array___)->___length___;
  char * string= (((char *)& VAR(___array___)->___length___)+sizeof(int));
  int status=write(fd, string, length);
}

void CALL11(___FileOutputStream______nativeClose____I, int fd, int fd) {
  close(fd);
}

void CALL11(___FileOutputStream______nativeFlush____I, int fd, int fd) {
  fsync(fd);
}

int CALL01(___FileOutputStream______nativeOpen_____AR_B, struct ArrayObject * ___filename___) {
  int length=VAR(___filename___)->___length___;
  char* filename= (((char *)& VAR(___filename___)->___length___)+sizeof(int));
  int fd=open(filename, O_WRONLY|O_CREAT|O_TRUNC, S_IRWXU);
  return fd;
}

int CALL01(___FileOutputStream______nativeAppend_____AR_B, struct ArrayObject * ___filename___) {
  int length=VAR(___filename___)->___length___;
  char* filename= (((char *)& VAR(___filename___)->___length___)+sizeof(int));
  int fd=open(filename, O_WRONLY|O_CREAT|O_APPEND, S_IRWXU);
  return fd;
}

int CALL01(___FileInputStream______nativeOpen_____AR_B, struct ArrayObject * ___filename___) {
  int length=VAR(___filename___)->___length___;
  char* filename= (((char *)& VAR(___filename___)->___length___)+sizeof(int));
  int fd=open(filename, O_RDONLY, 0);
  return fd;
}

void CALL11(___FileInputStream______nativeClose____I, int fd, int fd) {
  close(fd);
}

int CALL23(___FileInputStream______nativeRead____I__AR_B_I, int fd, int numBytes, int fd, struct ArrayObject * ___array___, int numBytes) {
  int toread=VAR(___array___)->___length___;
  char* string= (((char *)& VAR(___array___)->___length___)+sizeof(int));
  int status;

  if (numBytes<toread)
    toread=numBytes;

  status=read(fd, string, toread);
  return status;
}

long long CALL01(___File______nativeLength_____AR_B, struct ArrayObject * ___pathname___) {
  int length=VAR(___pathname___)->___length___;
  char* filename= (((char *)& VAR(___pathname___)->___length___)+sizeof(int));
  struct stat st;
  stat(filename, &st);
  return st.st_size;
}
