#ifndef _DSMDEBUG_H_
#define _DSMDEBUG_H_

#define TABORT1(s) {printf("%s\n", s); fflush(stdout);}
#define TABORT2(s, msg) {printf("%s(): %s\n", s, msg); fflush(stdout);}
#define TABORT3(func, s, msg, d) {printf("%s(): %s: for %s = %d\n", func, s, msg, d); fflush(stdout);}
#define TABORT4(s, d) {printf("%s = %d\n", s, d); fflush(stdout);}
#define TABORT5(func, msg1, msg2, val1, val2) {printf("%s(): %s = %x, %s = %d\n", func, msg1, val1, msg2, val2); fflush(stdout);}
#define TABORT6(a, b, c, val1, val2) {printf("%s = %x, %s for %s = %x\n", a, val1, b, c, val2); fflush(stdout);}
#define TABORT7(func, a, b, c, val1, val2) {printf("%s(): %s for %s =%d, %s = %x\n", func, a, b, val1, c, val2); fflush(stdout);}
#define TABORT8(func, s, d) {printf("%s(): %s = %d\n", func, s, d); fflush(stdout);}
#define TABORT9(func, a, b, c, d, val1, val2, val3) {printf("%s(): %s for %s =%x, %s = %d, %s = %x\n", func, a, b, val1, c, val2, d, val3); fflush(stdout);}


#endif
