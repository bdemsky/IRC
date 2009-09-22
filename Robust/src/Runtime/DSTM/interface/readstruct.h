#ifndef READSTRUCT_H
#define READSTRUCT_H
#define MAXBUF 1024
struct readstruct {
  char buf[MAXBUF];
  int head;
  int tail;
};

void recv_data_buf(int fd, struct readstruct *, void *buffer, int buflen);
int recv_data_errorcode_buf(int fd, struct readstruct *, void *buffer, int buflen);

#endif
