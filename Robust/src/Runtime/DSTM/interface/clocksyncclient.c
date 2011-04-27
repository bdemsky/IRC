/** This program runs the client for clock synchronization on all machines
   Client on all machines **/
// One clock tick =  (1 / CPU processor speed in Hz) secs
//compile:
//    gcc -Wall -o server clocksyncclient.c
//

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <string.h>
#include <math.h>

#define PORT        8500
/* REPLACE with your server machine name*/
#define DIRSIZE     64
#define NUMITER   10000


static __inline__ unsigned long long rdtsc(void) {
  unsigned hi, lo;
  __asm__ __volatile__ ("rdtsc" : "=a" (lo), "=d" (hi));
  return ( (unsigned long long)lo)|( ((unsigned long long)hi)<<32 );
}

int main(int argc, char **argv) {
  unsigned long long dir[1];
  int sd;
  struct sockaddr_in pin;
  struct hostent *hp;

  unsigned long long array1[NUMITER];
  unsigned long long array2[NUMITER];

  char *hostname=NULL;
  if(argc == 1) {
    printf("%s\n", "This program needs arguments....\n\n");
    exit(0);
  }

  if((strcmp(argv[1], "2")) == 0) {
    hostname="dc-2";
  } else if((strcmp(argv[1], "3")) == 0) {
    hostname="dc-3";
  } else if((strcmp(argv[1], "4")) == 0) {
    hostname="dc-4";
  } else if((strcmp(argv[1], "5")) == 0) {
    hostname="dc-5";
  } else if ((strcmp(argv[1], "6")) == 0) {
    hostname="dc-6";
  } else if ((strcmp(argv[1], "7")) == 0) {
    hostname="dc-7";
  } else if ((strcmp(argv[1], "8")) == 0) {
    hostname="dc-8";
  } else {
    printf("hostname is not known \n");
    exit(-1);
  }

  FILE *f1;
  f1=fopen(hostname, "w");

  /* go find out about the desired host machine */
  if ((hp = gethostbyname("dc-1.calit2.uci.edu")) == 0) {
    perror("gethostbyname");
    exit(1);
  }

  /* fill in the socket structure with host information */
  memset(&pin, 0, sizeof(pin));
  pin.sin_family = AF_INET;
  pin.sin_addr.s_addr = ((struct in_addr *)(hp->h_addr))->s_addr;
  pin.sin_port = htons(PORT);

  /* grab an Internet domain socket */
  if ((sd = socket(AF_INET, SOCK_STREAM, 0)) == -1) {
    perror("socket");
    exit(1);
  }

  /* connect to PORT on HOST */
  if (connect(sd,(struct sockaddr *)  &pin, sizeof(pin)) == -1) {
    perror("connect");
    exit(1);
  }

  int i;
  char data[1];
  long long norm = 0;
  recv(sd, data, sizeof(data), 0);
  for (i=0; i<NUMITER; i++) {
    /* send a message to the server PORT on machine HOST */
    array1[i]=rdtsc();
    if (send(sd, &array1[i], sizeof(unsigned long long), MSG_NOSIGNAL) == -1) {
      perror("send");
      exit(1);
    }
    //printf("DEBUG: array1[i]= %lld\n", array1[i]);

    /* wait for a message to come back from the server */
    if (recv(sd, dir, sizeof(unsigned long long), 0) == -1) {
      perror("recv");
      exit(1);
    }
    //printf("DEBUG: dir[0]= %lld\n", dir[0]);
    array2[i]=rdtsc() - dir[0];
    printf("%lld\n", array2[i]);
  }

  for(i=0; i<(NUMITER-1); i++) {
    norm += array2[i];
  }



  /* spew-out the results */
  //printf("DEBUG: Average offset= %lld\n", (norm/(NUMITER-1)));
  long long average=(norm/(NUMITER-1));
  printf("average= %lld",(norm/(NUMITER-1)));
  long long stddev, avg1=0;
  for(i=0; i<(NUMITER-1); i++) {
    avg1 += ((array2[i] - average) * (array2[i] - average));
  }
  float ans = (avg1/(NUMITER-1));
  float squareroot= sqrt(ans);
  float squareroot2= sqrt(avg1);

  printf("stddev= %f\n", squareroot);
  printf("error= %f\n", squareroot2/(NUMITER-1));

  fprintf(f1,"%lld",(norm/(NUMITER-1)));

  close(sd);
  fclose(f1);
  return 0;
}


