#define _GNU_SOURCE

#include <stdio.h>
#include <pthread.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <errno.h>
#include <sched.h>
#include <stdarg.h>
#include <stdlib.h>


static __inline__ unsigned long long rdtsc(void)
{
  unsigned hi, lo;
  __asm__ __volatile__ ("rdtsc" : "=a"(lo), "=d"(hi));
  return ( (unsigned long long)lo)|( ((unsigned long long)hi)<<32 );
}

static void set_affinity(unsigned long cpu)
{
  int err;
  cpu_set_t cpumask;

  CPU_ZERO(&cpumask);
  CPU_SET(cpu, &cpumask);
  err = sched_setaffinity(syscall(SYS_gettid),
      sizeof(cpu_set_t), &cpumask);

  if (err == -1)
    printf("set_affinity: %s\n", strerror(errno));
}

long long array1[1024];
long long array2[1024];
struct foo {
  int data;
  int padding[63];
};

volatile struct foo channel1;
volatile struct foo channel2;

void * side1(void* data) {
  int i;

  set_affinity(7);
  for(i=0;i<1024;i++) {
    while(1) {
      if (channel1.data==1)
	break;
    }
    channel1.data=0;
    array1[i]=rdtsc();
    channel2.data=1;
  }
}


void side2() {
  int i;
  for(i=0;i<1024;i+=8) {
        channel1.data=1;
    while(1) {
     if (channel2.data==1)
    	break;
    }
    channel2.data=0;
  }
}

int main(int argc, char **argv) {
  pthread_t thread;
  channel1.data=0;
  channel2.data=0;
  set_affinity(0);
  pthread_create(&thread, NULL, side1, NULL);
  side2();
  long long norm=array2[1];
  int i;
  for(i=0;i<1023;i++) {
    long long v1=array1[i]-norm;
    long long v2=array2[i]-norm;
    long long nv2=array2[i+1]-norm;
    printf("%d %lld %lld\n",i, v1, v2);
    printf("%lld %lld\n", v1-v2,nv2-v1);
  }
}
