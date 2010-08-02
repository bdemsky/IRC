#ifndef COREPROF_H
#define COREPROF_H

#ifndef CPMAXEVENTS
#define CPMAXEVENTS (1024*1024*128)
#endif

#define CP_BEGIN 0
#define CP_END 1
#define CP_EVENT 2
#define CP_MASK 3
#define CP_BASE_SHIFT 2

#define CP_MAIN 0

struct coreprofmonitor {
  int index;
  struct coreprofmonitor * next;
  unsigned int value[MAXEVENTS];
};

extern __thread int cp_threadnum;
extern __thread struct coreprofmonitor * cp_events;
extern struct coreprofmonitor * cp_eventlist;
void createprofiler();
void dumpprofiler();

#define CPLOGTIME *((long long *)&cp_events->value[cp_events->index])=rdtsc();	\
  cp_events->index+=2;

#define CPLOGEVENT(x) { CP_events->value[cp_events->index++]=x;	\
    CPLOGTIME							\
      }
#endif
