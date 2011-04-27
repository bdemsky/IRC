/** Server on dc-1 **/

//One clock tick =  (1 / CPU processor speed in Hz) secs
//compile:
//    gcc -Wall -o server clocksyncserver.c
//

#include <stdio.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <math.h>

#define PORT            8500
#define NUMITER     10000
#define DIRSIZE         1

static __inline__ unsigned long long rdtsc(void) {
  unsigned hi, lo;
  __asm__ __volatile__ ("rdtsc" : "=a" (lo), "=d" (hi));
  return ( (unsigned long long)lo)|( ((unsigned long long)hi)<<32 );
}

int main() {
  unsigned long long dir[DIRSIZE];  /* used for incomming dir name, and
                                       outgoing data */
  int sd, sd_current;
  socklen_t addrlen;
  struct   sockaddr_in sin;
  struct   sockaddr_in pin;

  FILE *f1;
  f1=fopen("dc-1", "w");

  unsigned long long array1[NUMITER];
  unsigned long long array2[NUMITER];
  /* get an internet domain socket */
  if ((sd = socket(AF_INET, SOCK_STREAM, 0)) == -1) {
    perror("socket");
    exit(1);
  }

  /* complete the socket structure */
  memset(&sin, 0, sizeof(sin));
  sin.sin_family = AF_INET;
  sin.sin_addr.s_addr = INADDR_ANY;
  sin.sin_port = htons(PORT);

  /* bind the socket to the port number */
  if (bind(sd, (struct sockaddr *) &sin, sizeof(sin)) == -1) {
    perror("bind");
    exit(1);
  }

  /* show that we are willing to listen */
  if (listen(sd, 5) == -1) {
    perror("listen");
    exit(1);
  }
  /* wait for a client to talk to us */
  addrlen = sizeof(pin);
  if ((sd_current = accept(sd, (struct sockaddr *)&pin, &addrlen)) == -1) {
    perror("accept");
    exit(1);
  }
  /* if you want to see the ip address and port of the client, uncomment the
     next two lines */

  /*
     printf("Hi there, from  %s#\n",inet_ntoa(pin.sin_addr));
     printf("Coming from port %d\n",ntohs(pin.sin_port));
   */

  int i;
  char data[1];
  data[0]='1';
  long long norm = 0;
  send(sd_current, data, sizeof(data), MSG_NOSIGNAL);
  for(i=0; i<NUMITER; i++) {
    /* get a message from the client */
    if (recv(sd_current, dir, sizeof(unsigned long long), 0) == -1) {
      perror("recv");
      exit(1);
    }
    //printf("DEBUG: dir[0]= %lld\n", dir[0]);
    array2[i] = rdtsc();
    //printf("DEBUG: array2[i]= %lld\n", array2[i]);
    //array1[i]=array2[i] - dir[0];
    array1[i]= dir[0] - array2[i];
    printf("%lld\n", array1[i]);

    /* acknowledge the message, reply w/ the file names */
    if (send(sd_current, &array2[i], sizeof(unsigned long long), MSG_NOSIGNAL) == -1) {
      perror("send");
      exit(1);
    }
    //array2[i]=rdtsc();
  }

  for(i=0; i<(NUMITER-1); i++) {
    norm += array1[i];
  }

  /* spew-out the results */
  //printf("DEBUG: Average offset= %lld\n", (norm/(NUMITER-1)));
  long long average=(norm/(NUMITER-1));
  printf("average= %lld",(norm/(NUMITER-1)));

  long long stddev, avg1=0;
  for(i=0; i<(NUMITER-1); i++) {
    avg1 += ((array1[i] - average) * (array1[i] - average));
  }
  float ans = (avg1/(NUMITER-1));
  float squareroot= sqrt(ans);
  float squareroot2= sqrt(avg1);

  printf("stddev= %f\n", squareroot);
  printf("error= %f\n", squareroot2/(NUMITER-1));
  fprintf(f1,"%lld\n",(norm/(NUMITER-1)));


  /* give client a chance to properly shutdown */
  sleep(1);

  /* close up both sockets */
  close(sd_current);
  close(sd);


  return 0;
}


