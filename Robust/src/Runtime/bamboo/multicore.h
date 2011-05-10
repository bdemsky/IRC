#ifndef BAMBOO_MULTICORE_H
#define BAMBOO_MULTICORE_H
#ifdef MULTICORE

#ifndef INLINE
#define INLINE    inline __attribute__((always_inline))
#endif

#ifndef bool
#define bool int
#endif

#ifndef true
#define true 1
#endif

#ifndef false
#define false 0
#endif

#ifndef INTPTR
#ifdef BIT64
#define INTPTR long
#define INTPTRSHIFT 3
#else
#define INTPTR int
#define INTPTRSHIFT 2
#endif
#endif

#endif // MULTICORE
#endif // BAMBOO_MULTICORE_H
