#include<jni.h>
#include<jni-private.h>
#include<stdlib.h>
#include<stdio.h>

#ifndef MAC
__thread struct jnireferences * jnirefs;

struct __jobject * getwrapper(void * objptr) {
  if ((jnirefs->index)>=MAXJNIREFS)
    printf("OVERFLOW IN JOBJECT\n");
  struct __jobject *ptr=&jnirefs->array[jnirefs->index++];
  ptr->ref=objptr;
  return ptr;
}

void jnipushframe() {
  struct jnireferences *ptr=calloc(1, sizeof(struct jnireferences));
  ptr->next=jnirefs;
  jnirefs=ptr;
}

void jnipopframe() {
  struct jnireferences *ptr=jnirefs;
  jnirefs=ptr->next;
  free(ptr);
}
#endif

jint RC_GetVersion(JNIEnv * env) {
  return JNI_VERSION_1_1;
}

jclass RC_DefineClass(JNIEnv * env, const char * c,
		      jobject loader, const jbyte * buf,
		      jsize bufLen) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

//should return jclass object corresponding to classname
jclass RC_FindClass(JNIEnv * env, const char *classname) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

jmethodID RC_FromReflectedMethod(JNIEnv * env, jobject mthdobj) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

jfieldID RC_FromReflectedField(JNIEnv * env, jobject fldobj) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

jobject RC_ToReflectedMethod(JNIEnv * env, jclass classobj, jmethodID methodobj, jboolean flag) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return 0;
}

jclass RC_GetSuperclass(JNIEnv * env, jclass classobj) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return 0;
}

jboolean RC_IsAssignableFrom(JNIEnv * env, jclass obj1, jclass obj2) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return 0;
}

jobject RC_ToReflectedField(JNIEnv * env, jclass obj1, jfieldID fld, jboolean flag) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

jint RC_Throw(JNIEnv * env, jthrowable obj) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return 0;
}

jint RC_ThrowNew(JNIEnv * env, jclass cls, const char * str) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
}

jthrowable RC_ExceptionOccurred(JNIEnv * env) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

void RC_ExceptionDescribe(JNIEnv * env) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
}

void RC_ExceptionClear(JNIEnv * env) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
}

void RC_FatalError(JNIEnv * env, const char * str) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
}

jint RC_PushLocalFrame(JNIEnv * env, jint n) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return 0;
}

jobject RC_PopLocalFrame(JNIEnv * env, jobject obj) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

jobject RC_NewGlobalRef(JNIEnv * env, jobject obj) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

void RC_DeleteGlobalRef(JNIEnv * env, jobject obj) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
}

void RC_DeleteLocalRef(JNIEnv * env, jobject obj) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
}

jboolean RC_IsSameObject(JNIEnv * env, jobject obj1, jobject jobj2) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return 0;
}

jobject RC_NewLocalRef(JNIEnv * env, jobject obj) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

jint RC_EnsureLocalCapacity(JNIEnv * env, jint capacity) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return 0;
}

jobject RC_AllocObject(JNIEnv * env, jclass cls) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return 0;
}

jobject RC_NewObject(JNIEnv * env, jclass cls, jmethodID mid, ...) {
  va_list va;
  va_start(va, mid);
  return RC_NewObjectV(env, cls, mid, va);
}

jobject RC_NewObjectV(JNIEnv * env, jclass cls, jmethodID mid, va_list va) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

jobject RC_NewObjectA(JNIEnv * env, jclass cls, jmethodID mid, const jvalue * vals) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

jobject RC_GetObjectArrayElement(JNIEnv * env, jobjectArray array, jsize size) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

void RC_SetObjectArrayElement(JNIEnv * env, jobjectArray array, jsize size, jobject obj) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
}

jclass RC_GetObjectClass(JNIEnv * env, jobject obj) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

jboolean RC_IsInstanceOf(JNIEnv * env, jobject obj, jclass cls) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return 0;
}

jmethodID RC_GetMethodID(JNIEnv * env, jclass cls, const char * str1, const char * str2) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

#define CALLMETHOD(R, T) R RC_Call ## T ## Method(JNIEnv *env, jobject obj, jmethodID mid, ...) { \
    va_list va;								\
    va_start(va, mid);							\
    return RC_Call ## T ## MethodV(env, obj, mid, va);			\
  }

#define CALLMETHODV(R, T) R RC_Call ## T ## MethodV(JNIEnv * env, jobject obj, jmethodID mid, va_list va) { \
    printf("MISSING FEATURE IN %d\n",__LINE__);				\
    return (R)0;							\
  }									

#define CALLMETHODA(R, T) R RC_Call ## T ## MethodA(JNIEnv * env, jobject obj, jmethodID mid, const jvalue * valarray) { \
    printf("MISSING FEATURE IN %d\n",__LINE__);				\
    return (R)0;							\
  }									

#define CALLNVMETHOD(R, T) R RC_CallNonvirtual ## T ## Method(JNIEnv *env, jobject obj, jclass cls, jmethodID mid, ...) { \
    va_list va;								\
    va_start(va, mid);							\
    return RC_CallNonvirtual ## T ## MethodV(env, obj, cls, mid, va);	\
  }

#define CALLNVMETHODV(R, T) R RC_CallNonvirtual ## T ## MethodV(JNIEnv * env, jobject obj, jclass cls, jmethodID mid, va_list va) { \
    printf("MISSING FEATURE IN %d\n",__LINE__);				\
    return (R)0;							\
  }									

#define CALLNVMETHODA(R, T) R RC_CallNonvirtual ## T ## MethodA(JNIEnv * env, jobject obj, jclass cls, jmethodID mid, const jvalue * valarray) { \
    printf("MISSING FEATURE IN %d\n",__LINE__);				\
    return (R)0;							\
  }									

#define GETFIELD(R, T) R RC_Get ## T ## Field(JNIEnv *env, jobject obj, jfieldID fld) { \
    printf("MISSING FEATURE IN %d\n",__LINE__);				\
    return (R)0;							\
  }

#define SETFIELD(R, T) void RC_Set ## T ## Field(JNIEnv *env, jobject obj, jfieldID fld, R src) { \
    printf("MISSING FEATURE IN %d\n",__LINE__);				\
  }

#define CALLSTMETHOD(R, T) R RC_CallStatic ## T ## Method(JNIEnv *env, jobject obj, jmethodID mid, ...) { \
    va_list va;								\
    va_start(va, mid);							\
    return RC_CallStatic ## T ## MethodV(env, obj, mid, va);		\
  }

#define CALLSTMETHODV(R, T) R RC_CallStatic ## T ## MethodV(JNIEnv * env, jobject obj, jmethodID mid, va_list va) { \
    printf("MISSING FEATURE IN %d\n",__LINE__);				\
    return (R)0;							\
  }									

#define CALLSTMETHODA(R, T) R RC_CallStatic ## T ## MethodA(JNIEnv * env, jobject obj, jmethodID mid, const jvalue * valarray) { \
    printf("MISSING FEATURE IN %d\n",__LINE__);				\
    return (R)0;							\
  }									

#define GETSTFIELD(R, T) R RC_GetStatic ## T ## Field(JNIEnv *env, jclass cls, jfieldID fld) { \
    printf("MISSING FEATURE IN %d\n",__LINE__);				\
    return (R)0;							\
  }

#define SETSTFIELD(R, T) void RC_SetStatic ## T ## Field(JNIEnv *env, jclass cls, jfieldID fld, R src) { \
    printf("MISSING FEATURE IN %d\n",__LINE__);				\
  }

#define NEWARRAY(R, T) R ## Array RC_New ## T ## Array(JNIEnv *env, jsize size) { \
    printf("MISSING FEATURE IN %d\n",__LINE__);				\
    return NULL;							\
  }

#define GETARRAY(R, T) R * RC_Get ## T ## ArrayElements(JNIEnv *env, R ## Array array, jboolean * b) { \
    printf("MISSING FEATURE IN %d\n",__LINE__);				\
    return (R *)0;							\
  }

#define RELEASEARRAY(R, T) void RC_Release ## T ## ArrayElements(JNIEnv *env, R ## Array array, R * ptr, jint num) { \
    printf("MISSING FEATURE IN %d\n",__LINE__);				\
  }

#define GETARRAYREGION(R, T) void RC_Get ## T ## ArrayRegion(JNIEnv *env, R ## Array array, jsize size1, jsize size2, R * ptr) { \
    printf("MISSING FEATURE IN %d\n",__LINE__);				\
  }

#define SETARRAYREGION(R, T) void RC_Set ## T ## ArrayRegion(JNIEnv *env, R ## Array array, jsize size1, jsize size2, const R * ptr) { \
    printf("MISSING FEATURE IN %d\n",__LINE__);				\
  }

#define CALLSET(R, T)				\
  CALLMETHODV(R, T)				\
  CALLMETHOD(R, T)				\
  CALLMETHODA(R, T)				\
  CALLNVMETHODV(R, T)				\
  CALLNVMETHOD(R, T)				\
  CALLNVMETHODA(R, T)				\
  GETFIELD(R, T)				\
  SETFIELD(R, T)				\
  CALLSTMETHODV(R, T)				\
  CALLSTMETHOD(R, T)				\
  CALLSTMETHODA(R, T)				\
  GETSTFIELD(R, T)				\
  SETSTFIELD(R, T)				\
  NEWARRAY(R, T)				\
  GETARRAY(R, T)				\
  RELEASEARRAY(R, T)				\
  GETARRAYREGION(R, T)				\
  SETARRAYREGION(R, T)

jobjectArray RC_NewObjectArray(JNIEnv *env, jsize size, jclass cls, jobject obj) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

CALLMETHODV(jobject, Object)
CALLMETHOD(jobject, Object)
CALLMETHODA(jobject, Object)
CALLNVMETHODV(jobject, Object)
CALLNVMETHOD(jobject, Object)
CALLNVMETHODA(jobject, Object)
GETFIELD(jobject, Object)
SETFIELD(jobject, Object)
CALLSTMETHODV(jobject, Object)
CALLSTMETHOD(jobject, Object)
CALLSTMETHODA(jobject, Object)
GETSTFIELD(jobject, Object)
SETSTFIELD(jobject, Object)
GETARRAY(jobject, Object)
RELEASEARRAY(jobject, Object)
GETARRAYREGION(jobject, Object)
SETARRAYREGION(jobject, Object)

CALLSET(jboolean, Boolean);
CALLSET(jbyte, Byte);
CALLSET(jchar, Char);
CALLSET(jshort, Short);
CALLSET(jint, Int);
CALLSET(jlong, Long);
CALLSET(jfloat, Float);
CALLSET(jdouble, Double);

void RC_CallVoidMethodV(JNIEnv * env, jobject obj, jmethodID mid, va_list va) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
}

void RC_CallVoidMethod(JNIEnv *env, jobject obj, jmethodID mid, ...) {
  va_list va;							       
  va_start(va, mid);							
  RC_CallVoidMethodV(env, obj, mid, va);			
}

void RC_CallVoidMethodA(JNIEnv * env, jobject obj, jmethodID mid, const jvalue * valarray) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
}									

void RC_CallNonvirtualVoidMethod(JNIEnv *env, jobject obj, jclass cls, jmethodID mid, ...) {
  va_list va;							       
  va_start(va, mid);							
  RC_CallNonvirtualVoidMethodV(env, obj, cls, mid, va);			
}

void RC_CallNonvirtualVoidMethodV(JNIEnv * env, jobject obj, jclass cls, jmethodID mid, va_list va) {
  printf("MISSING FEATURE IN %d\n",__LINE__);				
}									

void RC_CallNonvirtualVoidMethodA(JNIEnv * env, jobject obj, jclass cls, jmethodID mid, const jvalue * valarray) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
}									

void RC_CallStaticVoidMethodV(JNIEnv * env, jclass cls, jmethodID mid, va_list va) {
  printf("MISSING FEATURE IN %d\n",__LINE__);				
}									

void RC_CallStaticVoidMethod(JNIEnv *env, jclass cls, jmethodID mid, ...) {
  va_list va;							       
  va_start(va, mid);							
  RC_CallStaticVoidMethodV(env, cls, mid, va);			
}

void RC_CallStaticVoidMethodA(JNIEnv * env, jclass cls, jmethodID mid, const jvalue * valarray) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
}									

jfieldID RC_GetFieldID(JNIEnv * env, jclass cls, const char * str1, const char * str2) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

jmethodID RC_GetStaticMethodID(JNIEnv * env, jclass cls, const char * str1, const char * str2) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

jfieldID RC_GetStaticFieldID(JNIEnv * env, jclass cls, const char * str1, const char * str2) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

jint RC_RegisterNatives(JNIEnv * env, jclass cls, const JNINativeMethod * mid, jint num) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return 0;
}

jint RC_UnregisterNatives(JNIEnv * env, jclass cls) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return 0;
}

jint RC_MonitorEnter(JNIEnv * env, jobject obj) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return 0;
}

jint RC_MonitorExit(JNIEnv * env, jobject obj) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return 0;
}

jint RC_GetJavaVM(JNIEnv * env, JavaVM ** jvm) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return 0;
}

jstring  RC_NewString(JNIEnv * env, const jchar * str, jsize size) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

jsize RC_GetStringLength(JNIEnv *env, jstring str) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return 0;
}

const jchar * RC_GetStringChars(JNIEnv * env, jstring str, jboolean * flag) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

void RC_ReleaseStringChars(JNIEnv * env, jstring str, const jchar * str2) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
}

jstring RC_NewStringUTF(JNIEnv * env, const char *str) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

jsize RC_GetStringUTFLength(JNIEnv * env, jstring str) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return 0;
}

const char * RC_GetStringUTFChars(JNIEnv * env, jstring str, jboolean * flag) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return NULL;
}

void RC_ReleaseStringUTFChars(JNIEnv * env, jstring str, const char * str2) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
}

jsize RC_GetArrayLength(JNIEnv * env, jarray array) {
  printf("MISSING FEATURE IN %d\n",__LINE__);
  return 0;
}
