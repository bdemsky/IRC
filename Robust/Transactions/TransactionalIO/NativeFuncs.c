/* 
 * File:   HelloWorld.cpp
 * Author: navid
 *
 * Created on September 3, 2008, 2:17 PM
 */

        #include <jni.h>
        #include <errno.h>
	#include <fcntl.h>
	#include <unistd.h>
        #include <stdio.h>
	#include <stdlib.h>
	#include <sys/stat.h>
        #include "NativeFunctions.h"
        #define BUF_SIZE 8192

        JNIEXPORT jlong JNICALL Java_TransactionalIO_core_TransactionalFileWrapperFactory_getINodeNative
            (JNIEnv *env, jobject obj, jstring filename) 
        {
	    struct stat status_buf;
	    jlong inodenum;
	   // stat("/home/navid/myfile.txt",&status_buf);
	    char *str = (*env)->GetStringUTFChars(env, filename, 0);
	    if (stat(str,&status_buf)<0)
	 	inodenum = -1;   
	    else
	    {
		    inodenum = status_buf.st_ino;
	    }
	    (*env)->ReleaseStringUTFChars(env, filename, str);
	    return inodenum;
        }

	
        JNIEXPORT jint JNICALL Java_TransactionalIO_core_TransactionalFile_nativepread(JNIEnv *env, jobject obj2, jbyteArray buff, jlong offset, jint size, jobject fobj )	{
	    
	    
	    //signed char str[200];
	    signed char stackBuf[BUF_SIZE];
	    signed char *buf = 0;
	    size_t nativesize = size;
	    off_t nativeoffset =offset;
	    if (nativesize > BUF_SIZE){
	    	buf = malloc(nativesize);
		if (buf == 0) {
		   JNU_ThrowOutOfMemoryError(env, 0);
		   return; 
	        }
	    }
	    else buf = stackBuf;
	   

	    jclass cls2 = (*env) ->GetObjectClass(env, fobj);


	    jfieldID fid3 = (*env)->GetFieldID(env, cls2,"fd", "I");

	    jobject fp = (*env)->GetIntField(env, fobj, fid3); 

	    int res =  pread((int)fp, buf,nativesize ,nativeoffset);

	    (*env) -> SetByteArrayRegion(env, buff, 0, res, buf);

	    return res;
 	}

        JNIEXPORT jint JNICALL Java_TransactionalIO_core_ExtendedTransaction_nativepwrite(JNIEnv *env, jobject obj2, jbyteArray buff, jlong offset, jint size, jobject fobj )	{

	    jbyteArray str;
	    size_t nativesize = (*env)->GetArrayLength(env, buff);
	    off_t nativeoffset =offset;
	   

	    jclass cls2 = (*env) ->GetObjectClass(env, fobj);

	    str = (*env) -> GetByteArrayElements(env, buff, NULL);

	    jfieldID fid3 = (*env)->GetFieldID(env, cls2,"fd", "I");

	    jobject fp = (*env)->GetIntField(env, fobj, fid3); 

	    int res =  pwrite((int)fp, str,nativesize ,nativeoffset);


	    return res;
 	}
