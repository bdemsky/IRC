#include <jni.h>
#include <stdio.h>
#include "HelloWorld.h"

JNIEXPORT void JNICALL Java_HelloWorld_displayMessage(JNIEnv *env, jobject obj){
	printf("HelloWorld!\n");
}


