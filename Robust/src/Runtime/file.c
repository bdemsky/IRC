#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/uio.h>
#include <unistd.h>
#include "structdefs.h"
#include "mem.h"


void ___FileOutputStream______nativeWrite____I__AR_B(int fd, struct ArrayObject * ao) {
  int length=ao->___length___;
  char * string= (((char *)& ao->___length___)+sizeof(int));
  int status=write(fd, string, length);
}

void ___FileOutputStream______nativeClose____I(int fd) {
  close(fd);
}

int ___FileOutputStream______nativeOpen_____AR_B(struct ArrayObject * ao) {
  int length=ao->___length___;
  char* filename= (((char *)& ao->___length___)+sizeof(int));
  int fd=open(filename, O_WRONLY|O_CREAT|O_TRUNC, S_IRWXU);
  return fd;
}

int ___FileInputStream______nativeOpen_____AR_B(struct ArrayObject * ao) {
  int length=ao->___length___;
  char* filename= (((char *)& ao->___length___)+sizeof(int));
  int fd=open(filename, O_RDONLY, 0);
  return fd;
}

void ___FileInputStream______nativeClose____I(int fd) {
  close(fd);
}

int ___FileInputStream______nativeRead____I__AR_B_I(int fd, struct ArrayObject * ao, int numBytes) {
  int toread=ao->___length___;
  char* string= (((char *)& ao->___length___)+sizeof(int));
  int status;

  if (numBytes<toread)
    toread=numBytes;

  status=read(fd, string, toread);
  return status;
}
