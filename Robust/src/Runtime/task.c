#ifdef TASK
#include "runtime.h"
#include "structdefs.h"
#include "mem.h"
#include "checkpoint.h"
#include "Queue.h"
#include "SimpleHash.h"
#include "GenericHashtable.h"
#include <sys/select.h>
#include <sys/types.h>
#include <sys/mman.h>
#include <string.h>
#include <signal.h>

extern int injectfailures;
extern float failurechance;
extern int debugtask;
extern int instaccum;

#ifdef CONSCHECK
#include "instrument.h"
#endif

struct genhashtable * activetasks;
struct parameterwrapper * objectqueues[NUMCLASSES];
struct genhashtable * failedtasks;
struct taskparamdescriptor * currtpd;
struct RuntimeHash * forward;
struct RuntimeHash * reverse;

int main(int argc, char **argv) {
#ifdef BOEHM_GC
  GC_init(); // Initialize the garbage collector
#endif
#ifdef CONSCHECK
  initializemmap();
#endif
  processOptions();
  initializeexithandler();
  /* Create table for failed tasks */
  failedtasks=genallocatehashtable((unsigned int (*)(void *)) &hashCodetpd, 
				   (int (*)(void *,void *)) &comparetpd);
  /* Create queue of active tasks */
  activetasks=genallocatehashtable((unsigned int (*)(void *)) &hashCodetpd, 
				   (int (*)(void *,void *)) &comparetpd);
  
  /* Process task information */
  processtasks();

  /* Create startup object */
  createstartupobject(argc, argv);

  /* Start executing the tasks */
  executetasks();
}

void createstartupobject(int argc, char ** argv) {
  int i;
  
  /* Allocate startup object     */
#ifdef PRECISE_GC
  struct ___StartupObject___ *startupobject=(struct ___StartupObject___*) allocate_new(NULL, STARTUPTYPE);
  struct ArrayObject * stringarray=allocate_newarray(NULL, STRINGARRAYTYPE, argc-1); 
#else
  struct ___StartupObject___ *startupobject=(struct ___StartupObject___*) allocate_new(STARTUPTYPE);
  struct ArrayObject * stringarray=allocate_newarray(STRINGARRAYTYPE, argc-1); 
#endif
  /* Build array of strings */
  startupobject->___parameters___=stringarray;
  for(i=1;i<argc;i++) {
    int length=strlen(argv[i]);
#ifdef PRECISE_GC
    struct ___String___ *newstring=NewString(NULL, argv[i],length);
#else
    struct ___String___ *newstring=NewString(argv[i],length);
#endif
    ((void **)(((char *)& stringarray->___length___)+sizeof(int)))[i-1]=newstring;
  }
  
  /* Set initialized flag for startup object */
  flagorand(startupobject,1,0xFFFFFFFF);
}

int hashCodetpd(struct taskparamdescriptor *ftd) {
  int hash=(int)ftd->task;
  int i;						
  for(i=0;i<ftd->numParameters;i++){ 
    hash^=(int)ftd->parameterArray[i];
  }
  return hash;
}

int comparetpd(struct taskparamdescriptor *ftd1, struct taskparamdescriptor *ftd2) {
  int i;
  if (ftd1->task!=ftd2->task)
    return 0;
  for(i=0;i<ftd1->numParameters;i++)
    if(ftd1->parameterArray[i]!=ftd2->parameterArray[i])
      return 0;
  return 1;
}

/* This function sets a tag. */
#ifdef PRECISE_GC
void tagset(void *ptr, struct ___Object___ * obj, struct ___TagDescriptor___ * tagd) {
#else
void tagset(struct ___Object___ * obj, struct ___TagDescriptor___ * tagd) {
#endif
  struct ___Object___ * tagptr=obj->___tags___;
  if (tagptr==NULL) {
    obj->___tags___=(struct ___Object___ *)tagd;
  } else {
    /* Have to check if it is already set */
    if (tagptr->type==TAGTYPE) {
      struct ___TagDescriptor___ * td=(struct ___TagDescriptor___ *) tagptr;
      if (td==tagd)
	return;
#ifdef PRECISE_GC
      int ptrarray[]={2, (int) ptr, (int) obj, (int)tagd};
      struct ArrayObject * ao=allocate_newarray(&ptrarray,TAGARRAYTYPE,TAGARRAYINTERVAL);
      obj=(struct ___Object___ *)ptrarray[2];
      tagd=(struct ___TagDescriptor___ *)ptrarray[3];
      td=(struct ___TagDescriptor___ *) obj->___tags___;
#else
      struct ArrayObject * ao=allocate_newarray(TAGARRAYTYPE,TAGARRAYINTERVAL);
#endif
      ARRAYSET(ao, struct ___TagDescriptor___ *, 0, td);
      ARRAYSET(ao, struct ___TagDescriptor___ *, 1, tagd);
      obj->___tags___=(struct ___Object___ *) ao;
      ao->___cachedCode___=2;
    } else {
      /* Array Case */
      int i;
      struct ArrayObject *ao=(struct ArrayObject *) tagptr;
      for(i=0;i<ao->___cachedCode___;i++) {
	struct ___TagDescriptor___ * td=ARRAYGET(ao, struct ___TagDescriptor___*, i);
	if (td==tagd)
	  return;
      }
      if (ao->___cachedCode___<ao->___length___) {
	ARRAYSET(ao, struct ___TagDescriptor___ *, ao->___cachedCode___, tagd);
	ao->___cachedCode___++;
      } else {
#ifdef PRECISE_GC
	int ptrarray[]={2,(int) ptr, (int) obj, (int) tagd};
	struct ArrayObject * aonew=allocate_newarray(&ptrarray,TAGARRAYTYPE,TAGARRAYINTERVAL+ao->___length___);
	obj=(struct ___Object___ *)ptrarray[2];
	tagd=(struct ___TagDescriptor___ *) ptrarray[3];
	ao=(struct ArrayObject *)obj->___tags___;
#else
	struct ArrayObject * aonew=allocate_newarray(TAGARRAYTYPE,TAGARRAYINTERVAL+ao->___length___);
#endif
	aonew->___cachedCode___=ao->___length___+1;
	for(i=0;i<ao->___length___;i++) {
	  ARRAYSET(aonew, struct ___TagDescriptor___*, i, ARRAYGET(ao, struct ___TagDescriptor___*, i));
	}
	ARRAYSET(aonew, struct ___TagDescriptor___ *, ao->___length___, tagd);
      }
    }
  }

  {
    struct ___Object___ * tagset=tagd->flagptr;
    
    if(tagset==NULL) {
      tagd->flagptr=obj;
    } else if (tagset->type!=OBJECTARRAYTYPE) {
#ifdef PRECISE_GC
      int ptrarray[]={2, (int) ptr, (int) obj, (int)tagd};
      struct ArrayObject * ao=allocate_newarray(&ptrarray,OBJECTARRAYTYPE,OBJECTARRAYINTERVAL);
      obj=(struct ___Object___ *)ptrarray[2];
      tagd=(struct ___TagDescriptor___ *)ptrarray[3];
#else
      struct ArrayObject * ao=allocate_newarray(OBJECTARRAYTYPE,OBJECTARRAYINTERVAL);
#endif
      ARRAYSET(ao, struct ___Object___ *, 0, tagd->flagptr);
      ARRAYSET(ao, struct ___Object___ *, 1, obj);
      ao->___cachedCode___=2;
      tagd->flagptr=(struct ___Object___ *)ao;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) tagset;
      if (ao->___cachedCode___<ao->___length___) {
	ARRAYSET(ao, struct ___Object___*, ao->___cachedCode___++, obj);
      } else {
	int i;
#ifdef PRECISE_GC
	int ptrarray[]={2, (int) ptr, (int) obj, (int)tagd};
	struct ArrayObject * aonew=allocate_newarray(&ptrarray,OBJECTARRAYTYPE,OBJECTARRAYINTERVAL+ao->___length___);
	obj=(struct ___Object___ *)ptrarray[2];
	tagd=(struct ___TagDescriptor___ *)ptrarray[3];
	ao=(struct ArrayObject *)tagd->flagptr;
#else
	struct ArrayObject * aonew=allocate_newarray(OBJECTARRAYTYPE,OBJECTARRAYINTERVAL);
#endif
	aonew->___cachedCode___=ao->___cachedCode___+1;
	for(i=0;i<ao->___length___;i++) {
	  ARRAYSET(aonew, struct ___Object___*, i, ARRAYGET(ao, struct ___Object___*, i));
	}
	ARRAYSET(aonew, struct ___Object___ *, ao->___cachedCode___, obj);
	tagd->flagptr=(struct ___Object___ *) ao;
      }
    }
  }
}

/* This function clears a tag. */
#ifdef PRECISE_GC
void tagclear(void *ptr, struct ___Object___ * obj, struct ___TagDescriptor___ * tagd) {
#else
void tagclear(struct ___Object___ * obj, struct ___TagDescriptor___ * tagd) {
#endif
  /* We'll assume that tag is alway there.
     Need to statically check for this of course. */
  struct ___Object___ * tagptr=obj->___tags___;

  if (tagptr->type==TAGTYPE) {
    if ((struct ___TagDescriptor___ *)tagptr==tagd)
      obj->___tags___=NULL;
    else
      printf("ERROR 1 in tagclear\n");
  } else {
    struct ArrayObject *ao=(struct ArrayObject *) tagptr;
    int i;
    for(i=0;i<ao->___cachedCode___;i++) {
      struct ___TagDescriptor___ * td=ARRAYGET(ao, struct ___TagDescriptor___ *, i);
      if (td==tagd) {
	ao->___cachedCode___--;
	if (i<ao->___cachedCode___)
	  ARRAYSET(ao, struct ___TagDescriptor___ *, i, ARRAYGET(ao, struct ___TagDescriptor___ *, ao->___cachedCode___));
	ARRAYSET(ao, struct ___TagDescriptor___ *, ao->___cachedCode___, NULL);
	if (ao->___cachedCode___==0)
	  obj->___tags___=NULL;
	goto PROCESSCLEAR;
      }
    }
    printf("ERROR 2 in tagclear\n");
  }
 PROCESSCLEAR:
  {
    struct ___Object___ *tagset=tagd->flagptr;
    if (tagset->type!=OBJECTARRAYTYPE) {
      if (tagset==obj)
	tagd->flagptr=NULL;
      else
	printf("ERROR 3 in tagclear\n");
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) tagset;
      int i;
      for(i=0;i<ao->___cachedCode___;i++) {
	struct ___Object___ * tobj=ARRAYGET(ao, struct ___Object___ *, i);
	if (tobj==obj) {
	  ao->___cachedCode___--;
	  if (i<ao->___cachedCode___)
	    ARRAYSET(ao, struct ___Object___ *, i, ARRAYGET(ao, struct ___Object___ *, ao->___cachedCode___));
	  ARRAYSET(ao, struct ___Object___ *, ao->___cachedCode___, NULL);
	  if (ao->___cachedCode___==0)
	    tagd->flagptr=NULL;
	  goto ENDCLEAR;
	}
      }
      printf("ERROR 4 in tagclear\n");
    }
  }
 ENDCLEAR:
  return;
  
}
 
/* This function allocates a new tag. */
#ifdef PRECISE_GC
struct ___TagDescriptor___ * allocate_tag(void *ptr, int index) {
  struct ___TagDescriptor___ * v=(struct ___TagDescriptor___ *) mygcmalloc((struct garbagelist *) ptr, classsize[TAGTYPE]);
#else
struct ___TagDescriptor___ * allocate_tag(int index) {
  struct ___TagDescriptor___ * v=FREEMALLOC(classsize[TAGTYPE]);
#endif
  v->type=TAGTYPE;
  v->flag=index;
  return v;
} 



/* This function updates the flag for object ptr.  It or's the flag
   with the or mask and and's it with the andmask. */

void flagbody(struct ___Object___ *ptr, int flag);
#ifdef OPTIONAL
void enqueueoptional(struct ___Object___ * currobj);

struct optionaltaskdescriptor *** makeintersectionotd(int num, struct fsanalysiswrapper ** wrapperarray, int *result){
  int i,j,k;
  (*result)=0;
  struct optionaltaskdescriptor *** bigtmparray = RUNMALLOC(sizeof(struct optionaltaskdescriptor **)*maxotd);
  struct fsanalysiswrapper * tmpwrapper;
  struct fsanalysiswrapper * firstwrapper = wrapperarray[0];/*we are sure that num>0*/
  /*we check if the otd of the first wrapper is contained in all others*/
  for(i=0; i<firstwrapper->numoptionaltaskdescriptors; i++){
    struct optionaltaskdescriptor ** tmparray = RUNMALLOC(sizeof(struct optionaltaskdescriptor *) * num);
    struct optionaltaskdescriptor * otd = firstwrapper->optionaltaskdescriptorarray[i];
    tmparray[0]=otd;
    for(j=1; j<num; j++){
      tmpwrapper = wrapperarray[j];
      for(k=0; k<tmpwrapper->numoptionaltaskdescriptors; k++){
	struct optionaltaskdescriptor * tmpotd=tmpwrapper->optionaltaskdescriptorarray[k];
	if(otd->task->name == tmpotd->task->name){
	  tmparray[j]=tmpotd;
	  goto nextwrapper;
	}
	RUNFREE(tmparray);
	goto nextotd;
      }
    nextwrapper:
      ;
    }
    bigtmparray[(*result)]=tmparray;
    (*result)++;          
  nextotd:
    ;
  }
  
  {/*now allocate the good size for otdarray and put the otds*/
    struct optionaltaskdescriptor *** otdarray = RUNMALLOC(sizeof(struct optionaltaskdescriptor *) * (*result));
    for(i=0; i<(*result); i++)     
      otdarray[i]=bigtmparray[i];
    
    RUNFREE(bigtmparray);
    return otdarray;
  }
}
#endif
 

void flagorand(void * ptr, int ormask, int andmask) {
  int oldflag=((int *)ptr)[1];
  int flag=ormask|oldflag;
#ifdef OPTIONAL
  struct ___Object___ * obj = (struct ___Object___ *)ptr;
  if(obj->failedstatus==1){/*store the information about exitfses*/
    int i,j,counter=0, offset=0;
    for(i=0; i<obj->numotds; i++){
      counter+=obj->otds[i]->numenterflags;
    }
    obj->numexitfses=counter;
    if(obj->exitfses!=NULL) RUNFREE(obj->exitfses);
    obj->exitfses= RUNMALLOC(sizeof(int) * counter);
    for(i=0; i<obj->numotds; i++){
      for(j=0; j<obj->otds[i]->numenterflags; j++){
	oldflag=obj->otds[i]->enterflags[j];
	flag=ormask|oldflag;
	flag&=andmask;
	obj->exitfses[j+offset]=flag;
      }
      offset+=obj->otds[i]->numenterflags;
    }
    enqueueoptional(ptr);
  }
  else
#endif
    {
    flag&=andmask;
    // Not sure why this was necessary
    //  if (flag==oldflag) /* Don't do anything */
    //  return;
    //else 
    flagbody(ptr, flag);
    }
}
 
void intflagorand(void * ptr, int ormask, int andmask) {
  int oldflag=((int *)ptr)[1];
  int flag=ormask|oldflag;
#ifdef OPTIONAL
  struct ___Object___ * obj = (struct ___Object___ *)ptr;
  if(obj->failedstatus==1){/*store the information about exitfses*/
    int i,j,counter=0, offset=0;
    for(i=0; i<obj->numotds; i++){
      counter+=obj->otds[i]->numenterflags;
    }
    obj->numexitfses=counter;
    if(obj->exitfses!=NULL) RUNFREE(obj->exitfses);
    obj->exitfses= RUNMALLOC(sizeof(int) * counter);
    for(i=0; i<obj->numotds; i++){
      for(j=0; j<obj->otds[i]->numenterflags; j++){
	oldflag=obj->otds[i]->enterflags[j];
	flag=ormask|oldflag;
	flag&=andmask;
	obj->exitfses[j+offset]=flag;
      }
      offset+=obj->otds[i]->numenterflags;
    }
    enqueueoptional(ptr);
  }
  else
#endif
    {
    flag&=andmask;
    if (flag==oldflag) /* Don't do anything */
      return;
    else flagbody(ptr, flag);
    }
}

void flagorandinit(void * ptr, int ormask, int andmask) {
  int oldflag=((int *)ptr)[1];
  int flag=ormask|oldflag;
#ifdef OPTIONAL
  struct ___Object___ * obj = (struct ___Object___ *)ptr;
  if(obj->failedstatus==1){/*store the information about exitfses*/
    int i,j,counter=0, offset=0;
    for(i=0; i<obj->numotds; i++){
      counter+=obj->otds[i]->numenterflags;
    }
    obj->numexitfses=counter;
    if(obj->exitfses!=NULL) RUNFREE(obj->exitfses);
    obj->exitfses= RUNMALLOC(sizeof(int) * counter);
    for(i=0; i<obj->numotds; i++){
      for(j=0; j<obj->otds[i]->numenterflags; j++){
	oldflag=obj->otds[i]->enterflags[j];
	flag=ormask|oldflag;
	flag&=andmask;
	obj->exitfses[j+offset]=flag;
      }
      offset+=obj->otds[i]->numenterflags;
    }
    enqueueoptional(ptr);
  }
  else
#endif
    {
    flag&=andmask;
    flagbody(ptr,flag);
    }
}

#ifdef OPTIONAL
 removeoptionalfromqueues(int hashcode, struct ___Object___ * currobj, struct parameterwrapper * flagptr){/*find a better way to free the useless instances of the object*/
   while(flagptr!=NULL) {
     struct ___Object___ *temp=NULL;
     struct parameterwrapper *ptr;
     struct RuntimeNode * node = flagptr->objectset->listhead;
     while(node!=NULL){
       temp=(struct ___Object___ *)node->key;
       if(temp->failedstatus==1 && temp->hashcode==currobj->hashcode){
	 if(temp!=currobj){
	   ObjectHashremove(flagptr->objectset, (int)temp);//remove from wrapper
	   //delete the fields that wont be removed by the GC.
	   if(temp->exitfses!=NULL) RUNFREE(temp->exitfses);
	   if(temp->otds!=NULL) RUNFREE(temp->otds);
	   goto nextwrapper;
	 }
	 else{
	   //remove from wrapper
	   ObjectHashremove(flagptr->objectset, (int)temp);
	   goto nextwrapper;
	 }
       }
       node=node->next;
     }
   nextwrapper:
     ;
     flagptr=flagptr->next;
   }
 } 
#endif
 
 void flagbody(struct ___Object___ *ptr, int flag) {
   struct parameterwrapper *flagptr=(struct parameterwrapper *)ptr->flagptr;
   ptr->flag=flag;
   
   /*Remove object from all queues */
   while(flagptr!=NULL) {
     struct parameterwrapper *next;
     struct ___Object___ * tag=ptr->___tags___;
     int FIXME;
     ObjectHashget(flagptr->objectset, (int) ptr, (int *) &next, &FIXME);
     ObjectHashremove(flagptr->objectset, (int)ptr);
     flagptr=next;
   }
   
   {
     struct QueueItem *tmpptr;
     struct parameterwrapper * parameter=objectqueues[ptr->type];
     int i;
     struct parameterwrapper * prevptr=NULL;
     struct ___Object___ *tagptr=ptr->___tags___;
     
     /* Outer loop iterates through all parameter queues an object of
	this type could be in.  */
     
     while(parameter!=NULL) {
       /* Check tags */
       if (parameter->numbertags>0) {
	 if (tagptr==NULL)
	   goto nextloop;//that means the object has no tag but that param needs tag
	 else if(tagptr->type==TAGTYPE) {//one tag
	   struct ___TagDescriptor___ * tag=(struct ___TagDescriptor___*) tagptr;
	   for(i=0;i<parameter->numbertags;i++) {
	     //slotid is parameter->tagarray[2*i];
	     int tagid=parameter->tagarray[2*i+1];
	     if (tagid!=tagptr->flag)
	       goto nextloop; /*We don't have this tag */	  
	   }
	 } else {//multiple tags
	   struct ArrayObject * ao=(struct ArrayObject *) tagptr;
	   for(i=0;i<parameter->numbertags;i++) {
	     //slotid is parameter->tagarray[2*i];
	     int tagid=parameter->tagarray[2*i+1];
	     int j;
	     for(j=0;j<ao->___cachedCode___;j++) {
	       if (tagid==ARRAYGET(ao, struct ___TagDescriptor___*, i)->flag)
		 goto foundtag;
	     }
	     goto nextloop;
	   foundtag:
	     ;
	   }
	 }
       }
       
       /* Check flags */
       for(i=0;i<parameter->numberofterms;i++) {
	 int andmask=parameter->intarray[i*2];
	 int checkmask=parameter->intarray[i*2+1];
	 if ((flag&andmask)==checkmask) {
	   enqueuetasks(parameter, prevptr, ptr);
	   prevptr=parameter;
	   break;
	 }
       }
     nextloop:
       parameter=parameter->next;
     }
     ptr->flagptr=prevptr;
   }
 }
 
#ifdef OPTIONAL
 
 void enqueueoptional(struct ___Object___ * currobj){
   
   struct classanalysiswrapper * classwrapper=NULL; 
   struct fsanalysiswrapper * fswrapper=NULL;
   int counter=0;
   int numoptionaltaskdescriptors=0;
   struct optionaltaskdescriptor *** optionaltaskdescriptorarray=NULL;
   struct fsanalysiswrapper ** goodfswrappersarray=NULL;
   int numgoodfswrappers=0;
#ifdef DEBUG
   if(currobj->numexitfses==0)
     printf("Handling failed object\nType : %i\nFlag : 0x%x\n", currobj->type, currobj->flag);
   else{
     printf("Handling failed object\nType : %i\n", currobj->type);
     int fscount=0;
     for(fscount=0; fscount<currobj->numexitfses; fscount++)
       printf("Flag : 0x%x\n", currobj->exitfses[fscount]);
   }
   struct ___Object___ * tagptr = currobj->___tags___;
   if(tagptr!=NULL){
     if(tagptr->type==TAGTYPE) {
       printf("Tag : %i\n", tagptr->flag);}
     else {
       struct ArrayObject * ao=(struct ArrayObject *) tagptr;
       int numbertags = ao->___length___;
       for(counter=0; counter<numbertags; counter++){
	 printf("Tag : %i\n", ao[counter].flag);
       } 
     }
   }
#endif	  
   /*set the object as failed*/
   currobj->failedstatus = 1;
   
   /*test what optionaltaskdescriptors are available,
     find the class corresponding*/
   
   for(counter = 0; counter<numclasses; counter++){
     classwrapper = classanalysiswrapperarray[counter];
     if(classwrapper == NULL){
       fprintf(stderr, "ERROR : \"struct classanalysiswrapper * classwraper\" is a NULL pointer\n, Analysis has been skipped, check Runtime/task.c, function enqueueoptional\n");
       goto nothingtodo;
     }
     /*check object type*/
     if( currobj->type == classwrapper->type)
       goto classchecked;
   }
#ifdef DEBUG
   printf("No task will use this parameter as optional\n");
#endif
   removeoptionalfromqueues(currobj->hashcode,currobj, objectqueues[currobj->type]);
   goto nothingtodo;
 classchecked:
   ;
#ifdef DEBUG
   printf("Found the class, search through fses\n");
#endif	      
   /*search through fses*/
   goodfswrappersarray = RUNMALLOC(sizeof(struct fsanalysiswrapper *) * classwrapper->numfsanalysiswrappers); /*max number of good fswrappers*/
   if(goodfswrappersarray == NULL){
     fprintf(stderr, "ERROR : \"struct fsanalysiswrapper ** goodfswrappersarray\" is a NULL pointer\n, Analysis has been skipped, check Runtime/task.c, function enqueueoptional\n");
     removeoptionalfromqueues(currobj->hashcode,currobj, objectqueues[currobj->type]);
     goto nothingtodo;
   }
   for(counter = 0; counter<classwrapper->numfsanalysiswrappers; counter++){
     /*test the FS of the object (include flags and tags)*/
     fswrapper = classwrapper->fsanalysiswrapperarray[counter];
     if(fswrapper == NULL){
       fprintf(stderr, "ERROR : struct fsanalysiswrapper * is a NULL pointer\n, Analysis has been skipped, check Runtime/task.c, function enqueueoptional\n");
       removeoptionalfromqueues(currobj->hashcode,currobj, objectqueues[currobj->type]);
       goto nothingtodo;
     }
     /*check tags*/
     struct ___Object___ * tagptr = currobj->___tags___;
     if(fswrapper->numtags>0){
       if (tagptr==NULL)
	 goto nextloop;//that means the object has no tag but that param needs tag
       else if(tagptr->type==TAGTYPE) {//one tag
	 if(fswrapper->numtags!=1) goto nextloop;//we don't have the right number of tags
	 struct ___TagDescriptor___ * tag=(struct ___TagDescriptor___*) tagptr;
	 if (fswrapper->tags[0]!=tagptr->flag)
	   goto nextloop; 
       }	  
       else {//multiple tags
	 struct ArrayObject * ao=(struct ArrayObject *) tagptr;
	 int tag_counter=0;
	 if(ao->___length___!=fswrapper->numtags) goto nextloop;//we don't have the right number of tags
	 for(tag_counter=0;tag_counter<fswrapper->numtags;tag_counter++) {
	   int tagid=fswrapper->tags[tag_counter];
	   int j;
	   for(j=0;j<ao->___cachedCode___;j++) {
	     if (tagid==ARRAYGET(ao, struct ___TagDescriptor___*, tag_counter)->flag)
	       goto foundtag;
	   }
	   goto nextloop;
	 foundtag:
	   ;		      ;
	 }
       }
     }
     
     //check flags
     if(currobj->numexitfses==0){/*first time the method is invoqued*/
       if( currobj->flag == fswrapper->flags){
	 int otdc;
	 optionaltaskdescriptorarray = RUNMALLOC(sizeof(struct optionaltaskdescriptor **) * fswrapper->numoptionaltaskdescriptors);
	 numoptionaltaskdescriptors = fswrapper->numoptionaltaskdescriptors;
	 for(otdc = 0; otdc<fswrapper->numoptionaltaskdescriptors; otdc++){
	   struct optionaltaskdescriptor ** tmpptr = RUNMALLOC(sizeof(struct optionaltaskdescriptor *));
	   tmpptr[0] = fswrapper->optionaltaskdescriptorarray[otdc];
	   optionaltaskdescriptorarray[otdc] = tmpptr;
	 }
	 numgoodfswrappers=1;
	 goto fschecked;
       }
     }
     else if(currobj->numexitfses==1){/*one fs exit*/
       if(currobj->exitfses[0] == fswrapper->flags){
	 int otdc;
	 optionaltaskdescriptorarray = RUNMALLOC(sizeof(struct optionaltaskdescriptor **) * fswrapper->numoptionaltaskdescriptors);
	 numoptionaltaskdescriptors = fswrapper->numoptionaltaskdescriptors;
	 for(otdc = 0; otdc<fswrapper->numoptionaltaskdescriptors; otdc++){
	   struct optionaltaskdescriptor ** tmpptr = RUNMALLOC(sizeof(struct optionaltaskdescriptor *));
	   tmpptr[0] = fswrapper->optionaltaskdescriptorarray[otdc];
	   optionaltaskdescriptorarray[otdc] = tmpptr;
	 }
	 numgoodfswrappers=1;
	 goto fschecked;
       }
     }
     else{
       int fscount=0;
       for(fscount=0; fscount<currobj->numexitfses; fscount++){
	 
	 if( currobj->exitfses[fscount] == fswrapper->flags ){/*see if the fswraper correspond to one of the fses*/
	   goodfswrappersarray[numgoodfswrappers]=fswrapper;
	   numgoodfswrappers++;
	 }
       }
       if(counter==classwrapper->numfsanalysiswrappers-1) goto makeintersection; /*last fswrapper*/
     }
   nextloop:
     ;
   }
 nofs:
   ;
#ifdef DEBUG
   printf("FS not found, Nothing more to do\n");
#endif
   removeoptionalfromqueues(currobj->hashcode,currobj, objectqueues[currobj->type]);
   goto nothingtodo;
 makeintersection:
   ;
   if(numgoodfswrappers==0 || numgoodfswrappers==1) goto nofs; /*nothing has been found, we expect more than one wrapper for multiple flags*/
   optionaltaskdescriptorarray = makeintersectionotd(numgoodfswrappers, goodfswrappersarray, &numoptionaltaskdescriptors);
   if(optionaltaskdescriptorarray==NULL){
     fprintf(stderr, "ERROR : struct optionaltaskdescriptor ** is a NULL pointer\n, Analysis has been skipped, check Runtime/task.c, function enqueueoptional\n");
     goto nothingtodo;
   }
   
  fschecked:
   ;
#ifdef DEBUG
   printf("FS(es) found, intersection created, %i potential tasks :\n", numoptionaltaskdescriptors);
   
   
   /*find the parameterwrapper corresponding to the potential task*/
   for(counter = 0; counter<numoptionaltaskdescriptors; counter++){
     struct optionaltaskdescriptor ** tmpptr = optionaltaskdescriptorarray[counter];
     printf("Task %s\n", tmpptr[0]->task->name);
   }
   
#endif
   {		
     struct parameterwrapper * prevptr = NULL;
     struct parameterwrapper * flagptr = objectqueues[currobj->type];
     removeoptionalfromqueues(currobj->hashcode,currobj, flagptr);
     /*process for each otd*/
     for(counter = 0; counter<numoptionaltaskdescriptors; counter++){
       struct parameterwrapper * parameter = objectqueues[currobj->type];
       struct optionaltaskdescriptor ** tmpptr = optionaltaskdescriptorarray[counter];
       struct optionaltaskdescriptor * currotd = tmpptr[0];
       int otd_counter=0;
       while(parameter->task != currotd->task)
	 parameter=parameter->next;
#ifdef DEBUG
       printf("found parameterwrapper for task : %s\n", parameter->task->name);
#endif
       
       struct ___Object___ * newobj = RUNMALLOC(sizeof(struct ___Object___));
       (*newobj)=(*currobj);
       newobj->numotds=numgoodfswrappers;
       newobj->otds=RUNMALLOC(sizeof(struct optionaltaskdescriptor *) * numgoodfswrappers);
       for(otd_counter=0; otd_counter<numgoodfswrappers; otd_counter++){
	 newobj->otds[otd_counter]=tmpptr[otd_counter];
       }
       enqueuetasks(parameter, prevptr, newobj);
       prevptr = parameter;
       
     nextotd:
       ;
     }
     
   }
 nothingtodo:
   
   /*if(currobj->exitfses!=NULL) RUNFREE(currobj->exitfses);
     if(currobj->otds!=NULL) RUNFREE(currobj->otds);*///there has been a problem just before the program exit, maybe due to the GC ?
   RUNFREE(optionaltaskdescriptorarray);
   ;
 }

 /*we need to check if the object is optional, in this case, test the predicate*/
 /*here is the code for predicate checking*/
 /*The code has not been tested. I don't even know if it is working or efficient but it is a lead...
   if(currotd->numpredicatemembers == 0){
   printf("this task can be fired\n");
   goto enqueuetask;
   }
   else{
   int pred_counter;
   int predicatetrue = 0;
   for(pred_counter = 0; pred_counter<currotd->numpredicatemembers; pred_counter++){
   struct predicatemember * currpred = currotd->predicatememberarray[pred_counter];
   printf("predicate type : %i\n", currpred->type);
   
   //test if the predicate member is true
   struct parameterwrapper * paramwrapper = objectqueues[currpred->type];
   while(paramwrapper!=NULL){
   struct ObjectIterator * it = allocateObjectIterator(paramwrapper->objectset->listhead);
   do{
   struct ___Object___ * obj = (struct ___Object___ *)Objkey(it);
   printf("obj type : %i\n", obj->type);
   if(obj->type == currpred->type){
   //test the predicate
   printf("predicate to test\n");
   //only if good
   goto enqueuetask;
   }
   Objnext(it);
   }while(ObjhasNext(it));
   paramwrapper=paramwrapper->next;
   }
   printf("not the good predicate");
   //goto endanalysis
   }
   //the predicate members have to be all true
   }*/
 
#endif
 
 void enqueuetasks(struct parameterwrapper *parameter, struct parameterwrapper *prevptr, struct ___Object___ *ptr) {
  void * taskpointerarray[MAXTASKPARAMS];
  int j;
  int numparams=parameter->task->numParameters;
  int numiterators=parameter->task->numTotal-1;

  struct taskdescriptor * task=parameter->task;
  
  ObjectHashadd(parameter->objectset, (int) ptr, (int) prevptr, 0);//this add the object to parameterwrapper
  
  /* Add enqueued object to parameter vector */
  taskpointerarray[parameter->slot]=ptr;

  /* Reset iterators */
  for(j=0;j<numiterators;j++) {
    toiReset(&parameter->iterators[j]);
  }

  /* Find initial state */
  for(j=0;j<numiterators;j++) {
  backtrackinit:
    if(toiHasNext(&parameter->iterators[j], taskpointerarray))
      toiNext(&parameter->iterators[j], taskpointerarray);
    else if (j>0) {
      /* Need to backtrack */
      toiReset(&parameter->iterators[j]);
      j--;
      goto backtrackinit;
    } else {
      /* Nothing to enqueue */
      return;
    }
  }

  
  while(1) {
    /* Enqueue current state */
    int launch = 0;
    struct taskparamdescriptor *tpd=RUNMALLOC(sizeof(struct taskparamdescriptor));
    tpd->task=task;
    tpd->numParameters=numiterators+1;
    tpd->parameterArray=RUNMALLOC(sizeof(void *)*(numiterators+1));
    for(j=0;j<=numiterators;j++){
#ifdef OPTIONAL
#ifdef DEBUG
      struct ___Object___ * obj = (struct ___Object___ *)taskpointerarray[j];
      if(obj->failedstatus==1)
	printf("parameter %i used as optional for task %s\n", obj->type, task->name);
#endif
#endif
      tpd->parameterArray[j]=taskpointerarray[j];//store the actual parameters
    }
    /* Enqueue task */
    if ((!gencontains(failedtasks, tpd)&&!gencontains(activetasks,tpd))) {
      genputtable(activetasks, tpd, tpd);
    } else {
      RUNFREE(tpd->parameterArray);
      RUNFREE(tpd);
    }
    
    /* This loop iterates to the next parameter combination */
    if (numiterators==0)
      return;

    for(j=numiterators-1; j<numiterators;j++) {
    backtrackinc:
      if(toiHasNext(&parameter->iterators[j], taskpointerarray))
	toiNext(&parameter->iterators[j], taskpointerarray);
      else if (j>0) {
	/* Need to backtrack */
	toiReset(&parameter->iterators[j]);
	j--;
	goto backtrackinc;
      } else {
	/* Nothing more to enqueue */
	return;
      }
    }
  }
}
 
/* Handler for signals. The signals catch null pointer errors and
   arithmatic errors. */

void myhandler(int sig, siginfo_t *info, void *uap) {
  sigset_t toclear;
#ifdef DEBUG
  printf("sig=%d\n",sig);
  printf("signal\n");
#endif
  sigemptyset(&toclear);
  sigaddset(&toclear, sig);
  sigprocmask(SIG_UNBLOCK, &toclear,NULL); 
  longjmp(error_handler,1);
}

fd_set readfds;
int maxreadfd;
struct RuntimeHash *fdtoobject;

void addreadfd(int fd) {
  if (fd>=maxreadfd)
    maxreadfd=fd+1;
  FD_SET(fd, &readfds);
}

void removereadfd(int fd) {
  FD_CLR(fd, &readfds);
  if (maxreadfd==(fd+1)) {
    maxreadfd--;
    while(maxreadfd>0&&!FD_ISSET(maxreadfd-1, &readfds))
      maxreadfd--;
  }
}

#ifdef PRECISE_GC
#define OFFSET 2
#else
#define OFFSET 0
#endif

void executetasks() {
  void * taskpointerarray[MAXTASKPARAMS+OFFSET];

  /* Set up signal handlers */
  struct sigaction sig;
  sig.sa_sigaction=&myhandler;
  sig.sa_flags=SA_SIGINFO;
  sigemptyset(&sig.sa_mask);

  /* Catch bus errors, segmentation faults, and floating point exceptions*/
  sigaction(SIGBUS,&sig,0);
  sigaction(SIGSEGV,&sig,0);
  sigaction(SIGFPE,&sig,0);
  sigaction(SIGPIPE,&sig,0);

  /* Zero fd set */
  FD_ZERO(&readfds);
  maxreadfd=0;
  fdtoobject=allocateRuntimeHash(100);

  /* Map first block of memory to protected, anonymous page */
  mmap(0, 0x1000, 0, MAP_SHARED|MAP_FIXED|MAP_ANON, -1, 0);

  newtask:
  while((hashsize(activetasks)>0)||(maxreadfd>0)) {

    /* Check if any filedescriptors have IO pending */
    if (maxreadfd>0) {
      int i;
      struct timeval timeout={0,0};
      fd_set tmpreadfds;
      int numselect;
      tmpreadfds=readfds;
      numselect=select(maxreadfd, &tmpreadfds, NULL, NULL, &timeout);
      if (numselect>0) {
	/* Process ready fd's */
	int fd;
	for(fd=0;fd<maxreadfd;fd++) {
	  if (FD_ISSET(fd, &tmpreadfds)) {
	    /* Set ready flag on object */
	    void * objptr;
	    //	    printf("Setting fd %d\n",fd);
	    if (RuntimeHashget(fdtoobject, fd,(int *) &objptr)) {
	      intflagorand(objptr,1,0xFFFFFFFF); /* Set the first flag to 1 */
	    }
	  }
	}
      }
    }

    /* See if there are any active tasks */
    if (hashsize(activetasks)>0) {
      int i;
      currtpd=(struct taskparamdescriptor *) getfirstkey(activetasks);
      genfreekey(activetasks, currtpd);
      
      /* Check if this task has failed, allow a task that contains optional objects to fire */
      if (gencontains(failedtasks, currtpd)) {
	// Free up task parameter descriptor
	RUNFREE(currtpd->parameterArray);
	RUNFREE(currtpd);
	goto newtask;
      }
      int numparams=currtpd->task->numParameters;
      int numtotal=currtpd->task->numTotal;
      
      /* Make sure that the parameters are still in the queues */
      for(i=0;i<numparams;i++) {
	void * parameter=currtpd->parameterArray[i];
	struct parameterdescriptor * pd=currtpd->task->descriptorarray[i];
	struct parameterwrapper *pw=(struct parameterwrapper *) pd->queue;
	int j;
	/* Check that object is still in queue */
#ifdef OPTIONAL
	struct ___Object___ * obj = (struct ___Object___ *)parameter;
	if(obj->failedstatus==1){
	  struct ___Object___ *temp=NULL;
	  struct parameterwrapper * ptr;
	  struct ObjectNode * node = pw->objectset->listhead;
	  while(node!=NULL){
	    temp=(struct ___Object___ *)node->key;
	    if(temp->failedstatus==1 && temp->hashcode==obj->hashcode){
	      if(temp==obj)
		goto parameterpresent;
	    }
	    node=node->next;
	  }
	  RUNFREE(currtpd->parameterArray);
	  RUNFREE(currtpd);
	  goto newtask;
	}
	else
#endif
	{
	  if (!ObjectHashcontainskey(pw->objectset, (int) parameter)) {
	    RUNFREE(currtpd->parameterArray);
	    RUNFREE(currtpd);
	    goto newtask;
	  }
	}
      parameterpresent:
	;
	/* Check that object still has necessary tags */
	for(j=0;j<pd->numbertags;j++) {
	  int slotid=pd->tagarray[2*j]+numparams;
	  struct ___TagDescriptor___ *tagd=currtpd->parameterArray[slotid];
	  if (!containstag(parameter, tagd)) {
	    RUNFREE(currtpd->parameterArray);
	    RUNFREE(currtpd);
	    goto newtask;
	  }
	}
	
	taskpointerarray[i+OFFSET]=parameter;
      }
      /* Copy the tags */
      for(;i<numtotal;i++) {
	taskpointerarray[i+OFFSET]=currtpd->parameterArray[i];
      }

      {
	/* Checkpoint the state */
	forward=allocateRuntimeHash(100);
	reverse=allocateRuntimeHash(100);
	void ** checkpoint=makecheckpoint(currtpd->task->numParameters, currtpd->parameterArray, forward, reverse);
	int x;
	if (x=setjmp(error_handler)) {
	  int counter;
	  /* Recover */
#ifdef DEBUG
	  printf("Fatal Error=%d, Recovering!\n",x);
#endif
	  genputtable(failedtasks,currtpd,currtpd);
	  restorecheckpoint(currtpd->task->numParameters, currtpd->parameterArray, checkpoint, forward, reverse);

#ifdef OPTIONAL
#ifdef DEBUG	  
	  printf("%i object(s) restored\n", currtpd->task->numParameters);
#endif 
	  
	  for(counter=0; counter<currtpd->task->numParameters; counter++){
	    //remove the object from the previous parameterwrapper (maybe not necessary)
	    //do a new instance of the object. It allows the restored object to be used by other tasks as a non optional arg.
	    struct ___Object___ * currobj = RUNMALLOC(sizeof(struct ___Object___));
	    (*currobj)=(*(struct ___Object___ *)currtpd->parameterArray[counter]);
	    currobj->numexitfses = 0;
	    currobj->exitfses = NULL;
	    currobj->otds=NULL;
	    currobj->hashcode=(int)currobj;
	    enqueueoptional( currobj );
	  }
#endif
	  freeRuntimeHash(forward);
	  freeRuntimeHash(reverse);
	  freemalloc();
	  forward=NULL;
	  reverse=NULL;
	} else {
	  if (injectfailures) {
	    if ((((double)random())/RAND_MAX)<failurechance) {
	      printf("\nINJECTING TASK FAILURE to %s\n", currtpd->task->name);
	      longjmp(error_handler,10);
	    }
	  }
	  /* Actually call task */
#ifdef PRECISE_GC
	  ((int *)taskpointerarray)[0]=currtpd->task->numParameters;
	  taskpointerarray[1]=NULL;
#endif
	  if(debugtask){
	    printf("ENTER %s count=%d\n",currtpd->task->name, (instaccum-instructioncount));
	    ((void (*) (void **)) currtpd->task->taskptr)(taskpointerarray);
	    printf("EXIT %s count=%d\n",currtpd->task->name, (instaccum-instructioncount));
	  } else
	    ((void (*) (void **)) currtpd->task->taskptr)(taskpointerarray);
	    
	  freeRuntimeHash(forward);
	  freeRuntimeHash(reverse);
	  freemalloc();
	  // Free up task parameter descriptor
	  RUNFREE(currtpd->parameterArray);
	  RUNFREE(currtpd);
	  forward=NULL;
	  reverse=NULL;
	}
      }
    }
  }
}
 
/* This function processes an objects tags */
void processtags(struct parameterdescriptor *pd, int index, struct parameterwrapper *parameter, int * iteratorcount, int *statusarray, int numparams) {
  int i;
  
  for(i=0;i<pd->numbertags;i++) {
    int slotid=pd->tagarray[2*i];
    int tagid=pd->tagarray[2*i+1];
    
    if (statusarray[slotid+numparams]==0) {
      parameter->iterators[*iteratorcount].istag=1;
      parameter->iterators[*iteratorcount].tagid=tagid;
      parameter->iterators[*iteratorcount].slot=slotid+numparams;
      parameter->iterators[*iteratorcount].tagobjectslot=index;
      statusarray[slotid+numparams]=1;
      (*iteratorcount)++;
    }
  }
}


void processobject(struct parameterwrapper *parameter, int index, struct parameterdescriptor *pd, int *iteratorcount, int * statusarray, int numparams) {
  int i;
  int tagcount=0;
  struct ObjectHash * objectset=((struct parameterwrapper *)pd->queue)->objectset;

  parameter->iterators[*iteratorcount].istag=0;
  parameter->iterators[*iteratorcount].slot=index;
  parameter->iterators[*iteratorcount].objectset=objectset;
  statusarray[index]=1;

  for(i=0;i<pd->numbertags;i++) {
    int slotid=pd->tagarray[2*i];
    int tagid=pd->tagarray[2*i+1];
    if (statusarray[slotid+numparams]!=0) {
      /* This tag has already been enqueued, use it to narrow search */
      parameter->iterators[*iteratorcount].tagbindings[tagcount]=slotid+numparams;
      tagcount++;
    }
  }
  parameter->iterators[*iteratorcount].numtags=tagcount;

  (*iteratorcount)++;
}

/* This function builds the iterators for a task & parameter */

void builditerators(struct taskdescriptor * task, int index, struct parameterwrapper * parameter) {
  int statusarray[MAXTASKPARAMS];
  int i;
  int numparams=task->numParameters;
  int iteratorcount=0;
  for(i=0;i<MAXTASKPARAMS;i++) statusarray[i]=0;

  statusarray[index]=1; /* Initial parameter */
  /* Process tags for initial iterator */
  
  processtags(task->descriptorarray[index], index, parameter, & iteratorcount, statusarray, numparams);
  
  while(1) {
  loopstart:
    /* Check for objects with existing tags */
    for(i=0;i<numparams;i++) {
      if (statusarray[i]==0) {
	struct parameterdescriptor *pd=task->descriptorarray[i];
	int j;
	for(j=0;j<pd->numbertags;j++) {
	  int slotid=pd->tagarray[2*j];
	  if(statusarray[slotid+numparams]!=0) {
	    processobject(parameter, i, pd, &iteratorcount, statusarray, numparams);
	    processtags(pd, i, parameter, &iteratorcount, statusarray, numparams);
	    goto loopstart;
	  }
	}
      }
    }

    /* Next do objects w/ unbound tags*/

    for(i=0;i<numparams;i++) {
      if (statusarray[i]==0) {
	struct parameterdescriptor *pd=task->descriptorarray[i];
	if (pd->numbertags>0) {
	  processobject(parameter, i, pd, &iteratorcount, statusarray, numparams);
	  processtags(pd, i, parameter, &iteratorcount, statusarray, numparams);
	  goto loopstart;
	}
      }
    }

    /* Nothing with a tag enqueued */

    for(i=0;i<numparams;i++) {
      if (statusarray[i]==0) {
	struct parameterdescriptor *pd=task->descriptorarray[i];
	processobject(parameter, i, pd, &iteratorcount, statusarray, numparams);
	processtags(pd, i, parameter, &iteratorcount, statusarray, numparams);
	goto loopstart;
      }
    }

    /* Nothing left */
    return;
  }
}


 

/* This function processes the task information to create queues for
   each parameter type. */

void processtasks() {
  int i;
  for(i=0;i<numtasks;i++) {
    struct taskdescriptor * task=taskarray[i];
    int j;

    for(j=0;j<task->numParameters;j++) {
      struct parameterdescriptor *param=task->descriptorarray[j];
      struct parameterwrapper * parameter=RUNMALLOC(sizeof(struct parameterwrapper));
      struct parameterwrapper ** ptr=&objectqueues[param->type];

      param->queue=parameter;
      parameter->objectset=allocateObjectHash(10);
      parameter->numberofterms=param->numberterms;
      parameter->intarray=param->intarray;
      parameter->numbertags=param->numbertags;
      parameter->tagarray=param->tagarray;
      parameter->task=task;
      /* Link new queue in */
      while((*ptr)!=NULL)
	ptr=&((*ptr)->next);
      (*ptr)=parameter;
    }

    /* Build iterators for parameters */
    for(j=0;j<task->numParameters;j++) {
      struct parameterdescriptor *param=task->descriptorarray[j];
      struct parameterwrapper *parameter=param->queue;      
      parameter->slot=j;
      builditerators(task, j, parameter);
    }
  }
}

void toiReset(struct tagobjectiterator * it) {
  if (it->istag) {
    it->tagobjindex=0;
  } else if (it->numtags>0) {
    it->tagobjindex=0;
  } else {
    ObjectHashiterator(it->objectset, &it->it);
  }
}

int toiHasNext(struct tagobjectiterator *it, void ** objectarray) {
  if (it->istag) {
    /* Iterate tag */
    /* Get object with tags */
    struct ___Object___ *obj=objectarray[it->tagobjectslot];
    struct ___Object___ *tagptr=obj->___tags___;
    if (tagptr->type==TAGTYPE) {
      if ((it->tagobjindex==0)&& /* First object */
	  (it->tagid==((struct ___TagDescriptor___ *)tagptr)->flag)) /* Right tag type */
	return 1;
      else
	return 0;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) tagptr;
      int tagindex=it->tagobjindex;
      for(;tagindex<ao->___cachedCode___;tagindex++) {
	struct ___TagDescriptor___ *td=ARRAYGET(ao, struct ___TagDescriptor___ *, tagindex);
	if (td->flag==it->tagid) {
	  it->tagobjindex=tagindex; /* Found right type of tag */
	  return 1;
	}
      }
      return 0;
    }
  } else if (it->numtags>0) {
    /* Use tags to locate appropriate objects */
    struct ___TagDescriptor___ *tag=objectarray[it->tagbindings[0]];
    struct ___Object___ *objptr=tag->flagptr;
    int i;
    if (objptr->type!=OBJECTARRAYTYPE) {
      if (it->tagobjindex>0)
	return 0;
      if (!ObjectHashcontainskey(it->objectset, (int) objptr))
	return 0;
      for(i=1;i<it->numtags;i++) {
	struct ___TagDescriptor___ *tag2=objectarray[it->tagbindings[i]];
	if (!containstag(objptr,tag2))
	  return 0;
      }
      return 1;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) objptr;
      int tagindex;
      int i;
      for(tagindex=it->tagobjindex;tagindex<ao->___cachedCode___;tagindex++) {
	struct ___Object___ *objptr=ARRAYGET(ao, struct ___Object___*, tagindex);
	if (!ObjectHashcontainskey(it->objectset, (int) objptr))
	  continue;
	for(i=1;i<it->numtags;i++) {
	  struct ___TagDescriptor___ *tag2=objectarray[it->tagbindings[i]];
	  if (!containstag(objptr,tag2))
	    goto nexttag;
	}
	return 1;
      nexttag:
	;
      }
      it->tagobjindex=tagindex;
      return 0;
    }
  } else {
    return ObjhasNext(&it->it);
  }
}

int containstag(struct ___Object___ *ptr, struct ___TagDescriptor___ *tag) {
  int j;
  struct ___Object___ * objptr=tag->flagptr;
  if (objptr->type==OBJECTARRAYTYPE) {
    struct ArrayObject *ao=(struct ArrayObject *)objptr;
    for(j=0;j<ao->___cachedCode___;j++) {
      if (ptr==ARRAYGET(ao, struct ___Object___*, j))
	return 1;
    }
    return 0;
  } else
    return objptr==ptr;
}

void toiNext(struct tagobjectiterator *it , void ** objectarray) {
  /* hasNext has all of the intelligence */
  if(it->istag) {
    /* Iterate tag */
    /* Get object with tags */
    struct ___Object___ *obj=objectarray[it->tagobjectslot];
    struct ___Object___ *tagptr=obj->___tags___;
    if (tagptr->type==TAGTYPE) {
      it->tagobjindex++;
      objectarray[it->slot]=tagptr;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) tagptr;
      objectarray[it->slot]=ARRAYGET(ao, struct ___TagDescriptor___ *, it->tagobjindex++);
    }
  } else if (it->numtags>0) {
    /* Use tags to locate appropriate objects */
    struct ___TagDescriptor___ *tag=objectarray[it->tagbindings[0]];
    struct ___Object___ *objptr=tag->flagptr;
    if (objptr->type!=OBJECTARRAYTYPE) {
      it->tagobjindex++;
      objectarray[it->slot]=objptr;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) objptr;
      objectarray[it->slot]=ARRAYGET(ao, struct ___Object___ *, it->tagobjindex++);
    }
  } else {
    /* Iterate object */
    objectarray[it->slot]=(void *)Objkey(&it->it);
    Objnext(&it->it);
  }
}


#endif
