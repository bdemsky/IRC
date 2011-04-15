#include <jni.h>
#include <jni-private.h>

struct JNINativeInterface_ JNI_vtable = {
  NULL, //void *reserved0;
  NULL, //void *reserved1;
  NULL, //void *reserved2;
  NULL, //void *reserved3;
  RC_GetVersion, //jint     (JNICALL *GetVersion)                   (JNIEnv *);
  RC_DefineClass, //jclass   (JNICALL *DefineClass)                  (JNIEnv *, const char *, jobject, const jbyte *, jsize);
  RC_FindClass, //jclass   (JNICALL *FindClass)                    (JNIEnv *, const char *);
  RC_FromReflectedMethod, // jmethodID (JNICALL *FromReflectedMethod)	   (JNIEnv *, jobject);
  RC_FromReflectedField, //jfieldID  (JNICALL *FromReflectedField)	   (JNIEnv *, jobject);
  RC_ToReflectedMethod, //jobject   (JNICALL *ToReflectedMethod)	   (JNIEnv *, jclass, jmethodID, jboolean);
  RC_GetSuperclass, // jclass   (JNICALL *GetSuperclass)                (JNIEnv *, jclass);
  RC_IsAssignableFrom, //jboolean (JNICALL *IsAssignableFrom)             (JNIEnv *, jclass, jclass);
  RC_ToReflectedField,//jobject  (JNICALL *ToReflectedField)		   (JNIEnv *, jclass, jfieldID,jboolean);
  RC_Throw,//jint     (JNICALL *Throw)                        (JNIEnv *, jthrowable);
  RC_ThrowNew,//jint     (JNICALL *ThrowNew)                     (JNIEnv *, jclass, const char *);
  RC_ExceptionOccurred,//jthrowable (JNICALL *ExceptionOccurred)          (JNIEnv *);
  RC_ExceptionDescribe,//void     (JNICALL *ExceptionDescribe)            (JNIEnv *);
  RC_ExceptionClear, //void     (JNICALL *ExceptionClear)               (JNIEnv *);
  RC_FatalError,//void     (JNICALL *FatalError)                   (JNIEnv *, const char *);
  RC_PushLocalFrame,//jint     (JNICALL *PushLocalFrame)		   (JNIEnv *, jint);
  RC_PopLocalFrame,//jobject  (JNICALL *PopLocalFrame)		   (JNIEnv *, jobject);
  RC_NewGlobalRef,//jobject  (JNICALL *NewGlobalRef)                 (JNIEnv *, jobject);
  RC_DeleteGlobalRef,//void     (JNICALL *DeleteGlobalRef)              (JNIEnv *, jobject);
  RC_DeleteLocalRef,//void     (JNICALL *DeleteLocalRef)               (JNIEnv *, jobject);
  RC_IsSameObject,//jboolean (JNICALL *IsSameObject)                 (JNIEnv *, jobject,                                                     jobject);
  RC_NewLocalRef,//jobject  (JNICALL *NewLocalRef)		   (JNIEnv *, jobject);
  RC_EnsureLocalCapacity,//jint     (JNICALL *EnsureLocalCapacity)	   (JNIEnv *, jint);
  RC_AllocObject, //jobject  (JNICALL *AllocObject)                  (JNIEnv *, jclass);
  RC_NewObject,//jobject (JNICALL *NewObject)			   (JNIEnv *, jclass,                                                     jmethodID, ...);
  RC_NewObjectV,//jobject (JNICALL *NewObjectV)			   (JNIEnv *, jclass,                                                     jmethodID, va_list);
  RC_NewObjectA, //jobject (JNICALL *NewObjectA)			   (JNIEnv *, jclass,                                                     jmethodID, const jvalue *);
  RC_GetObjectClass, //  jclass   (JNICALL *GetObjectClass)               (JNIEnv *, jobject);
  RC_IsInstanceOf, //jboolean (JNICALL *IsInstanceOf)                 (JNIEnv *, jobject, jclass);
  RC_GetMethodID, //jmethodID (JNICALL *GetMethodID)                 (JNIEnv *, jclass,                                                     const char *, const char *);
  RC_CallObjectMethod, // jobject (JNICALL *CallObjectMethod)	   (JNIEnv *, jobject, jmethodID, ...);
  RC_CallObjectMethodV, //jobject (JNICALL *CallObjectMethodV)	   (JNIEnv *, jobject, jmethodID,                                            va_list);
  RC_CallObjectMethodA, //jobject (JNICALL *CallObjectMethodA)	   (JNIEnv *, jobject, jmethodID,                                            const jvalue *);
  RC_CallBooleanMethod, //jboolean (JNICALL *CallBooleanMethod)	   (JNIEnv *, jobject, jmethodID,                                            ...);
  RC_CallBooleanMethodV, //jboolean (JNICALL *CallBooleanMethodV)   (JNIEnv *, jobject, jmethodID,                                            va_list);
  RC_CallBooleanMethodA, //jboolean (JNICALL *CallBooleanMethodA)   (JNIEnv *, jobject, jmethodID,                                           const jvalue *);
  RC_CallByteMethod, //jbyte (JNICALL *CallByteMethod)   (JNIEnv *, jobject, jmethodID, ...);
  RC_CallByteMethodV, //jbyte (JNICALL *CallByteMethodV)	   (JNIEnv *, jobject, jmethodID,                                            va_list);
  RC_CallByteMethodA, //jbyte (JNICALL *CallByteMethodA)	   (JNIEnv *, jobject, jmethodID,                                            const jvalue *);
  RC_CallCharMethod, //jchar (JNICALL *CallCharMethod)	   (JNIEnv *, jobject, jmethodID, ...);
  RC_CallCharMethodV, //jchar (JNICALL *CallCharMethodV)	   (JNIEnv *, jobject, jmethodID,                                            va_list);
  RC_CallCharMethodA, //jchar (JNICALL *CallCharMethodA)	   (JNIEnv *, jobject, jmethodID,                                            const jvalue *);
  RC_CallShortMethod, //jshort (JNICALL *CallShortMethod)	   (JNIEnv *, jobject, jmethodID, ...);
  RC_CallShortMethodV, //jshort (JNICALL *CallShortMethodV)	   (JNIEnv *, jobject, jmethodID,                                            va_list);
  RC_CallShortMethodA, // jshort (JNICALL *CallShortMethodA)	   (JNIEnv *, jobject, jmethodID,                                            const jvalue *);
  RC_CallIntMethod, //jint 	(JNICALL *CallIntMethod)	   (JNIEnv *, jobject, jmethodID, ...);
  RC_CallIntMethodV, //jint 	(JNICALL *CallIntMethodV)	   (JNIEnv *, jobject, jmethodID,                                            va_list);
  RC_CallIntMethodA, //jint 	(JNICALL *CallIntMethodA)	   (JNIEnv *, jobject, jmethodID,                                            const jvalue *);
  RC_CallLongMethod, //jlong (JNICALL *CallLongMethod)	   (JNIEnv *, jobject, jmethodID, ...);
  RC_CallLongMethodV, //jlong (JNICALL *CallLongMethodV)	   (JNIEnv *, jobject, jmethodID,                                            va_list);
  RC_CallLongMethodA, //jlong (JNICALL *CallLongMethodA)	   (JNIEnv *, jobject, jmethodID,                                            const jvalue *);
  RC_CallFloatMethod, //jfloat (JNICALL *CallFloatMethod)	   (JNIEnv *, jobject, jmethodID, ...);
  RC_CallFloatMethodV, //jfloat (JNICALL *CallFloatMethodV)	   (JNIEnv *, jobject, jmethodID,                                            va_list);
  RC_CallFloatMethodA,//jfloat (JNICALL *CallFloatMethodA)	   (JNIEnv *, jobject, jmethodID,                                            const jvalue *);
  RC_CallDoubleMethod, //jdouble (JNICALL *CallDoubleMethod)	   (JNIEnv *, jobject, jmethodID, ...);
  RC_CallDoubleMethodV, //jdouble (JNICALL *CallDoubleMethodV)	   (JNIEnv *, jobject, jmethodID,va_list);
  RC_CallDoubleMethodA, //jdouble (JNICALL *CallDoubleMethodA)	   (JNIEnv *, jobject, jmethodID,                                            const jvalue *);
  RC_CallVoidMethod, //void  (JNICALL *CallVoidMethod)	   (JNIEnv *, jobject, jmethodID, ...);
  RC_CallVoidMethodV, //void  (JNICALL *CallVoidMethodV)	   (JNIEnv *, jobject, jmethodID,                                            va_list);
  RC_CallVoidMethodA, //void  (JNICALL *CallVoidMethodA)	   (JNIEnv *, jobject, jmethodID,                                            const jvalue *);
  RC_CallNonvirtualObjectMethod,// jobject   (JNICALL *CallNonvirtualObjectMethod)  (JNIEnv *, jobject, jclass,                                                    jmethodID, ...);
  RC_CallNonvirtualObjectMethodV, //jobject   (JNICALL *CallNonvirtualObjectMethodV) (JNIEnv *, jobject, jclass,		            jmethodID, va_list);
  RC_CallNonvirtualObjectMethodA, //jobject   (JNICALL *CallNonvirtualObjectMethodA) (JNIEnv *, jobject, jclass,					            jmethodID, const jvalue *);
  RC_CallNonvirtualBooleanMethod,//jboolean  (JNICALL *CallNonvirtualBooleanMethod) (JNIEnv *, jobject, jclass,					            jmethodID, ...);
  RC_CallNonvirtualBooleanMethodV, // jboolean  (JNICALL *CallNonvirtualBooleanMethodV) (JNIEnv *, jobject, jclass,					             jmethodID, va_list);
  RC_CallNonvirtualBooleanMethodA, // jboolean  (JNICALL *CallNonvirtualBooleanMethodA) (JNIEnv *, jobject, jclass,					             jmethodID, const jvalue *);
  RC_CallNonvirtualByteMethod,// jbyte     (JNICALL *CallNonvirtualByteMethod)	   (JNIEnv *, jobject, jclass,					            jmethodID, ...);
  RC_CallNonvirtualByteMethodV, // jbyte     (JNICALL *CallNonvirtualByteMethodV)   (JNIEnv *, jobject, jclass,					            jmethodID, va_list);
  RC_CallNonvirtualByteMethodA, //jbyte     (JNICALL *CallNonvirtualByteMethodA)   (JNIEnv *, jobject, jclass,					            jmethodID, const jvalue *);
  RC_CallNonvirtualCharMethod, // jchar     (JNICALL *CallNonvirtualCharMethod)	   (JNIEnv *, jobject, jclass,					            jmethodID, ...);
  RC_CallNonvirtualCharMethodV, // jchar     (JNICALL *CallNonvirtualCharMethodV)   (JNIEnv *, jobject, jclass,					            jmethodID, va_list);
  RC_CallNonvirtualCharMethodA, // jchar     (JNICALL *CallNonvirtualCharMethodA)   (JNIEnv *, jobject, jclass,					            jmethodID, const jvalue *);
  RC_CallNonvirtualShortMethod, // jshort    (JNICALL *CallNonvirtualShortMethod)   (JNIEnv *, jobject, jclass,					            jmethodID, ...);
  RC_CallNonvirtualShortMethodV, //jshort    (JNICALL *CallNonvirtualShortMethodV)  (JNIEnv *, jobject, jclass,					            jmethodID, va_list);
  RC_CallNonvirtualShortMethodA,// jshort    (JNICALL *CallNonvirtualShortMethodA)  (JNIEnv *, jobject, jclass,					            jmethodID, const jvalue *);
  RC_CallNonvirtualIntMethod, //jint 	    (JNICALL *CallNonvirtualIntMethod)	   (JNIEnv *, jobject, jclass,					            jmethodID, ...);
  RC_CallNonvirtualIntMethodV , //jint 	    (JNICALL *CallNonvirtualIntMethodV)	   (JNIEnv *, jobject, jclass,					            jmethodID, va_list);
  RC_CallNonvirtualIntMethodA, //jint 	    (JNICALL *CallNonvirtualIntMethodA)	   (JNIEnv *, jobject, jclass,					            jmethodID, const jvalue *);
  RC_CallNonvirtualLongMethod, //jlong     (JNICALL *CallNonvirtualLongMethod)	   (JNIEnv *, jobject, jclass,					            jmethodID, ...);
  RC_CallNonvirtualLongMethodV, //jlong     (JNICALL *CallNonvirtualLongMethodV)   (JNIEnv *, jobject, jclass,					            jmethodID, va_list);
  RC_CallNonvirtualLongMethodA, //jlong     (JNICALL *CallNonvirtualLongMethodA)   (JNIEnv *, jobject, jclass,					            jmethodID, const jvalue *);
  RC_CallNonvirtualFloatMethod, //jfloat    (JNICALL *CallNonvirtualFloatMethod)   (JNIEnv *, jobject, jclass,					            jmethodID, ...);
  RC_CallNonvirtualFloatMethodV, //jfloat    (JNICALL *CallNonvirtualFloatMethodV)  (JNIEnv *, jobject, jclass,					            jmethodID, va_list);
  RC_CallNonvirtualFloatMethodA, //jfloat    (JNICALL *CallNonvirtualFloatMethodA)  (JNIEnv *, jobject, jclass,					            jmethodID, const jvalue *);
  RC_CallNonvirtualDoubleMethod, //jdouble   (JNICALL *CallNonvirtualDoubleMethod)  (JNIEnv *, jobject, jclass,					            jmethodID, ...);
  RC_CallNonvirtualDoubleMethodV, //jdouble   (JNICALL *CallNonvirtualDoubleMethodV) (JNIEnv *, jobject, jclass,					            jmethodID, va_list);
  RC_CallNonvirtualDoubleMethodA, //jdouble   (JNICALL *CallNonvirtualDoubleMethodA) (JNIEnv *, jobject, jclass,					            jmethodID, const jvalue *);
  RC_CallNonvirtualVoidMethod,//void      (JNICALL *CallNonvirtualVoidMethod)	   (JNIEnv *, jobject, jclass,					            jmethodID, ...);
  RC_CallNonvirtualVoidMethodV, //void      (JNICALL *CallNonvirtualVoidMethodV)   (JNIEnv *, jobject, jclass,					            jmethodID, va_list);
  RC_CallNonvirtualVoidMethodA, //void      (JNICALL *CallNonvirtualVoidMethodA)   (JNIEnv *, jobject, jclass,					            jmethodID, const jvalue *);

  RC_GetFieldID, //jfieldID  (JNICALL *GetFieldID)          (JNIEnv *, jclass, const char *,					    const char *);

  RC_GetObjectField, //jobject  (JNICALL *GetObjectField)       (JNIEnv *, jobject, jfieldID);
  RC_GetBooleanField, //jboolean (JNICALL *GetBooleanField)      (JNIEnv *, jobject, jfieldID);
  RC_GetByteField,//jbyte    (JNICALL *GetByteField)         (JNIEnv *, jobject, jfieldID);
  RC_GetCharField, //jchar    (JNICALL *GetCharField)         (JNIEnv *, jobject, jfieldID);
  RC_GetShortField, //jshort   (JNICALL *GetShortField)        (JNIEnv *, jobject, jfieldID);
  RC_GetIntField,//jint     (JNICALL *GetIntField)          (JNIEnv *, jobject, jfieldID);
  RC_GetLongField, //jlong    (JNICALL *GetLongField)         (JNIEnv *, jobject, jfieldID);
  RC_GetFloatField, //jfloat   (JNICALL *GetFloatField)        (JNIEnv *, jobject, jfieldID);
  RC_GetDoubleField, //jdouble  (JNICALL *GetDoubleField)       (JNIEnv *, jobject, jfieldID);

  RC_SetObjectField, //void	(JNICALL *SetObjectField)	   (JNIEnv *, jobject,					    jfieldID, jobject);
  RC_SetBooleanField, //void	(JNICALL *SetBooleanField)	   (JNIEnv *, jobject,					    jfieldID, jboolean);
  RC_SetByteField,// void	(JNICALL *SetByteField)		   (JNIEnv *, jobject,					    jfieldID, jbyte);
  RC_SetCharField, //void	(JNICALL *SetCharField)		   (JNIEnv *, jobject,					    jfieldID, jchar);
  RC_SetShortField, //void	(JNICALL *SetShortField)	   (JNIEnv *, jobject,					    jfieldID, jshort);
  RC_SetIntField, //void	(JNICALL *SetIntField)		   (JNIEnv *, jobject,					    jfieldID, jint);
  RC_SetLongField,//void	(JNICALL *SetLongField)		   (JNIEnv *, jobject,					    jfieldID, jlong);
  RC_SetFloatField, //void	(JNICALL *SetFloatField)	   (JNIEnv *, jobject,					    jfieldID, jfloat);
  RC_SetDoubleField,//void	(JNICALL *SetDoubleField)	   (JNIEnv *, jobject,					    jfieldID, jdouble);

  RC_GetStaticMethodID, //jmethodID (JNICALL *GetStaticMethodID)   (JNIEnv *, jclass, const char *,					    const char *);

  RC_CallStaticObjectMethod, //jobject  (JNICALL *CallStaticObjectMethod)  (JNIEnv *, jclass, jmethodID,					       ...);
  RC_CallStaticObjectMethodV, //jobject  (JNICALL *CallStaticObjectMethodV) (JNIEnv *, jclass, jmethodID,					       va_list);
  RC_CallStaticObjectMethodA, //jobject  (JNICALL *CallStaticObjectMethodA) (JNIEnv *, jclass, jmethodID,					       const jvalue *);
  RC_CallStaticBooleanMethod,//jboolean (JNICALL *CallStaticBooleanMethod) (JNIEnv *, jclass, jmethodID,					       ...);
  RC_CallStaticBooleanMethodV, //jboolean (JNICALL *CallStaticBooleanMethodV) (JNIEnv *, jclass, jmethodID,					        va_list);
  RC_CallStaticBooleanMethodA, //jboolean (JNICALL *CallStaticBooleanMethodA) (JNIEnv *, jclass, jmethodID,					        const jvalue *);
  RC_CallStaticByteMethod, //jbyte	   (JNICALL *CallStaticByteMethod)    (JNIEnv *, jclass, jmethodID,					       ...);
  RC_CallStaticByteMethodV, //jbyte    (JNICALL *CallStaticByteMethodV)   (JNIEnv *, jclass, jmethodID,					       va_list);
  RC_CallStaticByteMethodA, //jbyte    (JNICALL *CallStaticByteMethodA)   (JNIEnv *, jclass, jmethodID,					       const jvalue *);
  RC_CallStaticCharMethod, //jchar    (JNICALL *CallStaticCharMethod)    (JNIEnv *, jclass, jmethodID,					       ...);
  RC_CallStaticCharMethodV, //jchar    (JNICALL *CallStaticCharMethodV)   (JNIEnv *, jclass, jmethodID,					       va_list);
  RC_CallStaticCharMethodA,//  jchar    (JNICALL *CallStaticCharMethodA)   (JNIEnv *, jclass, jmethodID,					       const jvalue *);
  RC_CallStaticShortMethod, //jshort   (JNICALL *CallStaticShortMethod)   (JNIEnv *, jclass, jmethodID,					       ...);
  RC_CallStaticShortMethodV ,//jshort   (JNICALL *CallStaticShortMethodV)  (JNIEnv *, jclass, jmethodID,					       va_list);
  RC_CallStaticShortMethodA, //jshort   (JNICALL *CallStaticShortMethodA)  (JNIEnv *, jclass, jmethodID,					       const jvalue *);
  RC_CallStaticIntMethod, //jint 	   (JNICALL *CallStaticIntMethod)     (JNIEnv *, jclass, jmethodID,					       ...);
  RC_CallStaticIntMethodV, //jint 	   (JNICALL *CallStaticIntMethodV)    (JNIEnv *, jclass, jmethodID,					       va_list);
  RC_CallStaticIntMethodA, //jint 	   (JNICALL *CallStaticIntMethodA)    (JNIEnv *, jclass, jmethodID,					       const jvalue *);
  RC_CallStaticLongMethod, //jlong    (JNICALL *CallStaticLongMethod)    (JNIEnv *, jclass, jmethodID,					       ...);
  RC_CallStaticLongMethodV,//jlong    (JNICALL *CallStaticLongMethodV)   (JNIEnv *, jclass, jmethodID,					       va_list);
  RC_CallStaticLongMethodA, //jlong    (JNICALL *CallStaticLongMethodA)   (JNIEnv *, jclass, jmethodID,					       const jvalue *);
  RC_CallStaticFloatMethod, //jfloat   (JNICALL *CallStaticFloatMethod)   (JNIEnv *, jclass, jmethodID,					       ...);
  RC_CallStaticFloatMethodV, //jfloat   (JNICALL *CallStaticFloatMethodV)  (JNIEnv *, jclass, jmethodID,					       va_list);
  RC_CallStaticFloatMethodA,//jfloat   (JNICALL *CallStaticFloatMethodA)  (JNIEnv *, jclass, jmethodID,					       const jvalue *);
  RC_CallStaticDoubleMethod, //jdouble  (JNICALL *CallStaticDoubleMethod)  (JNIEnv *, jclass, jmethodID,					       ...);
  RC_CallStaticDoubleMethodV, //jdouble  (JNICALL *CallStaticDoubleMethodV) (JNIEnv *, jclass, jmethodID,					       va_list);
  RC_CallStaticDoubleMethodA,// jdouble  (JNICALL *CallStaticDoubleMethodA) (JNIEnv *, jclass, jmethodID,					       const jvalue *);
  RC_CallStaticVoidMethod, //void     (JNICALL *CallStaticVoidMethod)    (JNIEnv *, jclass, jmethodID,					       ...);
  RC_CallStaticVoidMethodV, //void     (JNICALL *CallStaticVoidMethodV)   (JNIEnv *, jclass, jmethodID,				       va_list);
  RC_CallStaticVoidMethodA, //void     (JNICALL *CallStaticVoidMethodA)   (JNIEnv *, jclass, jmethodID,					       const jvalue *);
  RC_GetStaticFieldID, //  jfieldID (JNICALL *GetStaticFieldID)        (JNIEnv *, jclass, const char *,					       const char *);
  RC_GetStaticObjectField, //  jobject  (JNICALL *GetStaticObjectField)    (JNIEnv *, jclass, jfieldID);
  RC_GetStaticBooleanField,// jboolean (JNICALL *GetStaticBooleanField)   (JNIEnv *, jclass, jfieldID);
  RC_GetStaticByteField,//jbyte	   (JNICALL *GetStaticByteField)      (JNIEnv *, jclass, jfieldID);
  RC_GetStaticCharField,//jchar	   (JNICALL *GetStaticCharField)      (JNIEnv *, jclass, jfieldID);
  RC_GetStaticShortField,//jshort   (JNICALL *GetStaticShortField)     (JNIEnv *, jclass, jfieldID);
  RC_GetStaticIntField,//jint	   (JNICALL *GetStaticIntField)	      (JNIEnv *, jclass, jfieldID);
  RC_GetStaticLongField,//jlong	   (JNICALL *GetStaticLongField)      (JNIEnv *, jclass, jfieldID);
  RC_GetStaticFloatField,//jfloat   (JNICALL *GetStaticFloatField)     (JNIEnv *, jclass, jfieldID);
  RC_GetStaticDoubleField,//jdouble  (JNICALL *GetStaticDoubleField)    (JNIEnv *, jclass, jfieldID);

  RC_SetStaticObjectField,//void 	(JNICALL *SetStaticObjectField)	   (JNIEnv *, jclass,					    jfieldID, jobject);
  RC_SetStaticBooleanField,//void 	(JNICALL *SetStaticBooleanField)   (JNIEnv *, jclass,					    jfieldID, jboolean);
  RC_SetStaticByteField,//void 	(JNICALL *SetStaticByteField)	   (JNIEnv *, jclass,					    jfieldID, jbyte);
  RC_SetStaticCharField,//void 	(JNICALL *SetStaticCharField)	   (JNIEnv *, jclass,					    jfieldID, jchar);
  RC_SetStaticShortField,//void 	(JNICALL *SetStaticShortField)	   (JNIEnv *, jclass,					    jfieldID, jshort);
  RC_SetStaticIntField,//void 	(JNICALL *SetStaticIntField)	   (JNIEnv *, jclass,					    jfieldID, jint);
  RC_SetStaticLongField,//void 	(JNICALL *SetStaticLongField)	   (JNIEnv *, jclass,					    jfieldID, jlong);
  RC_SetStaticFloatField,//void 	(JNICALL *SetStaticFloatField)	   (JNIEnv *, jclass,					    jfieldID, jfloat);
  RC_SetStaticDoubleField,//void 	(JNICALL *SetStaticDoubleField)	   (JNIEnv *, jclass,					    jfieldID, jdouble);

  RC_NewString,//jstring  (JNICALL *NewString)            (JNIEnv *, const jchar *, jsize);
  RC_GetStringLength,//jsize    (JNICALL *GetStringLength)      (JNIEnv *, jstring);
  RC_GetStringChars,//const jchar * (JNICALL *GetStringChars)  (JNIEnv *, jstring, jboolean *);
  RC_ReleaseStringChars,//void     (JNICALL *ReleaseStringChars)   (JNIEnv *, jstring, const jchar *);
  RC_NewStringUTF,//jstring  (JNICALL *NewStringUTF)         (JNIEnv *, const char *);
  RC_GetStringUTFLength,//jsize    (JNICALL *GetStringUTFLength)   (JNIEnv *, jstring);
  RC_GetStringUTFChars,//const char * (JNICALL *GetStringUTFChars) (JNIEnv *, jstring, jboolean *);
  RC_ReleaseStringUTFChars,//void     (JNICALL *ReleaseStringUTFChars) (JNIEnv *, jstring, const char *);
  RC_GetArrayLength,//jsize    (JNICALL *GetArrayLength)       (JNIEnv *, jarray);
  RC_NewObjectArray,//jobjectArray (JNICALL *NewObjectArray)    (JNIEnv *, jsize, jclass, jobject);
  RC_GetObjectArrayElement,//jobject  (JNICALL *GetObjectArrayElement) (JNIEnv *, jobjectArray, jsize);
  RC_SetObjectArrayElement,//void     (JNICALL *SetObjectArrayElement) (JNIEnv *, jobjectArray, jsize,					     jobject);

  RC_NewBooleanArray,//jbooleanArray (JNICALL *NewBooleanArray)	   (JNIEnv *, jsize);
  RC_NewByteArray,//jbyteArray    (JNICALL *NewByteArray)		   (JNIEnv *, jsize);
  RC_NewCharArray,//jcharArray    (JNICALL *NewCharArray)		   (JNIEnv *, jsize);
  RC_NewShortArray,//jshortArray   (JNICALL *NewShortArray)	   (JNIEnv *, jsize);
  RC_NewIntArray,//jintArray     (JNICALL *NewIntArray)		   (JNIEnv *, jsize);
  RC_NewLongArray,//jlongArray    (JNICALL *NewLongArray)		   (JNIEnv *, jsize);
  RC_NewFloatArray,//jfloatArray   (JNICALL *NewFloatArray)	   (JNIEnv *, jsize);
  RC_NewDoubleArray,//jdoubleArray  (JNICALL *NewDoubleArray)	   (JNIEnv *, jsize);

  RC_GetBooleanArrayElements,//jboolean *	(JNICALL *GetBooleanArrayElements) (JNIEnv *, jbooleanArray,					            jboolean *);
  RC_GetByteArrayElements,//jbyte *	(JNICALL *GetByteArrayElements)	   (JNIEnv *, jbyteArray,					            jboolean *);
  RC_GetCharArrayElements,//jchar *	(JNICALL *GetCharArrayElements)	   (JNIEnv *, jcharArray,					            jboolean *);
  RC_GetShortArrayElements,//jshort *	(JNICALL *GetShortArrayElements)   (JNIEnv *, jshortArray,					            jboolean *);
  RC_GetIntArrayElements,//jint *	(JNICALL *GetIntArrayElements)	   (JNIEnv *, jintArray,					            jboolean *);
  RC_GetLongArrayElements,//jlong *	(JNICALL *GetLongArrayElements)	   (JNIEnv *, jlongArray,					            jboolean *);
  RC_GetFloatArrayElements,//jfloat *	(JNICALL *GetFloatArrayElements)   (JNIEnv *, jfloatArray,					            jboolean *);
  RC_GetDoubleArrayElements,//jdouble *	(JNICALL *GetDoubleArrayElements)  (JNIEnv *, jdoubleArray,					            jboolean *);

  RC_ReleaseBooleanArrayElements,//void		(JNICALL *ReleaseBooleanArrayElements) (JNIEnv *, jbooleanArray,						        jboolean *, jint);
  RC_ReleaseByteArrayElements,//void		(JNICALL *ReleaseByteArrayElements)    (JNIEnv *, jbyteArray,					                jbyte *, jint);
  RC_ReleaseCharArrayElements,//void		(JNICALL *ReleaseCharArrayElements)    (JNIEnv *, jcharArray,						        jchar *, jint);
  RC_ReleaseShortArrayElements,//void		(JNICALL *ReleaseShortArrayElements)   (JNIEnv *, jshortArray,						        jshort *, jint);
  RC_ReleaseIntArrayElements,//void		(JNICALL *ReleaseIntArrayElements)     (JNIEnv *, jintArray,						        jint *, jint);
  RC_ReleaseLongArrayElements,//void		(JNICALL *ReleaseLongArrayElements)    (JNIEnv *, jlongArray,						        jlong *, jint);
  RC_ReleaseFloatArrayElements,//void		(JNICALL *ReleaseFloatArrayElements)   (JNIEnv *, jfloatArray,						        jfloat *, jint);
  RC_ReleaseDoubleArrayElements,//void		(JNICALL *ReleaseDoubleArrayElements)  (JNIEnv *, jdoubleArray,						        jdouble *, jint);
  RC_GetBooleanArrayRegion,//void 		(JNICALL *GetBooleanArrayRegion)   (JNIEnv *, jbooleanArray,					            jsize, jsize, jboolean *);
  RC_GetByteArrayRegion,//void 		(JNICALL *GetByteArrayRegion)	   (JNIEnv *, jbyteArray,					            jsize, jsize, jbyte *);
  RC_GetCharArrayRegion,//void 		(JNICALL *GetCharArrayRegion)	   (JNIEnv *, jcharArray,					            jsize, jsize, jchar *);
  RC_GetShortArrayRegion,//void 		(JNICALL *GetShortArrayRegion)	   (JNIEnv *, jshortArray,					            jsize, jsize, jshort *);
  RC_GetIntArrayRegion,//void 		(JNICALL *GetIntArrayRegion)	   (JNIEnv *, jintArray,					            jsize, jsize, jint *);
  RC_GetLongArrayRegion,//void 		(JNICALL *GetLongArrayRegion)	   (JNIEnv *, jlongArray,					            jsize, jsize, jlong *);
  RC_GetFloatArrayRegion,//void 		(JNICALL *GetFloatArrayRegion)	   (JNIEnv *, jfloatArray,					            jsize, jsize, jfloat *);
  RC_GetDoubleArrayRegion,//void 		(JNICALL *GetDoubleArrayRegion)	   (JNIEnv *, jdoubleArray,					            jsize, jsize, jdouble *);

  RC_SetBooleanArrayRegion,//void 		(JNICALL *SetBooleanArrayRegion)   (JNIEnv *, jbooleanArray,					            jsize, jsize,                                                    const jboolean *);
  RC_SetByteArrayRegion,//void 		(JNICALL *SetByteArrayRegion)	   (JNIEnv *, jbyteArray,					            jsize, jsize,                                                    const jbyte *);
  RC_SetCharArrayRegion,//void 		(JNICALL *SetCharArrayRegion)	   (JNIEnv *, jcharArray,					            jsize, jsize,                                                    const jchar *);
  RC_SetShortArrayRegion,//void 		(JNICALL *SetShortArrayRegion)	   (JNIEnv *, jshortArray,					            jsize, jsize,                                                    const jshort *);
  RC_SetIntArrayRegion,//void 		(JNICALL *SetIntArrayRegion)	   (JNIEnv *, jintArray,					            jsize, jsize,                                                    const jint *);
  RC_SetLongArrayRegion,//void 		(JNICALL *SetLongArrayRegion)	   (JNIEnv *, jlongArray,					            jsize, jsize,                                                    const jlong *);
  RC_SetFloatArrayRegion,//void 		(JNICALL *SetFloatArrayRegion)	   (JNIEnv *, jfloatArray,					            jsize, jsize,                                                    const jfloat *);
  RC_SetDoubleArrayRegion,//void 		(JNICALL *SetDoubleArrayRegion)	   (JNIEnv *, jdoubleArray,					            jsize, jsize,                                                    const jdouble *);

  RC_RegisterNatives,//jint     (JNICALL *RegisterNatives)              (JNIEnv *, jclass,					            const JNINativeMethod *, 						    jint);
  RC_UnregisterNatives,//jint     (JNICALL *UnregisterNatives)            (JNIEnv *, jclass);
  RC_MonitorEnter,//jint     (JNICALL *MonitorEnter)                 (JNIEnv *, jobject);
  RC_MonitorExit,//jint     (JNICALL *MonitorExit)                  (JNIEnv *, jobject);
  RC_GetJavaVM,//jint     (JNICALL *GetJavaVM)                    (JNIEnv *, JavaVM **);

  /* ---- JNI 1.2 functions ---- */

  NULL, //void	   (JNICALL *GetStringRegion)	           (JNIEnv *, jstring, jsize,					            jsize, jchar *);
  NULL, //void     (JNICALL *GetStringUTFRegion)	   (JNIEnv *, jstring, jsize,					            jsize, char *);
  NULL, //void * (JNICALL *GetPrimitiveArrayCritical)      (JNIEnv *, jarray,                                                     jboolean *);
  NULL, //void   (JNICALL *ReleasePrimitiveArrayCritical)  (JNIEnv *, jarray, void *,                                                     jint);
  NULL, // const jchar * (JNICALL *GetStringCritical)       (JNIEnv *, jstring,                                                     jboolean *);
  NULL, //void          (JNICALL *ReleaseStringCritical)   (JNIEnv *, jstring,                                                   const jchar *);
  NULL, //  jweak  (JNICALL *NewWeakGlobalRef)               (JNIEnv *, jobject);
  NULL, //void   (JNICALL *DeleteWeakGlobalRef)            (JNIEnv *, jweak);
  NULL, //  jboolean	(JNICALL *ExceptionCheck)	   (JNIEnv *);

  /* ---- JNI 1.4 functions ---- */

  NULL, //jobject (JNICALL *NewDirectByteBuffer)           (JNIEnv *, void *, jlong);
  NULL, //void *  (JNICALL *GetDirectBufferAddress)        (JNIEnv *, jobject);
  NULL, //jlong   (JNICALL *GetDirectBufferCapacity)       (JNIEnv *, jobject);

  /* ---- JNI 1.6 functions ---- */

  NULL, //jobjectRefType (JNICALL *GetObjectRefType)       (JNIEnv *, jobject);
};
