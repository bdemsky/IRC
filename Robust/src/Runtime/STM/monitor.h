#ifndef MONITOR_H
#define MONITOR_H

#define MAXEVENTS (1024*1024*128)


#define EV_THREAD 0
#define EV_BARRIER 1
#define EV_READ 2
#define EV_WRITE 3
#define EV_START 4
#define EV_COMMIT 5
#define EV_ABORT 6

struct eventmonitor {
  int index;
  struct eventmonitor * next;
  unsigned int value[MAXEVENTS];
};

extern __thread struct eventmonitor * events;
extern struct eventmonitor * eventlist;
void createmonitor();
void dumpdata();

#if defined(__i386__)
static __inline__ unsigned long long rdtsc(void)
{
  unsigned long long int x;
  __asm__ volatile (".byte 0x0f, 0x31" : "=A" (x));
  return x;
}
#elif defined(__x86_64__)
static __inline__ unsigned long long rdtsc(void)
{
  unsigned hi, lo;
  __asm__ __volatile__ ("rdtsc" : "=a"(lo), "=d"(hi));
  return ( (unsigned long long)lo)|( ((unsigned long long)hi)<<32 );
}
#endif

#define LOGTIME *((long long *)&events->value[events->index])=rdtsc();	\
  events->index+=2;

#define EVLOGEVENT(x) { events->value[events->index++]=x;	\
    LOGTIME							\
      }

#define EVLOGEVENTOBJ(x,o) { events->value[events->index++]=x;	\
  events->value[events->index++]=o;				\
  LOGTIME							\
    }

#endif
