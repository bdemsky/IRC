#ifndef JNI_PRIVATE_H
#define JNI_PRIVATE_H
#include "jni.h"

struct _jmethodID {
  char *methodname;
};

struct _jfieldID {
  char *fieldname;
};

struct _jobject {
  void * ref;
};

struct c_class {
  int type;
  char * packagename;
  char * classname;
  int numMethods;
  struct _jmethodID * methods;
  int numFields;
  struct _jfieldID *fields;
};

#define MAXJNIREFS 2048
struct jnireferences {
  struct jnireferences * next;
  int index;
  struct _jobject array[MAXJNIREFS];
};

#ifndef MAC
struct _jobject * getwrapped(void * objptr);
void jnipushframe();
void jnipopframe();
extern __thread struct jnireferences * jnirefs;
#define JNIUNWRAP(x) ((x==NULL)?NULL:x->ref)
#define JNIWRAP(x) getwrapper(x);
#define JNIPUSHFRAME() jnipushframe();
#define JNIPOPFRAME() jnipopframe();
#endif

jint RC_GetVersion(JNIEnv * env);
jclass RC_DefineClass(JNIEnv * env, const char * c, jobject loader, const jbyte * buf, jsize bufLen);
jclass RC_FindClass(JNIEnv * env, const char *classname);
jmethodID RC_FromReflectedMethod(JNIEnv * env, jobject mthdobj);
jfieldID RC_FromReflectedField(JNIEnv * env, jobject fldobj);
jobject RC_ToReflectedMethod(JNIEnv * env, jclass classobj, jmethodID methodobj, jboolean flag);
jclass RC_GetSuperclass(JNIEnv * env, jclass classobj);
jboolean RC_IsAssignableFrom(JNIEnv *, jclass, jclass);
jobject RC_ToReflectedField(JNIEnv *, jclass, jfieldID, jboolean);
jint RC_Throw(JNIEnv * env, jthrowable exc);
jint RC_ThrowNew(JNIEnv * env, jclass cls, const char * str);
jthrowable RC_ExceptionOccurred(JNIEnv * env);
void RC_ExceptionDescribe(JNIEnv * env);
void RC_ExceptionClear(JNIEnv * env);
void RC_FatalError(JNIEnv * env, const char * str);
jint RC_PushLocalFrame(JNIEnv * env, jint i);
jobject RC_PopLocalFrame(JNIEnv * env, jobject obj);
jobject RC_NewGlobalRef(JNIEnv * env, jobject obj);
void RC_DeleteGlobalRef(JNIEnv * env, jobject obj);
void RC_DeleteLocalRef(JNIEnv * env, jobject obj);
jboolean RC_IsSameObject(JNIEnv * env, jobject obj1, jobject obj2);
jobject RC_NewLocalRef(JNIEnv * env, jobject obj);
jint RC_EnsureLocalCapacity(JNIEnv * env, jint capacity);
jobject RC_AllocObject(JNIEnv * env, jclass cls);
jobject RC_NewObject(JNIEnv * env, jclass cls, jmethodID methodobj, ...);
jobject RC_NewObjectV(JNIEnv * env, jclass cls, jmethodID methodobj, va_list valist);
jobject RC_NewObjectA(JNIEnv * env, jclass cls, jmethodID methodobj, const jvalue * args);
jobject RC_GetObjectArrayElement(JNIEnv *, jobjectArray, jsize);
void RC_SetObjectArrayElement(JNIEnv *, jobjectArray, jsize, jobject);

jclass RC_GetObjectClass(JNIEnv * env, jobject obj);
jboolean RC_IsInstanceOf(JNIEnv * env, jobject obj, jclass cls);
jmethodID RC_GetMethodID(JNIEnv * env, jclass cls, const char * str1, const char * str2);

#define DCALLMETHOD(R, T) R RC_Call ## T ## Method(JNIEnv *env, jobject obj, jmethodID mid, ...);

#define DCALLMETHODV(R, T) R RC_Call ## T ## MethodV(JNIEnv * env, jobject obj, jmethodID mid, va_list va);

#define DCALLMETHODA(R, T) R RC_Call ## T ## MethodA(JNIEnv * env, jobject obj, jmethodID mid, const jvalue * valarray);

#define DCALLNVMETHOD(R, T) R RC_CallNonvirtual ## T ## Method(JNIEnv *env, jobject obj, jclass cls, jmethodID mid, ...);

#define DCALLNVMETHODV(R, T) R RC_CallNonvirtual ## T ## MethodV(JNIEnv * env, jobject obj, jclass cls, jmethodID mid, va_list va);

#define DCALLNVMETHODA(R, T) R RC_CallNonvirtual ## T ## MethodA(JNIEnv * env, jobject obj, jclass cls, jmethodID mid, const jvalue * valarray);

#define DGETFIELD(R, T) R RC_Get ## T ## Field(JNIEnv *env, jobject obj, jfieldID fld);

#define DSETFIELD(R, T) void RC_Set ## T ## Field(JNIEnv *env, jobject obj, jfieldID fld, R src);

#define DCALLSTMETHOD(R, T) R RC_CallStatic ## T ## Method(JNIEnv *env, jobject obj, jmethodID mid, ...);

#define DCALLSTMETHODV(R, T) R RC_CallStatic ## T ## MethodV(JNIEnv * env, jobject obj, jmethodID mid, va_list va);

#define DCALLSTMETHODA(R, T) R RC_CallStatic ## T ## MethodA(JNIEnv * env, jobject obj, jmethodID mid, const jvalue * valarray);

#define DGETSTFIELD(R, T) R RC_GetStatic ## T ## Field(JNIEnv *env, jclass cls, jfieldID fld);

#define DSETSTFIELD(R, T) void RC_SetStatic ## T ## Field(JNIEnv *env, jclass cls, jfieldID fld, R src);

#define DNEWARRAY(R, T) R ## Array RC_New ## T ## Array(JNIEnv *env, jsize size);

#define DGETARRAY(R, T) R * RC_Get ## T ## ArrayElements(JNIEnv *env, R ## Array array, jboolean * b);

#define DRELEASEARRAY(R, T) void RC_Release ## T ## ArrayElements(JNIEnv *env, R ## Array array, R * ptr, jint num);

#define DGETARRAYREGION(R, T) void RC_Get ## T ## ArrayRegion(JNIEnv *env, R ## Array array, jsize size1, jsize size2, R * ptr);

#define DSETARRAYREGION(R, T) void RC_Set ## T ## ArrayRegion(JNIEnv *env, R ## Array array, jsize size1, jsize size2, const R * ptr);

#define DCALLSET(R, T)				\
  DCALLMETHODV(R, T)				\
  DCALLMETHOD(R, T)				\
  DCALLMETHODA(R, T)				\
  DCALLNVMETHODV(R, T)				\
  DCALLNVMETHOD(R, T)				\
  DCALLNVMETHODA(R, T)				\
  DGETFIELD(R, T)				\
  DSETFIELD(R, T)				\
  DCALLSTMETHODV(R, T)				\
  DCALLSTMETHOD(R, T)				\
  DCALLSTMETHODA(R, T)				\
  DGETSTFIELD(R, T)				\
  DSETSTFIELD(R, T)				\
  DNEWARRAY(R, T)				\
  DGETARRAY(R, T)				\
  DRELEASEARRAY(R, T)				\
  DGETARRAYREGION(R, T)				\
  DSETARRAYREGION(R, T)

DCALLMETHODV(jobject, Object)
DCALLMETHOD(jobject, Object)
DCALLMETHODA(jobject, Object)
DCALLNVMETHODV(jobject, Object)
DCALLNVMETHOD(jobject, Object)
DCALLNVMETHODA(jobject, Object)
DGETFIELD(jobject, Object)
DSETFIELD(jobject, Object)
DCALLSTMETHODV(jobject, Object)
DCALLSTMETHOD(jobject, Object)
DCALLSTMETHODA(jobject, Object)
DGETSTFIELD(jobject, Object)
DSETSTFIELD(jobject, Object)
DGETARRAY(jobject, Object)
DRELEASEARRAY(jobject, Object)
DGETARRAYREGION(jobject, Object)
DSETARRAYREGION(jobject, Object)

DCALLSET(jboolean, Boolean);
DCALLSET(jbyte, Byte);
DCALLSET(jchar, Char);
DCALLSET(jshort, Short);
DCALLSET(jint, Int);
DCALLSET(jlong, Long);
DCALLSET(jfloat, Float);
DCALLSET(jdouble, Double);

jobjectArray RC_NewObjectArray(JNIEnv *env, jsize size, jclass cls, jobject obj);
void RC_CallVoidMethodV(JNIEnv * env, jobject obj, jmethodID mid, va_list va);
void RC_CallVoidMethod(JNIEnv *env, jobject obj, jmethodID mid, ...);
void RC_CallVoidMethodA(JNIEnv * env, jobject obj, jmethodID mid, const jvalue * valarray);
void RC_CallStaticVoidMethod(JNIEnv *env, jclass cls, jmethodID mid, ...);
void RC_CallStaticVoidMethodV(JNIEnv * env, jclass cls, jmethodID mid, va_list va);
void RC_CallStaticVoidMethodA(JNIEnv * env, jclass cls, jmethodID mid, const jvalue * valarray);
void RC_CallNonvirtualVoidMethod(JNIEnv *env, jobject obj, jclass cls, jmethodID mid, ...);
void RC_CallNonvirtualVoidMethodV(JNIEnv * env, jobject obj, jclass cls, jmethodID mid, va_list va);
void RC_CallNonvirtualVoidMethodA(JNIEnv * env, jobject obj, jclass cls, jmethodID mid, const jvalue * valarray);
jfieldID RC_GetFieldID(JNIEnv * env, jclass cls, const char * str1, const char * str2);
jmethodID RC_GetStaticMethodID(JNIEnv * env, jclass cls, const char * str1, const char * str2);
jfieldID RC_GetStaticFieldID(JNIEnv * env, jclass cls, const char * str1, const char * str2);
jint RC_RegisterNatives(JNIEnv * env, jclass cls, const JNINativeMethod * mid, jint num);
jint RC_UnregisterNatives(JNIEnv * env, jclass cls);
jint RC_MonitorEnter(JNIEnv * env, jobject obj);
jint RC_MonitorExit(JNIEnv * env, jobject obj);
jint RC_GetJavaVM(JNIEnv * env, JavaVM ** jvm);
jstring  RC_NewString(JNIEnv * env, const jchar * str, jsize size);
jsize RC_GetStringLength(JNIEnv *env, jstring str);
const jchar * RC_GetStringChars(JNIEnv * env, jstring str, jboolean * flag);
void RC_ReleaseStringChars(JNIEnv * env, jstring str, const jchar * str2);
jstring RC_NewStringUTF(JNIEnv * env, const char *str);
jsize RC_GetStringUTFLength(JNIEnv * env, jstring str);
const char * RC_GetStringUTFChars(JNIEnv * env, jstring str, jboolean * flag);
void RC_ReleaseStringUTFChars(JNIEnv * env, jstring str, const char * str2);
jsize RC_GetArrayLength(JNIEnv * env, jarray array);

#endif
