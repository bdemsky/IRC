#ifndef JNI_PRIVATE_H
#define JNI_PRIVATE_H
struct c_class {
  int type;
  char * packagename;
  char * classname;
  int numMethods;
  jmethodID * methods;
  int numFields;
  jfieldID *fields;
};

struct jmethodID {
  char *methodname;
};

struct jfieldID {
  char *fieldname;
};

jint RC_GetVersion(JNIEnv *);
jclass RC_DefineClass(JNIEnv * env, const char * c, jobject loader, const jbyte * buf, jsize bufLen);
jclass RC_FindClass(JNIEnv * env, const char *classname);
jmethodID RC_FromReflectedMethod(JNIEnv * env, jobject mthdobj);
jmethodID RC_FromReflectedField(JNIEnv * env, jobject fldobj);
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


jclass RC_GetObjectClass(JNIEnv * env, jobject obj);
jboolean RC_IsInstanceOf(JNIEnv * env, jobject obj, jclass cls);
jmethodID RC_GetMethodID(JNIEnv * env, jclass cls, const char * str1, const char * str2);

#define CALLMETHOD(R, T) R RC_Call ## T ## Method(JNIEnv *env, jobject obj, jmethodID mid, ...);

#define CALLMETHODV(R, T) R RC_Call ## T ## MethodV(JNIEnv * env, jobject obj, jmethodID mid, va_list va);

#define CALLMETHODA(R, T) R RC_Call ## T ## MethodA(JNIEnv * env, jobject obj, jmethodID mid, const jvalue * valarray);

#define CALLNVMETHOD(R, T) R RC_CallNonvirtual ## T ## Method(JNIEnv *env, jobject obj, jmethodID mid, ...);

#define CALLNVMETHODV(R, T) R RC_CallNonvirtual ## T ## MethodV(JNIEnv * env, jobject obj, jmethodID mid, va_list va);

#define CALLNVMETHODA(R, T) R RC_CallNonvirtual ## T ## MethodA(JNIEnv * env, jobject obj, jmethodID mid, const jvalue * valarray);

#define GETFIELD(R, T) R Get ## T ## Field(JNIEnv *env, jobject obj, jfieldID fld);

#define SETFIELD(R, T) void Set ## T ## Field(JNIEnv *env, jobject obj, jfieldID fld, R src);

#define CALLSTMETHOD(R, T) R RC_CallStatic ## T ## Method(JNIEnv *env, jobject obj, jmethodID mid, ...);

#define CALLSTMETHODV(R, T) R RC_CallStatic ## T ## MethodV(JNIEnv * env, jobject obj, jmethodID mid, va_list va);

#define CALLSTMETHODA(R, T) R RC_CallStatic ## T ## MethodA(JNIEnv * env, jobject obj, jmethodID mid, const jvalue * valarray);

#define GETSTFIELD(R, T) R RC_GetStatic ## T ## Field(JNIEnv *env, jclass cls, jfieldID fld);

#define SETSTFIELD(R, T) void RC_SetStatic ## T ## Field(JNIEnv *env, jclass cls, jfieldID fld, R src);

#define NEWARRAY(R, T) R ## Array RC_New ## T ## Array(JNIEnv *env, jsize size);

#define GETARRAY(R, T) R * RC_Get ## T ## ArrayElements(JNIEnv *env, R ## Array array, jboolean * b);

#define RELEASEARRAY(R, T) void RC_Release ## T ## ArrayElements(JNIEnv *env, R ## Array array, R * ptr, jint num);

#define GETARRAYREGION(R, T) void Get ## T ## ArrayRegion(JNIEnv *env, R ## Array array, jsize size1, jsize size2, R * ptr);

#define SETARRAYREGION(R, T) void Set ## T ## ArrayRegion(JNIEnv *env, R ## Array array, jsize size1, jsize size2, const R * ptr);

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

CALLSET(jobject, Object);
CALLSET(jboolean, Boolean);
CALLSET(jbyte, Byte);
CALLSET(jchar, Char);
CALLSET(jshort, Short);
CALLSET(jint, Int);
CALLSET(jlong, Long);
CALLSET(jfloat, Float);
CALLSET(jdouble, Double);

void RC_CallVoidMethodV(JNIEnv * env, jobject obj, jmethodID mid, va_list va);
void RC_CallVoidMethod(JNIEnv *env, jobject obj, jmethodID mid, ...);
void RC_CallVoidMethodA(JNIEnv * env, jobject obj, jmethodID mid, const jvalue * valarray);
void RC_CallNonvirtualVoidMethod(JNIEnv *env, jobject obj, jmethodID mid, ...);
void RC_CallNonvirtualVoidMethodV(JNIEnv * env, jobject obj, jmethodID mid, va_list va);
void RC_CallNonvirtualVoidMethodA(JNIEnv * env, jobject obj, jmethodID mid, const jvalue * valarray);
jfieldID RC_GetFieldID(JNIEnv * env, jclass cls, const char * str1, const char * str2);
jmethodID RC_GetStaticMethodID(JNIEnv * env, jclass cls, const char * str1, const char * str2);
jfieldID RC_GetStaticFieldID(JNIEnv * env, jclass cls, const char * str1, const char * str2);
jint RC_RegisterNatives(JNIEnv * env, jclass cls, const JNINativeMethod * mid, jint num);
jint RC_UnregisterNatives(JNIEnv * env, jclass cls);
jint RC_MonitorEnter(JNIEnv * env, jobject obj);
jint RC_MonitorExit(JNIEnv * env, jobject obj);
jint RC_GetJavaVM(JNIEnv * env, JavaVM ** jvm);

#endif
