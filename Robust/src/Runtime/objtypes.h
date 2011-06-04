#ifndef OBJTYPES_H
#define OBJTYPES_H

#ifdef JNI
typedef struct ___java___________lang___________Object___ * ObjectPtr;
typedef struct ___java___________lang___________Thread___ * ThreadPtr;
typedef struct ___java___________lang___________String___ * StringPtr;
#else
typedef struct ___Object___ * ObjectPtr;
typedef struct ___String___ * StringPtr;
typedef struct ___Thread___ * ThreadPtr;
#endif
#endif
