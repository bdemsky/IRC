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
#define EV_ARRAYREAD 7
#define EV_ARRAYWRITE 8

struct eventmonitor {
  int index;
  struct eventmonitor * next;
  unsigned int value[MAXEVENTS];
};

#define MAXEVTHREADS 16
#define EVTHREADSHIFT 4
extern __thread int threadnum;
extern __thread struct eventmonitor * events;
extern struct eventmonitor * eventlist;
void createmonitor();
void dumpdata();

#define LOGTIME *((long long *)&events->value[events->index])=rdtsc();	\
  events->index+=2;

#define EVLOGEVENT(x) { events->value[events->index++]=x;	\
    LOGTIME							\
      }

#define EVLOGEVENTOBJ(x,o) { events->value[events->index++]=x;	\
    events->value[events->index++]=o;				\
  LOGTIME							\
    }

#define EVLOGEVENTARRAY(x,o,i) { events->value[events->index++]=x;	\
    events->value[events->index++]=o;					\
    events->value[events->index++]=i;					\
    LOGTIME								\
      }

#endif
