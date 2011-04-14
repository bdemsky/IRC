#include<jni.h>
#include<jni-private.h>

jint RC_GetVersion(JNIEnv * env) {
  return JNI_VERSION_1_1;
}

jclass RC_DefineClass(JNIEnv * env, const char * c,
		      jobject loader, const jbyte * buf,
		      jsize bufLen) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return NULL;
}

//should return jclass object corresponding to classname
jclass RC_FindClass(JNIEnv * env, const char *classname) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return NULL;
}

jmethodID RC_FromReflectedMethod(JNIEnv * env, jobject mthdobj) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return NULL;
}

jmethodID RC_FromReflectedField(JNIEnv * env, jobject fldobj) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return NULL;
}

jobject RC_ToReflectedMethod(JNIEnv * env, jclass classobj, jmethodID methodobj, jboolean flag) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return 0;
}

jclass RC_GetSuperclass(JNIEnv * env, jclass classobj) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return 0;
}

jboolean RC_IsAssignableFrom(JNIEnv * env, jclass obj1, jclass obj2) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return 0;
}

jobject RC_ToReflectedField(JNIEnv * env, jclass obj1, jfieldID fld, jboolean) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return NULL
}

jint RC_Throw(JNIEnv * env, jthrowable obj) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return 0;
}

jint RC_ThrowNew(JNIEnv *, jclass, const char *) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
}

jthrowable RC_ExceptionOccurred(JNIEnv * env) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return NULL;
}

void RC_ExceptionDescribe(JNIEnv * env) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
}

void RC_ExceptionClear(JNIEnv * env) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
}

void RC_FatalError(JNIEnv * env, const char * str) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
}

jint RC_PushLocalFrame(JNIEnv * env, jint n) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return 0;
}

jobject RC_PopLocalFrame(JNIEnv * env, jobject obj) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return NULL;
}

jobject RC_NewGlobalRef(JNIEnv * env, jobject obj) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return NULL;
}

void RC_DeleteGlobalRef(JNIEnv * env, jobject obj) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
}

void RC_DeleteLocalRef(JNIEnv * env, jobject obj) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
}

jboolean RC_IsSameObject(JNIEnv * env, jobject obj1, jobject jobj2) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return 0;
}

jobject RC_NewLocalRef(JNIEnv * env, jobject obj) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return NULL;
}

jint RC_EnsureLocalCapacity(JNIEnv * env, jint capacity) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return 0;
}

jobject RC_AllocObject(JNIEnv * env, jclass cls) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return 0;
}

jobject RC_NewObject(JNIEnv * env, jclass cls, jmethodID mid, ...) {
  va_list va;
  va_start(va, mid);
  return RC_NewObjectV(env, cls, mid, va);
}

jobject RC_NewObjectV(JNIEnv * env, jclass cls, jmethodID mid, va_list va) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return NULL;
}

jobject RC_NewObjectA(JNIEnv * env, jclass cls, jmethodID mid, const jvalue * vals) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return NULL;
}

jclass RC_GetObjectClass(JNIEnv * env, jobject obj) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return NULL;
}

jboolean RC_IsInstanceOf(JNIEnv * env, jobject obj, jclass cls) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return NULL;
}

jmethodID RC_GetMethodID(JNIEnv * env, jclass cls, const char * str1, const char * str2) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return NULL;
}

#define CALLMETHOD(R, T) R RC_Call ## T ## Method(JNIEnv *env, jobject obj, jmethodID mid, ...) { \
    va_list va;								\
    va_start(va, mid);							\
    return RC_Call ## T ## MethodV(env, cls, mid, va);			\
  }

#define CALLMETHODV(R, T) R RC_Call ## T ## MethodV(JNIEnv * env, jobject obj, jmethodID mid, va_list va) { \
    printf("MISSING FEATURE IN %d\n",___LINE___);			\
    return 0;								\
  }									

#define CALLMETHODA(R, T) R RC_Call ## T ## MethodA(JNIEnv * env, jobject obj, jmethodID mid, const jvalue * valarray) { \
    printf("MISSING FEATURE IN %d\n",___LINE___);			\
    return 0;								\
  }									

#define CALLNVMETHOD(R, T) R RC_CallNonvirtual ## T ## Method(JNIEnv *env, jobject obj, jmethodID mid, ...) { \
    va_list va;								\
    va_start(va, mid);							\
    return RC_CallNonVirtual ## T ## MethodV(env, cls, mid, va);	\
  }

#define CALLNVMETHODV(R, T) R RC_CallNonvirtual ## T ## MethodV(JNIEnv * env, jobject obj, jmethodID mid, va_list va) { \
    printf("MISSING FEATURE IN %d\n",___LINE___);			\
    return 0;								\
  }									

#define CALLNVMETHODA(R, T) R RC_CallNonvirtual ## T ## MethodA(JNIEnv * env, jobject obj, jmethodID mid, const jvalue * valarray) { \
    printf("MISSING FEATURE IN %d\n",___LINE___);			\
    return 0;								\
  }									

#define GETFIELD(R, T) R Get ## T ## Field(JNIEnv *env, jobject obj, jfieldID fld) { \
    printf("MISSING FEATURE IN %d\n",___LINE___);			\
    return 0;								\
  }

#define SETFIELD(R, T) void Set ## T ## Field(JNIEnv *env, jobject obj, jfieldID fld, R src) { \
    printf("MISSING FEATURE IN %d\n",___LINE___);			\
    return 0;								\
  }

#define CALLSTMETHOD(R, T) R RC_CallStatic ## T ## Method(JNIEnv *env, jobject obj, jmethodID mid, ...) { \
    va_list va;								\
    va_start(va, mid);							\
    return RC_CallStatic ## T ## MethodV(env, cls, mid, va);	\
  }

#define CALLSTMETHODV(R, T) R RC_CallStatic ## T ## MethodV(JNIEnv * env, jobject obj, jmethodID mid, va_list va) { \
    printf("MISSING FEATURE IN %d\n",___LINE___);			\
    return 0;								\
  }									

#define CALLSTMETHODA(R, T) R RC_CallStatic ## T ## MethodA(JNIEnv * env, jobject obj, jmethodID mid, const jvalue * valarray) { \
    printf("MISSING FEATURE IN %d\n",___LINE___);			\
    return 0;								\
  }									

#define GETSTFIELD(R, T) R RC_GetStatic ## T ## Field(JNIEnv *env, jclass cls, jfieldID fld) { \
    printf("MISSING FEATURE IN %d\n",___LINE___);			\
    return 0;								\
  }

#define SETSTFIELD(R, T) void RC_SetStatic ## T ## Field(JNIEnv *env, jclass cls, jfieldID fld, R src) { \
    printf("MISSING FEATURE IN %d\n",___LINE___);			\
    return 0;								\
  }

#define NEWARRAY(R, T) R ## Array RC_New ## T ## Array(JNIEnv *env, jsize size) { \
    printf("MISSING FEATURE IN %d\n",___LINE___);			\
    return NULL;							\
  }

#define GETARRAY(R, T) R * RC_Get ## T ## ArrayElements(JNIEnv *env, R ## Array, jboolean * b) { \
    printf("MISSING FEATURE IN %d\n",___LINE___);			\
    return 0;								\
  }

#define RELEASEARRAY(R, T) void RC_Release ## T ## ArrayElements(JNIEnv *env, R ## Array array, T * ptr, jint num) { \
    printf("MISSING FEATURE IN %d\n",___LINE___);			\
  }

#define GETARRAYREGION(R, T) void Get ## T ## ArrayRegion(JNIEnv *env, R ## Array array, jsize size1, jsize size2, R * ptr) { \
    printf("MISSING FEATURE IN %d\n",___LINE___);			\
  }

#define SETARRAYREGION(R, T) void Set ## T ## ArrayRegion(JNIEnv *env, R ## Array array, jsize size1, jsize size2, const R * ptr) { \
    printf("MISSING FEATURE IN %d\n",___LINE___);			\
  }

#define CALLSET(R, T)				\
  CALLMETHOD(R, T)				\
  CALLMETHODV(R, T)				\
  CALLMETHODA(R, T)				\
  CALLNVMETHOD(R, T)				\
  CALLNVMETHODV(R, T)				\
  CALLNVMETHODA(R, T)				\
  GETFIELD(R, T)				\
  SETFIELD(R, T)				\
  CALLSTMETHOD(R, T)				\
  CALLSTMETHODV(R, T)				\
  CALLSTMETHODA(R, T)				\
  GETSTFIELD(R, T)				\
  SETSTFIELD(R, T)				\
  NEWARRAY(R, T)				\
  GETARRAY(R, T)				\
  RELEASEARRAY(R, T)				\
  GETARRAYREGION(R, T)				\
  SETARRAYREGION(R, T)

CALLSET(jobject, Object);

CALLSET(jboolean, Boolean);

CALLSET(jbyte, Byte);

CALLSET(jchar, Char);

CALLSET(jshort, Short);

CALLSET(jint, Int);

CALLSET(jlong, Long);

CALLSET(jfloat, Float);

CALLSET(jdouble, Double);

void RC_CallVoidMethod(JNIEnv *env, jobject obj, jmethodID mid, ...) {
  va_list va;							       
  va_start(va, mid);							
  RC_CallVoidMethodV(env, cls, mid, va);			
}

void RC_CallVoidMethodV(JNIEnv * env, jobject obj, jmethodID mid, va_list va) {
  printf("MISSING FEATURE IN %d\n",___LINE___);				
}									

void RC_CallVoidMethodA(JNIEnv * env, jobject obj, jmethodID mid, const jvalue * valarray) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return 0;
}									

void RC_CallNonvirtualVoidMethod(JNIEnv *env, jobject obj, jmethodID mid, ...) {
  va_list va;							       
  va_start(va, mid);							
  RC_CallVoidMethodV(env, cls, mid, va);			
}

void RC_CallNonvirtualVoidMethodV(JNIEnv * env, jobject obj, jmethodID mid, va_list va) {
  printf("MISSING FEATURE IN %d\n",___LINE___);				
}									

void RC_CallNonvirtualVoidMethodA(JNIEnv * env, jobject obj, jmethodID mid, const jvalue * valarray) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
}									

jfieldID RC_GetFieldID(JNIEnv * env, jclass cls, const char * str1, const char * str2) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return NULL;
}

jmethodID RC_GetStaticMethodID(JNIEnv * env, jclass cls, const char * str1, const char * str2) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return NULL;
}

jfieldID RC_GetStaticFieldID(JNIEnv * env, jclass cls, const char * str1, const char * str2) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return NULL;
}

jint RC_RegisterNatives(JNIEnv * env, jclass cls, const JNINativeMethod * mid, jint num) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return 0;
}

jint RC_UnregisterNatives(JNIEnv * env, jclass cls) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return 0;
}

jint RC_MonitorEnter(JNIEnv * env, jobject obj) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return 0;
}

jint RC_MonitorExit(JNIEnv * env, jobject obj) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return 0;
}

jint RC_GetJavaVM(JNIEnv * env, JavaVM ** jvm) {
  printf("MISSING FEATURE IN %d\n",___LINE___);
  return 0;
}
