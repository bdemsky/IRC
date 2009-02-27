#ifndef TRANS_H
#define TRANS_H

extern __thread objstr_t *t_cache;
extern __thread struct ___Object___ *revertlist;
#ifdef ABORTREADERS
extern __thread int t_abort;
extern __thread jmp_buf aborttrans;
#endif

#endif
