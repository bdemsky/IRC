#ifndef READSTRUCT_H
#define READSTRUCT_H
#define MAXBUF 1024
struct readstruct {
  char buf[MAXBUF];
  int head;
  int tail;
};

#define WMAXBUF 2048
#define WTOP 512
struct writestruct {
  char buf[WMAXBUF];
  int offset;
};

void recv_data_buf(int fd, struct readstruct *, void *buffer, int buflen);
int recv_data_errorcode_buf(int fd, struct readstruct *, void *buffer, int buflen);
void send_buf(int fd, struct writestruct * sendbuffer, void *buffer, int buflen);
void forcesend_buf(int fd, struct writestruct * sendbuffer, void *buffer, int buflen);

#endif
