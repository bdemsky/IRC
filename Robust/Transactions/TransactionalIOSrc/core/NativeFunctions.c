/* 
 * File:   HelloWorld.cpp
 * Author: navid
 *
 * Created on September 3, 2008, 2:17 PM
 */

        #include <jni.h>
        #include <stdio.h>
	#include <sys/stat.h>
        #include "NativeFunctions.h"
        
        JNIEXPORT jlong JNICALL Java_test2_Main_getINodeNative
            (JNIEnv *env, jobject obj, jstring filename) 
        {
	    struct stat status_buf;
	    jlong inodenum;
	   // stat("/home/navid/myfile.txt",&status_buf);
	    char *str = (*env)->GetStringUTFChars(env, filename, 0);
	    printf("\n");
	    printf("File Name is: %s \n", str);
	    if (stat(str,&status_buf)<0)
	 	inodenum = -1;   
	    else
	    {
		    printf("Inode number is: %lu \n", status_buf.st_ino);
		    inodenum = status_buf.st_ino;
	    }
	    return inodenum;
        }
