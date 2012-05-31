#ifndef MULTICORE
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#endif
#include <sys/types.h>
#include "structdefs.h"
#include "mem.h"
#include "runtime.h"
#include "methodheaders.h"
#ifdef INPUTFILE
#include "InputFileArrays.h"
#endif

#ifdef D___FileOutputStream______nativeWrite____I__AR_B_I_I
void CALL34(___FileOutputStream______nativeWrite____I__AR_B_I_I, int fd, int off, int len, int fd, struct ArrayObject * ___array___, int off, int len) {
#ifdef MULTICORE
#else
  char * string= (((char *)&VAR(___array___)->___length___)+sizeof(int));
  int status=write(fd, &string[off], len);
#endif
}
#endif

#ifdef D___FileOutputStream______nativeClose____I
void CALL11(___FileOutputStream______nativeClose____I, int fd, int fd) {
#ifdef MULTICORE
#else
  close(fd);
#endif
}
#endif

#ifdef D___FileOutputStream______nativeFlush____I
void CALL11(___FileOutputStream______nativeFlush____I, int fd, int fd) {
  // not supported in RAW version
#ifdef MULTICORE
#else
  fsync(fd);
#endif
}
#endif

#ifdef D___FileOutputStream______nativeOpen_____AR_B
int CALL01(___FileOutputStream______nativeOpen_____AR_B, struct ArrayObject * ___filename___) {
  int length=VAR(___filename___)->___length___;
  char* filename= (((char *)&VAR(___filename___)->___length___)+sizeof(int));
#ifdef MULTICORE
#ifdef INPUTFILE
  int fd=filename2fd(filename, length);
  return fd;
#else
  return 0;
#endif
#else
  int fd=open(filename, O_WRONLY|O_CREAT|O_TRUNC, S_IRWXU);
  return fd;
#endif
}
#endif

#ifdef D___FileOutputStream______nativeAppend_____AR_B
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
#endif

#ifdef D___FileInputStream______nativeOpen_____AR_B
int CALL01(___FileInputStream______nativeOpen_____AR_B, struct ArrayObject * ___filename___) {
  int length=VAR(___filename___)->___length___;
  char* filename= (((char *)&VAR(___filename___)->___length___)+sizeof(int));
#ifdef MULTICORE
#ifdef INPUTFILE
  int fd=filename2fd(filename, length);
  return fd;
#else
  return 0;
#endif
#else
  int fd;
  if ((fd=open(filename, O_RDONLY, 0)) < 0) {
    printf(">>>\n");
    perror("open failed");
    printf("filename is %s\n", filename);
    system("pwd");
    printf("<<<\n");
  }
  return fd;
#endif
}
#endif

#ifdef D___FileInputStream______nativeClose____I
void CALL11(___FileInputStream______nativeClose____I, int fd, int fd) {
#ifdef MULTICORE
#else
  close(fd);
#endif
}
#endif

#ifdef D___FileInputStream______nativeRead____I__AR_B_I
int CALL23(___FileInputStream______nativeRead____I__AR_B_I, int fd, int numBytes, int fd, struct ArrayObject * ___array___, int numBytes) {
#ifdef MULTICORE
  return -1;
#else
  int toread=VAR(___array___)->___length___;
  char* string= (((char *)&VAR(___array___)->___length___)+sizeof(int));
  int status;

  if (numBytes<toread)
    toread=numBytes;

  if ((status=read(fd, string, toread)) < 0) {
    perror("");
  }
  return status;
#endif
}
#endif

#ifdef D___FileInputStream______nativePeek____I
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
#endif

#ifdef D___File______nativeLength_____AR_B
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
#endif

#ifdef D___FileInputStream______nativeAvailable____I
int CALL11(___FileInputStream______nativeAvailable____I, int fd, int fd) {
#ifdef MULTICORE
  return 0;
#else
  int avail;
  int cur=lseek(fd, 0, SEEK_CUR);
  int fsize = lseek(fd, 0, SEEK_END);
  lseek(fd,cur,SEEK_SET); // seek back to the current position
  avail=fsize-cur;
  return avail;
#endif
}
#endif
