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
#endif
