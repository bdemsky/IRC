#ifndef BAMBOO_MULTICORE_H
#define BAMBOO_MULTICORE_H
#ifdef MULTICORE

#ifndef INLINE
#define INLINE    inline __attribute__((always_inline))
#endif

#ifndef bool
#define bool int
#define true 1
#define false 0
#endif

#endif // MULTICORE
#endif // BAMBOO_MULTICORE_H
