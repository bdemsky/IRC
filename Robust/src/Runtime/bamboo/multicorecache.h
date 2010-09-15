#ifndef BAMBOO_MULTICORE_CACHE_H
#define BAMBOO_MULTICORE_CACHE_H

#ifdef MULTICORE_GC
#ifdef GC_CACHE_ADAPT
#define GC_TILE_TIMER_EVENT_SETTING 100000000 // should be consistent with 
                                              // runtime_arch.h
#define GC_NUM_SAMPLING 24
#define GC_CACHE_ADAPT_HOTPAGE_THRESHOLD 1000
#define GC_CACHE_ADAPT_ACCESS_THRESHOLD  30

// should be consistent with multicoreruntime.h
typedef union
{
  unsigned int word;
  struct
  {
    // policy type
    unsigned int cache_mode   : 2;
	// Reserved.
    unsigned int __reserved_0 : 6;
	// Location Override Target Y
    unsigned int lotar_y      : 4;
    // Reserved.
    unsigned int __reserved_1 : 4;
    // Location Override Target X
    unsigned int lotar_x      : 4;
    // Reserved.
    unsigned int __reserved_2 : 12;
  };
} bamboo_cache_policy_t;

#define BAMBOO_CACHE_MODE_LOCAL 0
#define BAMBOO_CACHE_MODE_HASH 1
#define BAMBOO_CACHE_MODE_NONE 2
#define BAMBOO_CACHE_MODE_COORDS 3

#endif // GC_CACHE_ADAPT
#endif // MULTICORE_GC

#endif
