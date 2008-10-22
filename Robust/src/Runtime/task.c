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
struct ctable * forward;
struct ctable * reverse;

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
  failedtasks=genallocatehashtable((unsigned int(*) (void *)) &hashCodetpd,
                                   (int(*) (void *,void *)) &comparetpd);
  /* Create queue of active tasks */
  activetasks=genallocatehashtable((unsigned int(*) (void *)) &hashCodetpd,
                                   (int(*) (void *,void *)) &comparetpd);

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
  for(i=1; i<argc; i++) {
    int length=strlen(argv[i]);
#ifdef PRECISE_GC
    struct ___String___ *newstring=NewString(NULL, argv[i],length);
#else
    struct ___String___ *newstring=NewString(argv[i],length);
#endif
    ((void **)(((char *)&stringarray->___length___)+sizeof(int)))[i-1]=newstring;
  }

  /* Set initialized flag for startup object */
  flagorand(startupobject,1,0xFFFFFFFF);
  enqueueObject(startupobject);
}

int hashCodetpd(struct taskparamdescriptor *ftd) {
  int hash=(int)ftd->task;
  int i;
  for(i=0; i<ftd->numParameters; i++) {
    hash^=(int)ftd->parameterArray[i];
  }
  return hash;
}

int comparetpd(struct taskparamdescriptor *ftd1, struct taskparamdescriptor *ftd2) {
  int i;
  if (ftd1->task!=ftd2->task)
    return 0;
  for(i=0; i<ftd1->numParameters; i++)
    if(ftd1->parameterArray[i]!=ftd2->parameterArray[i])
      return 0;
#ifdef OPTIONAL
  for(i=0; i<ftd1->numParameters; i++) {
    if(ftd1->failed[i]!=ftd2->failed[i])
      return 0;
  }
#endif
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
      for(i=0; i<ao->___cachedCode___; i++) {
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
	for(i=0; i<ao->___length___; i++) {
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
	for(i=0; i<ao->___length___; i++) {
	  ARRAYSET(aonew, struct ___Object___*, i, ARRAYGET(ao, struct ___Object___*, i));
	}
	ARRAYSET(aonew, struct ___Object___ *, ao->___cachedCode___, obj);
	tagd->flagptr=(struct ___Object___ *) aonew;
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
    for(i=0; i<ao->___cachedCode___; i++) {
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
      for(i=0; i<ao->___cachedCode___; i++) {
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
void enqueueoptional(struct ___Object___ * currobj, int numfailedfses, int * failedfses, struct taskdescriptor * task, int index);
#endif

int flagcomp(const int *val1, const int *val2) {
  return (*val1)-(*val2);
}

void flagorand(void * ptr, int ormask, int andmask) {
#ifdef OPTIONAL
  struct ___Object___ * obj = (struct ___Object___ *)ptr;
  if(obj->numfses) { /*store the information about fses*/
    int flag, i, j,counter, offset=0;
    for(i=0; i<obj->numfses; i++) {
      int oldoffset;
      counter=obj->fses[offset++];
      oldoffset=offset;
      for(j=0; j<counter; j++) {
	flag=obj->fses[offset];
	obj->fses[offset++]=(flag|ormask)&andmask;
      }
      qsort(&obj->fses[oldoffset], sizeof(int), counter, (int(*) (const void *, const void *)) &flagcomp);
    }
    enqueueoptional(obj, 0, NULL, NULL, 0);
  } else
#endif
  {
    int oldflag=((int *)ptr)[1];
    int flag=ormask|oldflag;
    flag&=andmask;
    flagbody(ptr, flag);
  }
}

bool intflagorand(void * ptr, int ormask, int andmask) {
#ifdef OPTIONAL
  struct ___Object___ * obj = (struct ___Object___ *)ptr;
  if(obj->numfses) { /*store the information about fses*/
    int flag, i, j,counter, offset=0;
    for(i=0; i<obj->numfses; i++) {
      int oldoffset;
      counter=obj->fses[offset++];
      oldoffset=offset;
      for(j=0; j<counter; j++) {
	flag=obj->fses[offset];
	obj->fses[offset++]=(flag|ormask)&andmask;
      }
      qsort(&obj->fses[oldoffset], sizeof(int), counter, (int(*) (const void *, const void *)) &flagcomp);
    }
    enqueueoptional(obj, 0, NULL, NULL, 0);
  } else
#endif
  {
    int oldflag=((int *)ptr)[1];
    int flag=ormask|oldflag;
    flag&=andmask;
    if (flag==oldflag)   /* Don't do anything */
      return false;
    else {
      flagbody(ptr, flag);
      return true;
    }
  }
}

void flagorandinit(void * ptr, int ormask, int andmask) {
  int oldflag=((int *)ptr)[1];
  int flag=ormask|oldflag;
  flag&=andmask;
  flagbody(ptr,flag);
}

void flagbody(struct ___Object___ *ptr, int flag) {
  struct parameterwrapper *flagptr=(struct parameterwrapper *)ptr->flagptr;
  ptr->flag=flag;

  /*Remove object from all queues */
  while(flagptr!=NULL) {
    struct parameterwrapper *next;
    int UNUSED, UNUSED2;
    int * enterflags;
    ObjectHashget(flagptr->objectset, (int) ptr, (int *) &next, (int *) &enterflags, &UNUSED, &UNUSED2);
    ObjectHashremove(flagptr->objectset, (int)ptr);
    if (enterflags!=NULL)
      free(enterflags);
    flagptr=next;
  }
}

void enqueueObject(void *vptr) {
  struct ___Object___ *ptr = (struct ___Object___ *)vptr;

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
	  goto nextloop; //that means the object has no tag but that param needs tag
	else if(tagptr->type==TAGTYPE) { //one tag
	  struct ___TagDescriptor___ * tag=(struct ___TagDescriptor___*) tagptr;
	  for(i=0; i<parameter->numbertags; i++) {
	    //slotid is parameter->tagarray[2*i];
	    int tagid=parameter->tagarray[2*i+1];
	    if (tagid!=tagptr->flag)
	      goto nextloop; /*We don't have this tag */
	  }
	} else { //multiple tags
	  struct ArrayObject * ao=(struct ArrayObject *) tagptr;
	  for(i=0; i<parameter->numbertags; i++) {
	    //slotid is parameter->tagarray[2*i];
	    int tagid=parameter->tagarray[2*i+1];
	    int j;
	    for(j=0; j<ao->___cachedCode___; j++) {
	      if (tagid==ARRAYGET(ao, struct ___TagDescriptor___*, j)->flag)
		goto foundtag;
	    }
	    goto nextloop;
foundtag:
	    ;
	  }
	}
      }

      /* Check flags */
      for(i=0; i<parameter->numberofterms; i++) {
	int andmask=parameter->intarray[i*2];
	int checkmask=parameter->intarray[i*2+1];
	if ((ptr->flag&andmask)==checkmask) {
	  enqueuetasks(parameter, prevptr, ptr, NULL, 0);
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

int checktags(struct ___Object___ * currobj, struct fsanalysiswrapper * fswrapper) {
  /* Check Tags */
  struct ___Object___ * tagptr = currobj->___tags___;
  if(fswrapper->numtags>0) {
    if (tagptr==NULL)
      return 0; //that means the object has no tag but that param
    //needs tag
    else if(tagptr->type==TAGTYPE) { //one tag
      if(fswrapper->numtags!=1)
	return 0; //we don't have the right number of tags
      struct ___TagDescriptor___ * tag=(struct ___TagDescriptor___*) tagptr;
      if (fswrapper->tags[0]!=tagptr->flag)
	return 0;
    } else {  //multiple tags
      struct ArrayObject * ao=(struct ArrayObject *) tagptr;
      int tag_counter=0;
      int foundtag=0;

      if(ao->___length___!=fswrapper->numtags)
	return 0; //we don't have the right number of tags
      for(tag_counter=0; tag_counter<fswrapper->numtags; tag_counter++) {
	int tagid=fswrapper->tags[tag_counter];
	int j;
	for(j=0; j<ao->___cachedCode___; j++) {
	  if (tagid==ARRAYGET(ao, struct ___TagDescriptor___*, tag_counter)->flag)
	    return 1;
	}
	return 0;
      }
    }
  }
  return 1;
}

int getlength(int *flist, int len) {
  int count=0;
  int i;
  for(i=0; i<len; i++) {
    int size=flist[count];
    count+=1+size;
  }
  return count;
}

int * domergeor(int *flist1, int len1, int *flist2, int len2) {
  int size1=getlength(flist1, len1);
  int size2=getlength(flist2, len2);
  int *merge=RUNMALLOC((size1+size2)*sizeof(int));
  memcpy(merge, flist1, size1*sizeof(int));
  memcpy(&merge[size1], flist2, size2*sizeof(int));
  return merge;
}

int domerge(int * flist1, int len1, int *flist2, int len2, int *merge) {
  int count=0;
  int i=0;
  int j=0;
  while(i<len1||j<len2) {
    if (i<len1&&(j==len2||flist1[i]<flist2[j])) {
      if(merge!=NULL) {
	merge[count]=flist1[i];
      }
      i++;
      count++;
    } else if (j<len2&&(i==len1||flist2[j]<flist1[i])) {
      if(merge!=NULL) {
	merge[count]=flist2[j];
      }
      j++;
      count++;
    } else if (i<len1&&j<len2&&flist1[i]==flist2[j]) {
      if(merge!=NULL) {
	merge[count]=flist1[i];
      }
      i++;
      j++;
      count++;
    }
  }
  return count;
}

/* Merge flags from ftlmerge into ftl. */
void mergeitems(struct failedtasklist *ftl, struct failedtasklist *ftlmerge) {
  int length=0;
  int i,j;
  int *mergedlist;
  int offset=0;
  for(i=0; i<ftl->numflags; i++) {
    int len=ftl->flags[offset++];
    int offsetmerge=0;
    for(j=0; j<ftlmerge->numflags; j++) {
      int lenmerge=ftlmerge->flags[offsetmerge++];
      length+=1+domerge(&ftl->flags[offset],len,&ftlmerge->flags[offsetmerge],lenmerge, NULL);
      offsetmerge+=lenmerge;
    }
    offset+=len;
  }
  mergedlist=RUNMALLOC(sizeof(int)*length);

  offset=0;
  length=0;
  for(i=0; i<ftl->numflags; i++) {
    int len=ftl->flags[offset++];
    int offsetmerge=0;
    for(j=0; j<ftlmerge->numflags; j++) {
      int lenmerge=ftlmerge->flags[offsetmerge++];
      int size=domerge(&ftl->flags[offset],len,&ftlmerge->flags[offsetmerge],lenmerge,&mergedlist[length+1]);
      mergedlist[length]=size;
      length+=size+1;
    }
  }
  RUNFREE(ftl->flags);
  ftl->flags=mergedlist;
  ftl->numflags*=ftlmerge->numflags;
}

void mergefailedlists(struct failedtasklist **andlist, struct failedtasklist *list) {
  struct failedtasklist *tmpptr;
  while((*andlist)!=NULL) {
    struct failedtasklist *searchftl=list;
    while(searchftl!=NULL) {
      if ((*andlist)->task==searchftl->task&&
          (*andlist)->index==searchftl->index) {
	mergeitems(*andlist, searchftl);
	break;
      }
      searchftl=searchftl->next;
    }
    if (searchftl==NULL) {
      //didn't find andlist
      tmpptr=*andlist;
      *andlist=(*andlist)->next; //splice item out of list
      RUNFREE(tmpptr->flags); //free the item
      RUNFREE(tmpptr);
    } else {
      andlist=&((*andlist)->next); //iterate to next item
    }
  }
  //free the list we're searching
  while(list!=NULL) {
    tmpptr=list->next;
    RUNFREE(list->flags);
    RUNFREE(list);
    list=tmpptr;
  }
}

struct failedtasklist * processfailstate(struct classanalysiswrapper * classwrapper, struct taskdescriptor *task, int index, struct ___Object___ * currobj, int flagstate) {
  struct failedtasklist *list=NULL;
  int i,h;
  struct fsanalysiswrapper *fswrapper=NULL;
  for(h=0; h<classwrapper->numfsanalysiswrappers; h++) {
    struct fsanalysiswrapper * tmp=classwrapper->fsanalysiswrapperarray[h];
    if (tmp->flags==flagstate&&checktags(currobj, tmp)) {
      //we only match exactly here
      fswrapper=tmp;
      break;
    }
  }
  if (fswrapper==NULL)
    return list;
  for(i=0; i<fswrapper->numtaskfailures; i++) {
    int j;
    struct taskfailure * taskfail=fswrapper->taskfailurearray[i];
    if (taskfail->task==task&&taskfail->index==index) {
      int start=0;
      while(start<taskfail->numoptionaltaskdescriptors) {
	struct taskdescriptor *currtask=NULL;
	struct failedtasklist *tmpftl;
	int currindex;
	int totallength=0;
	int *enterflags;
	int numenterflags, offset;
	struct parameterwrapper *pw;
	for(j=start; j<taskfail->numoptionaltaskdescriptors; j++) {
	  struct optionaltaskdescriptor *otd=taskfail->optionaltaskdescriptorarray[j];
	  if(currtask==NULL) {
	    currtask=otd->task;
	    currindex=otd->index;
	  } else if (currtask!=otd->task||currindex!=otd->index)
	    break;
	  totallength+=otd->numenterflags;
	}
	pw=currtask->descriptorarray[currindex]->queue;
	enterflags=RUNMALLOC(totallength*sizeof(int));
	numenterflags=j-start;
	offset=0;
	for(start; start<j; start++) {
	  struct optionaltaskdescriptor *otd=taskfail->optionaltaskdescriptorarray[start];
	  enterflags[offset++]=otd->numenterflags;
	  memcpy(&enterflags[offset], otd->enterflags, otd->numenterflags*sizeof(int));
	  offset+=otd->numenterflags;
	}
	tmpftl=RUNMALLOC(sizeof(struct failedtasklist));
	tmpftl->next=list;
	tmpftl->task=currtask;
	tmpftl->numflags=numenterflags;
	tmpftl->flags=enterflags;
	list=tmpftl;
      }
    }
  }
  return list;
}

struct failedtasklist * processnormfailstate(struct classanalysiswrapper * classwrapper, struct ___Object___ * currobj, int flagstate) {
  struct failedtasklist *list=NULL;
  int i,h;
  int start=0;
  struct fsanalysiswrapper *fswrapper=NULL;
  for(h=0; h<classwrapper->numfsanalysiswrappers; h++) {
    struct fsanalysiswrapper * tmp=classwrapper->fsanalysiswrapperarray[h];
    if (tmp->flags==flagstate&&checktags(currobj, tmp)) {
      //we only match exactly here
      fswrapper=tmp;
      break;
    }
  }
  if(fswrapper==NULL)
    return NULL;

  while(start<fswrapper->numoptionaltaskdescriptors) {
    struct taskdescriptor *currtask=NULL;
    struct failedtasklist *tmpftl;
    int j;
    int currindex;
    int totallength=0;
    int *enterflags;
    int numenterflags, offset;
    struct parameterwrapper *pw;
    for(j=start; j<fswrapper->numoptionaltaskdescriptors; j++) {
      struct optionaltaskdescriptor *otd=fswrapper->optionaltaskdescriptorarray[j];
      if(currtask==NULL) {
	currtask=otd->task;
	currindex=otd->index;
      } else if (currtask!=otd->task||currindex!=otd->index)
	break;
      totallength+=otd->numenterflags;
    }
    pw=currtask->descriptorarray[currindex]->queue;
    enterflags=RUNMALLOC(totallength*sizeof(int));
    numenterflags=j-start;
    offset=0;
    for(start; start<j; start++) {
      struct optionaltaskdescriptor *otd=fswrapper->optionaltaskdescriptorarray[start];
      enterflags[offset++]=otd->numenterflags;
      memcpy(&enterflags[offset], otd->enterflags, otd->numenterflags*sizeof(int));
      offset+=otd->numenterflags;
    }
    tmpftl=RUNMALLOC(sizeof(struct failedtasklist));
    tmpftl->next=list;
    tmpftl->task=currtask;
    tmpftl->numflags=numenterflags;
    tmpftl->flags=enterflags;
    list=tmpftl;
  }
  return list;
}



void enqueuelist(struct ___Object___ * currobj, struct failedtasklist * andlist) {
  while(andlist!=NULL) {
    struct failedtasklist *tmp=andlist;
    struct parameterwrapper *pw=andlist->task->descriptorarray[andlist->index]->queue;
    struct parmaeterwrapper *next;
    int * flags;
    int numflags;
    int isnonfailed;

    if (enqueuetasks(pw, currobj->flagptr, currobj, tmp->flags, tmp->numflags))
      currobj->flagptr=pw;

    andlist=andlist->next;
    RUNFREE(tmp);
  }
}

void enqueueoptional(struct ___Object___ * currobj, int numfailedfses, int * failedfses, struct taskdescriptor * task, int index) {
  struct classanalysiswrapper * classwrapper=NULL;

  /*test what optionaltaskdescriptors are available, find the class
     corresponding*/
  if (classanalysiswrapperarray[currobj->type]!=NULL) {
    classwrapper = classanalysiswrapperarray[currobj->type];
  } else
    return;

  if(task!=NULL) {
    /* We have a failure */
    if (failedfses==NULL) {
      /* Failed in normal state */
      /*first time the method is invoked*/
      int i,h;
      struct fsanalysiswrapper *fswrapper=NULL;

      for(h=0; h<classwrapper->numfsanalysiswrappers; h++) {
	struct fsanalysiswrapper * tmp=classwrapper->fsanalysiswrapperarray[h];
	if (tmp->flags==currobj->flag&&checktags(currobj, tmp)) {
	  //we only match exactly here
	  fswrapper=tmp;
	  break;
	}
      }
      if(fswrapper==NULL) //nothing to do in this state
	return;
      for(i=0; i<fswrapper->numtaskfailures; i++) {
	int j;
	struct taskfailure * taskfail=fswrapper->taskfailurearray[i];
	if (taskfail->task==task&&taskfail->index==index) {
	  int start=0;
	  while(start<taskfail->numoptionaltaskdescriptors) {
	    struct taskdescriptor *currtask=NULL;
	    int currindex;
	    int totallength=0;
	    int *enterflags;
	    int numenterflags, offset;
	    struct parameterwrapper *pw;
	    for(j=start; j<taskfail->numoptionaltaskdescriptors; j++) {
	      struct optionaltaskdescriptor *otd=taskfail->optionaltaskdescriptorarray[j];
	      if(currtask==NULL) {
		currtask=otd->task;
		currindex=otd->index;
	      } else if (currtask!=otd->task||currindex!=otd->index)
		break;
	      totallength+=otd->numenterflags; //1 is to store the lengths
	    }
	    pw=currtask->descriptorarray[currindex]->queue;
	    numenterflags=j-start;
	    enterflags=RUNMALLOC((totallength+numenterflags)*sizeof(int));

	    offset=0;
	    for(start; start<j; start++) {
	      struct optionaltaskdescriptor *otd=taskfail->optionaltaskdescriptorarray[start];
	      enterflags[offset++]=otd->numenterflags;
	      memcpy(&enterflags[offset], otd->enterflags, otd->numenterflags*sizeof(int));
	      offset+=otd->numenterflags;
	    }
	    //Enqueue this one
	    if (enqueuetasks(pw, currobj->flagptr, currobj, enterflags, numenterflags))
	      currobj->flagptr=pw;
	  }
	}
      }
    } else {
      /* Failed in failed state */
      int i;
      int offset=0;
      for(i=0; i<numfailedfses; i++) {
	int numfses=failedfses[offset++];
	int j;
	struct failedtasklist *andlist=NULL;
	for(j=0; j<numfses; j++) {
	  int flagstate=failedfses[offset++];
	  struct failedtasklist *currlist=processfailstate(classwrapper, task, index, currobj, flagstate);
	  if (andlist==NULL)
	    andlist=currlist;
	  else
	    mergefailedlists(&andlist, currlist);
	}
	enqueuelist(currobj, andlist);
      }
    }
  } else {
    /* No failure, but we are in a failed state */
    struct parameterwrapper *flagptr=(struct parameterwrapper *)currobj->flagptr;

    /*Remove object from all queues */
    while(flagptr!=NULL) {
      struct parameterwrapper *next;
      int UNUSED, UNUSED2;
      int * enterflags;
      ObjectHashget(flagptr->objectset, (int) currobj, (int *) &next, (int *) &enterflags, &UNUSED, &UNUSED2);
      ObjectHashremove(flagptr->objectset, (int)currobj);
      if (enterflags!=NULL)
	free(enterflags);
      flagptr=next;
    }

    /* Failed in failed state */
    int i;
    int offset=0;
    for(i=0; i<currobj->numfses; i++) {
      int numfses=currobj->fses[offset++];
      int j;
      struct failedtasklist *andlist=NULL;
      for(j=0; j<numfses; j++) {
	int flagstate=currobj->fses[offset++];
	struct failedtasklist *currlist=processnormfailstate(classwrapper, currobj, flagstate);
	if (andlist==NULL)
	  andlist=currlist;
	else
	  mergefailedlists(&andlist, currlist);
      }
      enqueuelist(currobj, andlist);
    }
  }
}


#endif

int enqueuetasks(struct parameterwrapper *parameter, struct parameterwrapper *prevptr, struct ___Object___ *ptr, int * enterflags, int numenterflags) {
  void * taskpointerarray[MAXTASKPARAMS];
#ifdef OPTIONAL
  int failed[MAXTASKPARAMS];
#endif
  int j;
  int numparams=parameter->task->numParameters;
  int numiterators=parameter->task->numTotal-1;
  int retval=1;
  int addnormal=1;
  int adderror=1;

  struct taskdescriptor * task=parameter->task;

#ifdef OPTIONAL
  if (ObjectHashcontainskey(parameter->objectset, (int) ptr)) {
    /* The object is already here...or it with the existing item */
    int * oldflags;
    int oldnumflags;
    int oldptr;
    int oldstatus;
    int *mergedflags;
    ObjectHashget(parameter->objectset, (int) ptr, &oldptr, (int *) &oldflags, &oldnumflags, &oldstatus);
    mergedflags=domergeor(oldflags, oldnumflags, enterflags, numenterflags);
    ObjectHashupdate(parameter->objectset, (int) ptr, oldptr, mergedflags, oldnumflags+numenterflags, oldstatus||(enterflags==NULL));

    RUNFREE(oldflags);
    RUNFREE(enterflags);

    //only add if truly needed
    if (oldstatus)
      addnormal=0;
    if (oldnumflags>0)
      adderror=0;

    retval=0;
  } else {
#endif
  ObjectHashadd(parameter->objectset, (int) ptr, (int) prevptr, (int) enterflags, numenterflags, enterflags==NULL);  //this add the object to parameterwrapper
#ifdef OPTIONAL
}
#endif

  /* Add enqueued object to parameter vector */
  taskpointerarray[parameter->slot]=ptr;
#ifdef OPTIONAL
  failed[parameter->slot]=(enterflags!=NULL);
#endif

  /* Reset iterators */
  for(j=0; j<numiterators; j++) {
    toiReset(&parameter->iterators[j]);
  }

  /* Find initial state */
  for(j=0; j<numiterators; j++) {
backtrackinit:
    if(toiHasNext(&parameter->iterators[j], taskpointerarray OPTARG(failed))) {
      toiNext(&parameter->iterators[j], taskpointerarray OPTARG(failed));
    } else if (j>0) {
      /* Need to backtrack */
      toiReset(&parameter->iterators[j]);
      j--;
      goto backtrackinit;
    } else {
      /* Nothing to enqueue */
      return retval;
    }
  }


  while(1) {
    /* Enqueue current state */
    int launch = 0;
    struct taskparamdescriptor *tpd=RUNMALLOC(sizeof(struct taskparamdescriptor));
    tpd->task=task;
    tpd->numParameters=numiterators+1;
    tpd->parameterArray=RUNMALLOC(sizeof(void *)*(numiterators+1));
#ifdef OPTIONAL
    tpd->failed=RUNMALLOC(sizeof(int)*(numiterators+1));
#endif
    for(j=0; j<=numiterators; j++) {
      tpd->parameterArray[j]=taskpointerarray[j]; //store the actual parameters
#ifdef OPTIONAL
      tpd->failed[j]=failed[j];
      if (failed[j]!=0&&failed[j]!=1) {
	printf("BAD\n");
      }
#endif
    }
    /* Enqueue task */
    if ((!gencontains(failedtasks, tpd)&&!gencontains(activetasks,tpd))) {
      genputtable(activetasks, tpd, tpd);
    } else {
      RUNFREE(tpd->parameterArray);
#ifdef OPTIONAL
      RUNFREE(tpd->failed);
#endif
      RUNFREE(tpd);
    }

    /* This loop iterates to the next parameter combination */
    if (numiterators==0)
      return retval;

    for(j=numiterators-1; j<numiterators; j++) {
backtrackinc:
      if(toiHasNext(&parameter->iterators[j], taskpointerarray OPTARG(failed))) {
	toiNext(&parameter->iterators[j], taskpointerarray OPTARG(failed));
      } else if (j>0) {
	/* Need to backtrack */
	toiReset(&parameter->iterators[j]);
	j--;
	goto backtrackinc;
      } else {
	/* Nothing more to enqueue */
	return retval;
      }
    }
  }
  return retval;
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

#ifdef OPTIONAL
int * fsescopy(int *src, int len) {
  int *dst;
  if (src==NULL)
    return NULL;
  dst=RUNMALLOC(len*sizeof(int));
  memcpy(dst, src, len*sizeof(int));
  return dst;
}
#endif

void executetasks() {
  void * taskpointerarray[MAXTASKPARAMS+OFFSET];
#ifdef OPTIONAL
  int * fsesarray[MAXTASKPARAMS];
  int * oldfsesarray[MAXTASKPARAMS];
  int numfsesarray[MAXTASKPARAMS];
#endif

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
	for(fd=0; fd<maxreadfd; fd++) {
	  if (FD_ISSET(fd, &tmpreadfds)) {
	    /* Set ready flag on object */
	    void * objptr;
	    //	    printf("Setting fd %d\n",fd);
	    if (RuntimeHashget(fdtoobject, fd,(int *) &objptr)) {
	      if(intflagorand(objptr,1,0xFFFFFFFF)) { /* Set the first flag to 1 */
		enqueueObject(objptr);
	      }
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
#ifdef OPTIONAL
	RUNFREE(currtpd->failed);
#endif
	RUNFREE(currtpd);
	goto newtask;
      }
      int numparams=currtpd->task->numParameters;
      int numtotal=currtpd->task->numTotal;

      /* Make sure that the parameters are still in the queues */
      for(i=0; i<numparams; i++) {
	void * parameter=currtpd->parameterArray[i];
	struct parameterdescriptor * pd=currtpd->task->descriptorarray[i];
	struct parameterwrapper *pw=(struct parameterwrapper *) pd->queue;
	int j;
	/* Check that object is still in queue */
#ifdef OPTIONAL
	{
	  int UNUSED, UNUSED2;
	  int *flags;
	  int numflags, isnonfailed;
	  int failed=currtpd->failed[i];
	  if (!ObjectHashget(pw->objectset, (int) parameter, &UNUSED, (int *) &flags, &numflags, &isnonfailed)) {
	    RUNFREE(currtpd->parameterArray);
	    RUNFREE(currtpd->failed);
	    RUNFREE(currtpd);
	    goto newtask;
	  } else {
	    if (failed&&(flags!=NULL)) {
	      //Failed parameter
	      fsesarray[i]=flags;
	      numfsesarray[i]=numflags;
	    } else if (!failed && isnonfailed) {
	      //Non-failed parameter
	      fsesarray[i]=NULL;
	      numfsesarray[i]=0;
	    } else {
	      RUNFREE(currtpd->parameterArray);
	      RUNFREE(currtpd->failed);
	      RUNFREE(currtpd);
	      goto newtask;
	    }
	  }
	}
#else
	{
	  if (!ObjectHashcontainskey(pw->objectset, (int) parameter)) {
	    RUNFREE(currtpd->parameterArray);
	    RUNFREE(currtpd);
	    goto newtask;
	  }
	}
#endif
parameterpresent:
	;
	/* Check that object still has necessary tags */
	for(j=0; j<pd->numbertags; j++) {
	  int slotid=pd->tagarray[2*j]+numparams;
	  struct ___TagDescriptor___ *tagd=currtpd->parameterArray[slotid];
	  if (!containstag(parameter, tagd)) {
	    RUNFREE(currtpd->parameterArray);
#ifdef OPTIONAL
	    RUNFREE(currtpd->failed);
#endif
	    RUNFREE(currtpd);
	    goto newtask;
	  }
	}

	taskpointerarray[i+OFFSET]=parameter;
      }
      /* Copy the tags */
      for(; i<numtotal; i++) {
	taskpointerarray[i+OFFSET]=currtpd->parameterArray[i];
      }

      {
	/* Checkpoint the state */
	forward=cCreate(256, 0.4);
	reverse=cCreate(256, 0.4);
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
	  for(counter=0; counter<currtpd->task->numParameters; counter++) {
	    //enqueue as failed
	    enqueueoptional(currtpd->parameterArray[counter], numfsesarray[counter], fsesarray[counter], currtpd->task, counter);

	    //free fses copies
	    if (fsesarray[counter]!=NULL)
	      RUNFREE(fsesarray[counter]);
	  }
#endif
	  cDelete(forward);
	  cDelete(reverse);
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
	                                                                    ((int *)taskpointerarray)[0]=currtpd->numParameters;
	  taskpointerarray[1]=NULL;
#endif
#ifdef OPTIONAL
	  //get the task flags set
	  for(i=0; i<numparams; i++) {
	    oldfsesarray[i]=((struct ___Object___ *)taskpointerarray[i+OFFSET])->fses;
	    fsesarray[i]=fsescopy(fsesarray[i], numfsesarray[i]);
	    ((struct ___Object___ *)taskpointerarray[i+OFFSET])->fses=fsesarray[i];
	  }
#endif
	  if(debugtask) {
	    printf("ENTER %s count=%d\n",currtpd->task->name, (instaccum-instructioncount));
	    ((void(*) (void **))currtpd->task->taskptr)(taskpointerarray);
	    printf("EXIT %s count=%d\n",currtpd->task->name, (instaccum-instructioncount));
	  } else
	    ((void(*) (void **))currtpd->task->taskptr)(taskpointerarray);

#ifdef OPTIONAL
	  for(i=0; i<numparams; i++) {
	    //free old fses
	    if(oldfsesarray[i]!=NULL)
	      RUNFREE(oldfsesarray[i]);
	  }
#endif

	  cDelete(forward);
	  cDelete(reverse);
	  freemalloc();
	  // Free up task parameter descriptor
	  RUNFREE(currtpd->parameterArray);
#ifdef OPTIONAL
	  RUNFREE(currtpd->failed);
#endif
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

  for(i=0; i<pd->numbertags; i++) {
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

  for(i=0; i<pd->numbertags; i++) {
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
  for(i=0; i<MAXTASKPARAMS; i++) statusarray[i]=0;

  statusarray[index]=1; /* Initial parameter */
  /* Process tags for initial iterator */

  processtags(task->descriptorarray[index], index, parameter, &iteratorcount, statusarray, numparams);

  while(1) {
loopstart:
    /* Check for objects with existing tags */
    for(i=0; i<numparams; i++) {
      if (statusarray[i]==0) {
	struct parameterdescriptor *pd=task->descriptorarray[i];
	int j;
	for(j=0; j<pd->numbertags; j++) {
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

    for(i=0; i<numparams; i++) {
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

    for(i=0; i<numparams; i++) {
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

void printdebug() {
  int i;
  int j;
  for(i=0; i<numtasks; i++) {
    struct taskdescriptor * task=taskarray[i];
    printf("%s\n", task->name);
    for(j=0; j<task->numParameters; j++) {
      struct parameterdescriptor *param=task->descriptorarray[j];
      struct parameterwrapper *parameter=param->queue;
      struct ObjectHash * set=parameter->objectset;
      struct ObjectIterator objit;
      printf("  Parameter %d\n", j);
      ObjectHashiterator(set, &objit);
      while(ObjhasNext(&objit)) {
	struct ___Object___ * obj=(struct ___Object___ *)Objkey(&objit);
	struct ___Object___ * tagptr=obj->___tags___;
	int nonfailed=Objdata4(&objit);
	int numflags=Objdata3(&objit);
	int flags=Objdata2(&objit);
	Objnext(&objit);
	printf("    Contains %lx\n", obj);
	printf("      flag=%d\n", obj->flag);
#ifdef OPTIONAL
	printf("      flagsstored=%x\n",flags);
	printf("      numflags=%d\n", numflags);
	printf("      nonfailed=%d\n",nonfailed);
#endif
	if (tagptr==NULL) {
	} else if (tagptr->type==TAGTYPE) {
	  printf("      tag=%lx\n",tagptr);
	} else {
	  int tagindex=0;
	  struct ArrayObject *ao=(struct ArrayObject *)tagptr;
	  for(; tagindex<ao->___cachedCode___; tagindex++) {
	    printf("      tag=%lx\n",ARRAYGET(ao, struct ___TagDescriptor___*, tagindex));
	  }
	}
      }
    }
  }
}


/* This function processes the task information to create queues for
   each parameter type. */

void processtasks() {
  int i;
  for(i=0; i<numtasks; i++) {
    struct taskdescriptor * task=taskarray[i];
    int j;

    for(j=0; j<task->numParameters; j++) {
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
      parameter->slot=j;
      /* Link new queue in */
      while((*ptr)!=NULL)
	ptr=&((*ptr)->next);
      (*ptr)=parameter;
    }

    /* Build iterators for parameters */
    for(j=0; j<task->numParameters; j++) {
      struct parameterdescriptor *param=task->descriptorarray[j];
      struct parameterwrapper *parameter=param->queue;
      builditerators(task, j, parameter);
    }
  }
}

void toiReset(struct tagobjectiterator * it) {
  if (it->istag) {
    it->tagobjindex=0;
  } else if (it->numtags>0) {
    it->tagobjindex=0;
#ifdef OPTIONAL
    it->failedstate=0;
#endif
  } else {
    ObjectHashiterator(it->objectset, &it->it);
#ifdef OPTIONAL
    it->failedstate=0;
#endif
  }
}

int toiHasNext(struct tagobjectiterator *it, void ** objectarray OPTARG(int * failed)) {
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
      for(; tagindex<ao->___cachedCode___; tagindex++) {
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
      for(i=1; i<it->numtags; i++) {
	struct ___TagDescriptor___ *tag2=objectarray[it->tagbindings[i]];
	if (!containstag(objptr,tag2))
	  return 0;
      }
#ifdef OPTIONAL
      if (it->failedstate==1) {
	int UNUSED, UNUSED2;
	int * flags;
	int isnonfailed;
	ObjectHashget(it->objectset, (int) objptr, &UNUSED, (int *) &flags, &UNUSED2, &isnonfailed);
	if (flags!=NULL) {
	  return 1;
	} else {
	  it->tagobjindex++;
	  it->failedstate=0;
	  return 0;
	}
      } else {
	int UNUSED, UNUSED2;
	int * flags;
	int isnonfailed;
	ObjectHashget(it->objectset, (int) objptr, &UNUSED, (int *) &flags, &UNUSED2, &isnonfailed);
	if (!isnonfailed) {
	  it->failedstate=1;
	}
	return 1;
      }
#endif
      return 1;
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) objptr;
      int tagindex;
      int i;
#ifdef OPTIONAL
      if (it->failedstate==1) {
	int UNUSED, UNUSED2;
	int * flags;
	int isnonfailed;
	struct ___Object___ *objptr=ARRAYGET(ao, struct ___Object___*, it->tagobjindex);
	ObjectHashget(it->objectset, (int) objptr, &UNUSED, (int *) &flags, &UNUSED2, &isnonfailed);
	if (flags!=NULL) {
	  return 1;
	} else {
	  it->failedstate=0;
	  it->tagobjindex++;
	}
      }
#endif
      for(tagindex=it->tagobjindex; tagindex<ao->___cachedCode___; tagindex++) {
	struct ___Object___ *objptr=ARRAYGET(ao, struct ___Object___*, tagindex);
	if (!ObjectHashcontainskey(it->objectset, (int) objptr))
	  continue;
	for(i=1; i<it->numtags; i++) {
	  struct ___TagDescriptor___ *tag2=objectarray[it->tagbindings[i]];
	  if (!containstag(objptr,tag2))
	    goto nexttag;
	}
#ifdef OPTIONAL
	{
	  int UNUSED, UNUSED2;
	  int flags, isnonfailed;
	  struct ___Object___ *objptr=ARRAYGET(ao, struct ___Object___*, tagindex);
	  ObjectHashget(it->objectset, (int) objptr, &UNUSED, &flags, &UNUSED2, &isnonfailed);
	  if (!isnonfailed) {
	    it->failedstate=1;
	  }
	}
#endif
	it->tagobjindex=tagindex;
	return 1;
nexttag:
	;
      }
      it->tagobjindex=tagindex;
      return 0;
    }
  } else {
#ifdef OPTIONAL
    if (it->failedstate==1) {
      if (Objdata2(&it->it))
	return 1;
      else {
	it->failedstate=0;
	Objnext(&it->it);
      }
    }
    if (ObjhasNext(&it->it)) {
      if (!Objdata4(&it->it)) {
	//failed state only
	it->failedstate=1;
      }
      return 1;
    } else
      return 0;
#else
    return ObjhasNext(&it->it);
#endif
  }
}

int containstag(struct ___Object___ *ptr, struct ___TagDescriptor___ *tag) {
  int j;
  struct ___Object___ * objptr=tag->flagptr;
  if (objptr->type==OBJECTARRAYTYPE) {
    struct ArrayObject *ao=(struct ArrayObject *)objptr;
    for(j=0; j<ao->___cachedCode___; j++) {
      if (ptr==ARRAYGET(ao, struct ___Object___*, j))
	return 1;
    }
    return 0;
  } else
    return objptr==ptr;
}

void toiNext(struct tagobjectiterator *it, void ** objectarray OPTARG(int * failed)) {
  /* hasNext has all of the intelligence */
  if(it->istag) {
    /* Iterate tag */
    /* Get object with tags */
    struct ___Object___ *obj=objectarray[it->tagobjectslot];
    struct ___Object___ *tagptr=obj->___tags___;
#ifdef OPTIONAL
    failed[it->slot]=0; //have to set it to something
#endif
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
#ifdef OPTIONAL
      failed[it->slot]=it->failedstate;
      objectarray[it->slot]=objptr;
      if (it->failedstate==0) {
	it->failedstate=1;
      } else {
	it->failedstate=0;
	it->tagobjindex++;
      }
#else
      it->tagobjindex++;
      objectarray[it->slot]=objptr;
#endif
    } else {
      struct ArrayObject *ao=(struct ArrayObject *) objptr;
#ifdef OPTIONAL
      failed[it->slot]=it->failedstate;
      objectarray[it->slot]=ARRAYGET(ao, struct ___Object___ *, it->tagobjindex);
      if (it->failedstate==0) {
	it->failedstate=1;
      } else {
	it->failedstate=0;
	it->tagobjindex++;
      }
#else
      objectarray[it->slot]=ARRAYGET(ao, struct ___Object___ *, it->tagobjindex++);
#endif
    }
  } else {
    /* Iterate object */
    void * tmpp = (void *) Objkey(&it->it);
    objectarray[it->slot]=tmpp;
#ifdef OPTIONAL
    failed[it->slot]=it->failedstate;
    if (it->failedstate==0) {
      it->failedstate=1;
    } else {
      it->failedstate=0;
      Objnext(&it->it);
    }
#else
    Objnext(&it->it);
#endif
  }
}
#endif
