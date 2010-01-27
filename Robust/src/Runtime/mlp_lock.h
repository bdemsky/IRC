#define LOCK_PREFIX \
  ".section .smp_locks,\"a\"\n"   \
  "  .align 4\n"                  \
  "  .long 661f\n"             /* address */\
  ".previous\n"                   \
  "661:\n\tlock; "


static inline void atomic_dec(volatile int *v) {
  __asm__ __volatile__ (LOCK_PREFIX "decl %0"
                        : "+m" (*v));
}

static inline void atomic_inc(volatile int *v) {
  __asm__ __volatile__ (LOCK_PREFIX "incl %0"
                        : "+m" (*v));
}

static inline int atomic_sub_and_test(int i, volatile int *v) {
  unsigned char c;

  __asm__ __volatile__ (LOCK_PREFIX "subl %2,%0; sete %1"
                        : "+m" (*v), "=qm" (c)
                        : "ir" (i) : "memory");
  return c;
}
