#include "option.h"
#include <stdio.h>
#include <string.h>
#include "runtime.h"

extern char *options;
extern int injectfailures;
extern float failurechance;
extern int debugtask;
extern int injectinstructionfailures;
extern int failurecount;
extern float instfailurechance;
extern int numfailures;
extern int instaccum;
extern char ** environ;

void processOptions() {
  int i;
  options=NULL;
  for(i=0;environ[i]!=0;i++) {
    if (strncmp(environ[i],"BRISTLECONE=",12)==0) {
      options=environ[i]+12;
      break;
    }
  }
  
  while(options!=NULL) {
    if (strncmp(options,"-injectfailures",sizeof("-injectfailures")-1)==0) {
      options=strchr(options,' ');
      if (options!=NULL) options++;
      if (options==NULL)
	break;
      sscanf(options, "%f", &failurechance);
      injectfailures=1;
      printf("Injecting errors with chance=%f\n",failurechance);
      options=strchr(options,' ');
      if (options!=NULL) options++;
    } else if (strncmp(options,"-injectinstructionfailures",sizeof("-injectinstructionfailures")-1)==0) {
      options=strchr(options,' ');
      if (options!=NULL) options++;
      if (options==NULL)
	break;
      sscanf(options, "%d", &failurecount);
      options=strchr(options,' ');
      if (options!=NULL) options++;
      if (options==NULL)
	break;

      sscanf(options, "%f", &instfailurechance);
      options=strchr(options,' ');
      if (options!=NULL) options++;
      if (options==NULL)
	break;

      sscanf(options, "%d", &numfailures);
      options=strchr(options,' ');
      if (options!=NULL) options++;

      instaccum=failurecount;
      instructioncount=failurecount;
      injectinstructionfailures=1;
      printf("Number of failures=%d\n",numfailures);
      printf("Injecting errors with count=%d\n",failurecount);
      printf("Injecting errors with chance=%f\n",instfailurechance);
    } else if (strncmp(options, "-debugtask",sizeof("-debugtask")-1)==0) {
      options=strchr(options,' ');
      if (options!=NULL) options++;
      debugtask=1;
      printf("Debug task option on.\n");
    } else if (strncmp(options, "-initializerandom", sizeof("-initializerandom")-1)==0) {
      options=strchr(options,' ');
      if (options!=NULL) options++;
      printf("Initializing random number generator.\n");
      srandomdev();
    } else
      break;
  }
}
