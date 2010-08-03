#ifndef COREPROF_H
#define COREPROF_H
#ifndef COREPROF
//Core Prof turned off
#define CREATEPROFILER() ;
#define EXITPROFILER() ;
#define DUMPPROFILER() ;
#define CPLOGEVENT(x,y) ;
#else
//Core Prof defined
#ifndef CPMAXEVENTS
#define CPMAXEVENTS (1024*1024*128)
#endif
#define CREATEPROFILER() createprofiler();
#define EXITPROFILER() exitprofiler();
#define DUMPPROFILER() dumpprofiler();

#define CPLOGEVENT(x,y) { CP_events->value[cp_events->index++]=((x<<CP_BASE_SHIFT)|y); \
    CPLOGTIME								\
      }


#define CP_BEGIN 0
#define CP_END 1
#define CP_EVENT 2
#define CP_MASK 3
#define CP_BASE_SHIFT 2

#define CP_MAIN 0
#define CP_RUNMALLOC 1
#define CP_RUNFREE 1

#define CPLOGTIME *((long long *)&cp_events->value[cp_events->index])=rdtsc();	\
  cp_events->index+=2;

struct coreprofmonitor {
  int index;
  struct coreprofmonitor * next;
  unsigned int value[MAXEVENTS];
};

extern __thread int cp_threadnum;
extern __thread struct coreprofmonitor * cp_events;
extern struct coreprofmonitor * cp_eventlist;
void createprofiler();
void exitprofiler();
void dumpprofiler();

static inline void *cp_calloc(int size) {
  CP_LOGEVENT(CP_RUNMALLOC, CP_BEGIN);
  void *mem=calloc(1,size);
  CP_LOGEVENT(CP_RUNMALLOC, CP_END);
  return mem;
}

static inline void cp_free(void *ptr) {
  CP_LOGEVENT(CP_RUNFREE, CP_BEGIN);
  free(ptr);
  CP_LOGEVENT(CP_RUNFREE, CP_END);
}
#endif
#endif
