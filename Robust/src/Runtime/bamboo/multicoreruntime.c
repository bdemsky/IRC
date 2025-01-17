#ifdef MULTICORE
#include "runtime.h"
#include "multicoreruntime.h"
#include "methodheaders.h"
#include "multicoregarbage.h"
#ifdef PMC_GC
#include "multicoregcprofile.h"
#include "multicoregc.h"
#include "pmc_garbage.h"
#endif
#include "multicore_arch.h"
#include <stdio.h>
#ifdef PERFCOUNT
#include "bme_perf_counter.h"
#endif
#ifdef MEMPERFCOUNT
#include "memprof.h"
#endif
#ifdef INPUTFILE
#include "InputFileArrays.h"
#endif


extern int classsize[];
extern int typearray[];
extern int typearray2[];
extern int* supertypes[];

#ifdef TASK
extern struct genhashtable * activetasks;
#endif

#ifdef MGC
int corenum = 0;
#endif

int instanceofif(int otype, int type) {
  if(otype == type) {
    return 1;
  }
  if(otype == -1) {
    return 0;
  }
  int num = supertypes[otype][0];
  for(int i = 1; i < num + 1; i++) {
    int t = supertypes[otype][i];
    if(instanceofif(t, type) == 1) {
      return 1;
    }
  }
  return 0;
}

int instanceof(struct ___Object___ *ptr, int type) {
  if(ptr == NULL) {
    return 0;
  }
  int i=ptr->type;
  if(instanceofif(i, type) == 1) {
    return 1;
  }
  if (i>NUMCLASSES) {
    do {
      if (i==type)
        return 1;
      i=typearray2[i-NUMCLASSES];
    } while(i!=-1);
  }
  return 0;
}

void initializeexithandler() {
}

/* This function inject failures */
void injectinstructionfailure() {
  // not supported in MULTICORE version
  return;
}

#ifdef D___Double______nativeparsedouble____L___String___
double CALL01(___Double______nativeparsedouble____L___String___,
              struct ___String___ * ___str___) {
  int length=VAR(___str___)->___count___;
  int maxlength=(length>60) ? 60 : length;
  char str[maxlength+1];
  struct ArrayObject * chararray=VAR(___str___)->___value___;
  int i;
  int offset=VAR(___str___)->___offset___;
  for(i=0; i<maxlength; i++) {
    str[i]=
      ((short *)(((char *)&chararray->___length___)+sizeof(int)))[i+offset];
  }
  str[i]=0;
  double d=0.0; //atof(str); TODO Unimplemented nativeparsedoulbe
  return d;
}
#endif

#ifdef D___Double______nativeparsedouble_____AR_B_I_I 
double CALL23(___Double______nativeparsedouble_____AR_B_I_I, 
              int start, 
              int length,
              int start,
              int length,
              struct ArrayObject * ___str___) {
  int maxlength=(length>60)?60:length;
  char str[maxlength+1];
  struct ArrayObject * bytearray=VAR(___str___);
  int i;
  for(i=0; i<maxlength; i++) {
    str[i]=(((char *)&bytearray->___length___)+sizeof(int))[i+start];
  }
  str[i]=0;
  double d=0.0; //atof(str); TODO Unimplemented nativeparsedouble
  return d;
}
#endif

typedef union jvalue {
  bool z;
  char    c;
  short   s;
  int     i;
  long long    j;
  float   f;
  double  d;
} jvalue;

#ifdef D___Double______doubleToRawLongBits____D 
long long CALL11(___Double______doubleToRawLongBits____D, 
                 double ___value___, 
                 double ___value___) {
  jvalue val;
  val.d = ___value___;

#if defined(__IEEE_BYTES_LITTLE_ENDIAN)
  /* On little endian ARM processors when using FPA, word order of
     doubles is still big endian. So take that into account here. When
     using VFP, word order of doubles follows byte order. */
#define SWAP_DOUBLE(a)    (((a) << 32) | (((a) >> 32) & 0x00000000ffffffff))
  val.j = SWAP_DOUBLE(val.j);
#endif

  return val.j;
}
#endif

#ifdef D___Double______longBitsToDouble____J 
double CALL11(___Double______longBitsToDouble____J, 
              long long ___bits___, 
              long long ___bits___) {
  jvalue val;
  val.j = ___bits___;

#if defined(__IEEE_BYTES_LITTLE_ENDIAN)
#ifndef SWAP_DOUBLE
#define SWAP_DOUBLE(a)    (((a) << 32) | (((a) >> 32) & 0x00000000ffffffff))
#endif
  val.j = SWAP_DOUBLE(val.j);
#endif

  return val.d;
}
#endif

#ifdef D___String______convertdoubletochar____D__AR_C
int CALL12(___String______convertdoubletochar____D__AR_C, 
           double ___val___, 
           double ___val___, 
           struct ArrayObject * ___chararray___) {
  int length=VAR(___chararray___)->___length___;
  char str[length];
  int i;
  int num=snprintf(str, length, "%f",___val___);
  if (num>=length)
    num=length-1;
  for(i=0; i<length; i++) {
    ((short *)(((char *)&VAR(___chararray___)->___length___)+sizeof(int)))[i]=
      (short)str[i];
  }
  return num;
}
#endif

#ifdef D___System______deepArrayCopy____L___Object____L___Object___
void deepArrayCopy(struct ___Object___ * dst, 
                   struct ___Object___ * src) {
  int dsttype=((int *)dst)[0];
  int srctype=((int *)src)[0];
  if (dsttype<NUMCLASSES||srctype<NUMCLASSES||srctype!=dsttype)
    return;
  struct ArrayObject *aodst=(struct ArrayObject *)dst;
  struct ArrayObject *aosrc=(struct ArrayObject *)src;
  int dstlength=aodst->___length___;
  int srclength=aosrc->___length___;
  if (dstlength!=srclength)
    return;
  unsigned INTPTR *pointer=pointerarray[srctype];
  if (pointer==0) {
    int elementsize=classsize[srctype];
    int size=srclength*elementsize;
    //primitives
    memcpy(((char *)&aodst->___length___)+sizeof(int) , 
        ((char *)&aosrc->___length___)+sizeof(int), size);
  } else {
    //objects
    int i;
    for(i=0;i<srclength;i++) {
      struct ___Object___ * ptr=
        ((struct ___Object___**)(((char*)&aosrc->___length___)+sizeof(int)))[i];
      int ptrtype=((int *)ptr)[0];
      if (ptrtype>=NUMCLASSES) {
        struct ___Object___ * dstptr=((struct ___Object___**)
            (((char*)&aodst->___length___)+sizeof(int)))[i];
        deepArrayCopy(dstptr,ptr);
      } else {
        //hit an object
        ((struct ___Object___ **)
         (((char*) &aodst->___length___)+sizeof(int)))[i]=ptr;
      }
    }
  }
}

void CALL02(___System______deepArrayCopy____L___Object____L___Object___, 
            struct ___Object___ * ___dst___, 
            struct ___Object___ * ___src___) {
  deepArrayCopy(VAR(___dst___), VAR(___src___));
}
#endif

#ifdef D___System______arraycopy____L___Object____I_L___Object____I_I
void arraycopy(struct ___Object___ *src, 
               int srcPos, 
               struct ___Object___ *dst, 
               int destPos, 
               int length) {
  int dsttype=((int *)dst)[0];
  int srctype=((int *)src)[0];

  //not an array or type mismatch
  if (dsttype<NUMCLASSES||srctype<NUMCLASSES/*||srctype!=dsttype*/)
    return;

  struct ArrayObject *aodst=(struct ArrayObject *)dst;
  struct ArrayObject *aosrc=(struct ArrayObject *)src;
  int dstlength=aodst->___length___;
  int srclength=aosrc->___length___;

  if (length<=0)
    return;
  if (srcPos+length>srclength)
    return;
  if (destPos+length>dstlength)
    return;

  unsigned INTPTR *pointer=pointerarray[srctype];
  if (pointer==0) {
    int elementsize=classsize[srctype];
    int size=length*elementsize;
    //primitives
    memcpy(((char *)&aodst->___length___)+sizeof(int)+destPos*elementsize, 
        ((char *)&aosrc->___length___)+sizeof(int)+srcPos*elementsize, size);
  } else {
    //objects
    int i;
    for(i=0;i<length;i++) {
      struct ___Object___ * ptr=((struct ___Object___**)
          (((char*)&aosrc->___length___)+sizeof(int)))[i+srcPos];
      int ptrtype=((int *)ptr)[0];
      //hit an object
      ((struct ___Object___ **)
       (((char*) &aodst->___length___)+sizeof(int)))[i+destPos]=ptr;
    }
  }
}

void CALL35(___System______arraycopy____L___Object____I_L___Object____I_I, 
            int ___srcPos___, 
            int ___destPos___, 
            int ___length___, 
            struct ___Object___ * ___src___, 
            int ___srcPos___, 
            struct ___Object___ * ___dst___, 
            int  ___destPos___, 
            int ___length___) {
  arraycopy(VAR(___src___), ___srcPos___, VAR(___dst___), ___destPos___, 
      ___length___);
}
#endif

#ifdef D___System______exit____I
void CALL11(___System______exit____I,
            int ___status___, 
            int ___status___) {
// gc_profile mode, output gc prfiling data
#if defined(MULTICORE_GC)||defined(PMC_GC)
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    BAMBOO_PRINT(BAMBOO_GET_EXE_TIME());
    BAMBOO_PRINT(0xbbbbbbbb);
    CACHEADAPT_DISABLE_TIMER();
    GC_OUTPUT_PROFILE_DATA();
#ifdef PERFCOUNT
    print_statistics();
#endif
    gc_outputProfileDataReadable();
    tprintf("FINISH_EXECUTION\n");
  }
#endif 
  BAMBOO_EXIT_APP(___status___);
}
#endif

#ifdef D___Vector______removeElement_____AR_L___Object____I_I
void CALL23(___Vector______removeElement_____AR_L___Object____I_I, 
            int ___index___, 
            int ___size___, 
            struct ArrayObject * ___array___, 
            int ___index___, 
            int ___size___) {
  char* offset=((char *)(&VAR(___array___)->___length___))
    +sizeof(unsigned int)+sizeof(void *)*___index___;
  memmove(offset, offset+sizeof(void *),
      (___size___-___index___-1)*sizeof(void *));
}
#endif

#ifdef D___System______printI____I
void CALL11(___System______printI____I,
            int ___status___, 
            int ___status___) {
  BAMBOO_PRINT(0x1111);
  BAMBOO_PRINT_REG(___status___);
}
#endif

#ifdef D___System______currentTimeMillis____
long long CALL00(___System______currentTimeMillis____) {
  //TilePro64 is 700mHz
  return ((unsigned long long)BAMBOO_GET_EXE_TIME())/700000;
}
#endif

#ifdef D___System______numGCs____
long long ___System______numGCs____(struct ___System______numGCs_____params * ___params___) {
#ifdef MULTICORE_GC
  return numGCs;
#else
  return 0;
#endif
}
#endif

#ifdef D___System______milliGcTime____
long long CALL00(___System______milliGcTime____) {
#ifdef MULTICORE_GC
  return GCtime/700000;
#else
  return 0;
#endif
}
#endif

#ifdef D___System______nanoTime____ 
long long CALL00(___System______nanoTime____) {
  //TilePro64 is 700mHz
  return ((unsigned long long)BAMBOO_GET_EXE_TIME())/700;
}
#endif

#ifdef D___System______setgcprofileflag____
void CALL00(___System______setgcprofileflag____) {
#ifdef GC_PROFILE
#ifdef MGC_SPEC
  extern volatile bool gc_profile_flag;
  gc_profile_flag = true;
#endif
#endif
}
#endif

#ifdef D___System______resetgcprofileflag____
void CALL00(___System______resetgcprofileflag____) {
#ifdef GC_PROFILE
#ifdef MGC_SPEC
  extern volatile bool gc_profile_flag;
  gc_profile_flag = false;
#endif
#endif
}
#endif

#ifdef D___System______gc____
void CALL00(___System______gc____) {
#ifdef MULTICORE_GC
  if(BAMBOO_NUM_OF_CORE == STARTUPCORE) {
    if(!gc_status_info.gcprocessing && !gcflag) {
      gcflag = true;
      gcprecheck = true;
      for(int i = 0; i < NUMCORESACTIVE; i++) {
        // reuse the gcnumsendobjs & gcnumreceiveobjs
        gcnumsendobjs[0][i] = 0;
        gcnumreceiveobjs[0][i] = 0;
      }
      for(int i = 0; i < NUMCORES4GC; i++) {
        if(i != STARTUPCORE) {
          send_msg_1(i,GCSTARTPRE);
        }
      }
    }
  } else {
    // send msg to the startup core to start gc
    send_msg_1(STARTUPCORE, GCINVOKE);
  }
#endif
}
#endif

#ifdef D___System______printString____L___String___
void CALL01(___System______printString____L___String___, struct ___String___ * ___s___) {
#if defined(MGC)&&defined(TILERA_BME)
  struct ArrayObject * chararray=VAR(___s___)->___value___;
  int i;
  int offset=VAR(___s___)->___offset___;
  tprintf("");
  for(i=0; i<VAR(___s___)->___count___; i++) {
    short sc=
      ((short *)(((char *)&chararray->___length___)+sizeof(int)))[i+offset];
    printf("%c", sc);
  }
#endif // MGC
}
#endif

#ifdef INPUTFILE
#ifdef D___Scanner______nextInt____ 
int CALL01(___Scanner______nextInt____, struct ___Scanner___ * ___this___) {
  int pos = VAR(___this___)->___currentpos___;
  int isHighbits = VAR(___this___)->___isHighbits___;
  int value = nextInt(VAR(___this___)->___fd___, &pos, &isHighbits);
  VAR(___this___)->___currentpos___ = pos;
  VAR(___this___)->___isHighbits___ = isHighbits;
  return value;
}
#endif

#ifdef D___Scanner______nextDouble____ 
double CALL01(___Scanner______nextDouble____, struct ___Scanner___ * ___this___) {
  int pos = VAR(___this___)->___currentpos___;
  int isHighbits = VAR(___this___)->___isHighbits___;
  double value = nextDouble(VAR(___this___)->___fd___, &pos, &isHighbits);
  VAR(___this___)->___currentpos___ = pos;
  VAR(___this___)->___isHighbits___ = isHighbits;
  return value;
}
#endif
#endif

/* Object allocation function */

#if defined(MULTICORE_GC)||defined(PMC_GC)
void * allocate_new(void * ptr, 
                    int type) {
  struct ___Object___ * v=
    (struct ___Object___*)FREEMALLOC((struct garbagelist*) ptr,classsize[type]);
  v->type=type;
#ifdef TASK
  v->version = 0;
  v->lock = NULL;
  v->lockcount = 0;
#endif
  initlock(v);
  return v;
}

/* Array allocation function */

struct ArrayObject * allocate_newarray(void * ptr, 
                                       int type, 
                                       int length) {
  struct ArrayObject * v=(struct ArrayObject *)FREEMALLOC(
      (struct garbagelist*)ptr,
      sizeof(struct ArrayObject)+length*classsize[type]);
  v->type=type;
#ifdef TASK
  v->version = 0;
  v->lock = NULL;
#endif
  if (length<0) {
    return NULL;
  }
  v->___length___=length;
  initlock((struct ___Object___ *)v);
  return v;
}

#else
void * allocate_new(int type) {
  struct ___Object___ * v=FREEMALLOC(classsize[type]);
  v->type=type;
#ifdef TASK
  v->version = 0;
  v->lock = NULL;
#endif
  initlock(v);
  return v;
}

/* Array allocation function */

struct ArrayObject * allocate_newarray(int type, 
                                       int length) {
  struct ArrayObject * v=FREEMALLOC(
      sizeof(struct ArrayObject)+length*classsize[type]);
  v->type=type;
#ifdef TASK
  v->version = 0;
  v->lock = NULL;
#endif
  v->___length___=length;
  initlock((struct ___Object___ *) v);
  return v;
}
#endif

/* Converts C character arrays into Java strings */
#if defined(MULTICORE_GC)||defined(PMC_GC)
__attribute__((malloc)) struct ___String___ * NewStringShort(void * ptr, 
                                                             const short *str,
                                                             int length) {
#else
__attribute__((malloc)) struct ___String___ * NewStringShort(const short *str,
                                                             int length) {
#endif
  int i;
#if defined(MULTICORE_GC)||defined(PMC_GC)
  struct ArrayObject * chararray=
    allocate_newarray((struct garbagelist *)ptr, CHARARRAYTYPE, length);
  INTPTR ptrarray[]={1, (INTPTR) ptr, (INTPTR) chararray};
  struct ___String___ * strobj=
    allocate_new((struct garbagelist *) &ptrarray, STRINGTYPE);
  chararray=(struct ArrayObject *) ptrarray[2];
#else
  struct ArrayObject * chararray=allocate_newarray(CHARARRAYTYPE, length);
  struct ___String___ * strobj=allocate_new(STRINGTYPE);
#endif
  strobj->___value___=chararray;
  strobj->___count___=length;
  strobj->___offset___=0;

  for(i=0; i<length; i++) {
    ((short *)(((char *)&chararray->___length___)+sizeof(int)))[i]=str[i];
  }
  return strobj;
}

/* Converts C character arrays into Java strings */
#if defined(MULTICORE_GC)||defined(PMC_GC)
struct ___String___ * NewString(void * ptr, 
                                const char *str,
                                int length) {
#else
struct ___String___ * NewString(const char *str,
                                int length) {
#endif
  int i;
#if defined(MULTICORE_GC)||defined(PMC_GC)
  struct ArrayObject * chararray=
    allocate_newarray((struct garbagelist *)ptr, CHARARRAYTYPE, length);
  int ptrarray[]={1, (int) ptr, (int) chararray};
  struct ___String___ * strobj=
    allocate_new((struct garbagelist *) &ptrarray, STRINGTYPE);
  chararray=(struct ArrayObject *) ptrarray[2];
#else
  struct ArrayObject * chararray=allocate_newarray(CHARARRAYTYPE, length);
  struct ___String___ * strobj=allocate_new(STRINGTYPE);
#endif
  strobj->___value___=chararray;
  strobj->___count___=length;
  strobj->___offset___=0;

  for(i=0; i<length; i++) {
    ((short*)(((char*)&chararray->___length___)+sizeof(int)))[i]=(short)str[i];
  }
  return strobj;
}

/* Generated code calls this if we fail a bounds check */

 void failedboundschk(int num, int index, struct ArrayObject * ao) {
#ifndef TASK
  printf("Array out of bounds at line %u with index %u of object %x with lengt\
h %u\n", num, index, ao, ao->___length___);
#ifdef THREADS
  threadexit();
#elif defined MGC
  BAMBOO_EXIT();
#else
  exit(-1);
#endif
#else
#ifndef MULTICORE
  printf("Array out of bounds\n");
  longjmp(error_handler,2);
#else
  BAMBOO_EXIT();
#endif
#endif
}

/* Generated code calls this if we fail null ptr chk */
void failednullptr(void * ptr) {
#if defined(MULTICORE_GC)||defined(PMC_GC)
#ifndef RAW
  //print out current stack
  int i,j;
  j = 0;
  struct garbagelist * stackptr = (struct garbagelist *)ptr;
  while(stackptr!=NULL) {
    tprintf("Stack %d: \n\t", j);
    for(i=0; i<stackptr->size; i++) {
      if(stackptr->array[i] != NULL) {
        tprintf("%x, ", stackptr->array[i]);
      } else {
        tprintf("NULL, ");
      }
    }
    tprintf("\n");
    stackptr=stackptr->next;
  }
#endif
#endif
#ifndef TASK
  printf("NULL ptr\n");
#ifdef THREADS
  threadexit();
#elif defined MGC
  BAMBOO_EXIT();
#else
  exit(-1);
#endif
#else
#ifndef MULTICORE
  printf("NULL ptr\n");
  longjmp(error_handler,2);
#else
  BAMBOO_EXIT();
#endif
#endif
}

/* Abort task call */
void abort_task() {
#ifdef TASK
#ifndef MULTICORE
  printf("Aborting\n");
  longjmp(error_handler,4);
#endif
#else
  printf("Aborting\n");
  exit(-1);
#endif
}

void initruntimedata() {
  // initialize the arrays
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    // startup core to initialize corestatus[]
    for(int i = 0; i < NUMCORESACTIVE; ++i) {
      corestatus[i] = 1;
      numsendobjs[i] = 0;
      numreceiveobjs[i] = 0;
    } 
    numconfirm = 0;
    waitconfirm = false;
  }

  busystatus = true;
  self_numsendobjs = 0;
  self_numreceiveobjs = 0;

  for(int i = 0; i < BAMBOO_MSG_BUF_LENGTH; ++i) {
    msgdata[i] = -1;
  }
  msgdataindex = 0;
  msgdatalast = 0;
  //msglength = BAMBOO_MSG_BUF_LENGTH;
  msgdatafull = false;
  for(int i = 0; i < BAMBOO_OUT_BUF_LENGTH; ++i) {
    outmsgdata[i] = -1;
  }
  outmsgindex = 0;
  outmsglast = 0;
  outmsgleft = 0;
  isMsgHanging = false;
  
  smemflag = true;
  bamboo_cur_msp = NULL;
  bamboo_smem_size = 0;
#ifndef INTERRUPT
  reside = false;
#endif

  INITMULTICOREGCDATA();

#ifdef MGC
  initializethreads();
  bamboo_current_thread = NULL;
#endif // MGC

  INITTASKDATA();
}

void disruntimedata() {
  DISMULTICOREGCDATA();
  DISTASKDATA();
  BAMBOO_LOCAL_MEM_CLOSE();
  BAMBOO_SHARE_MEM_CLOSE();
}

void recordtotalexetime() {
#ifdef USEIO
  totalexetime = BAMBOO_GET_EXE_TIME()-bamboo_start_time;
#else // USEIO
  unsigned long long timediff=BAMBOO_GET_EXE_TIME()-bamboo_start_time;
  BAMBOO_PRINT(timediff);
#ifndef BAMBOO_MEMPROF
  BAMBOO_PRINT(0xbbbbbbbb);
#endif
#endif // USEIO
}

void getprofiledata_I() {
  //profile mode, send msgs to other cores to request pouring out progiling data
#ifdef PROFILE
  // use numconfirm to check if all cores have finished output task profiling 
  // information. This is safe as when the execution reaches this phase there 
  // should have no other msgs except the PROFILEFINISH msg, there should be 
  // no gc too.
  numconfirm=NUMCORESACTIVE-1;
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  for(i = 1; i < NUMCORESACTIVE; ++i) {
    // send profile request msg to core i
    send_msg_2(i, PROFILEOUTPUT, totalexetime);
  } 
#ifndef RT_TEST
  // pour profiling data on startup core
  outputProfileData();
#endif
  while(true) {
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
    if(numconfirm != 0) {
      int halt = 100;
      BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
      while(halt--) {
      }
    } else {
      BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
      break;
    }  
  } 
#endif
}

void checkCoreStatus() {
  int i = 0;
  int sumsendobj = 0;
  if((!waitconfirm) ||
     (waitconfirm && (numconfirm == 0))) {
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
    corestatus[BAMBOO_NUM_OF_CORE] = 0;
    numsendobjs[BAMBOO_NUM_OF_CORE] = self_numsendobjs;
    numreceiveobjs[BAMBOO_NUM_OF_CORE] = self_numreceiveobjs;
    // check the status of all cores
    for(i = 0; i < NUMCORESACTIVE; ++i) {
      if(corestatus[i] != 0) {
        break;
      }
    } 
    if(i == NUMCORESACTIVE) {
      // check if the sum of send objs and receive obj are the same
      // yes->check if the info is the latest; no->go on executing
      sumsendobj = 0;
      for(i = 0; i < NUMCORESACTIVE; ++i) {
        sumsendobj += numsendobjs[i];
      } 
      for(i = 0; i < NUMCORESACTIVE; ++i) {
        sumsendobj -= numreceiveobjs[i];
      }  
      if(0 == sumsendobj) {
        if(!waitconfirm) {
          // the first time found all cores stall
          // send out status confirm msg to all other cores
          // reset the corestatus array too
          corestatus[BAMBOO_NUM_OF_CORE] = 1;
          waitconfirm = true;
          numconfirm = NUMCORESACTIVE - 1;
          BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
          for(i = 1; i < NUMCORESACTIVE; ++i) {
            corestatus[i] = 1;
            // send status confirm msg to core i
            send_msg_1(i, STATUSCONFIRM);
          }   
          return;
        } else {
          // all the core status info are the latest
          // terminate; for profiling mode, send request to all
          // other cores to pour out profiling data
          recordtotalexetime();
          getprofiledata_I();
          CACHEADAPT_DISABLE_TIMER();
          GC_OUTPUT_PROFILE_DATA();
#ifdef PERFCOUNT
	  print_statistics();
#endif
	  gc_outputProfileDataReadable();
          disruntimedata();
	  tprintf("FINISH_EXECUTION\n");
          BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
          terminate();  // All done.
        }
      } else {		
        // still some objects on the fly on the network
        // reset the waitconfirm and numconfirm
        waitconfirm = false;
        numconfirm = 0;
      }  
    } else {
      // not all cores are stall, keep on waiting
      waitconfirm = false;
      numconfirm = 0;
    }  
    BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
  } 
}

// main function for each core
void run(int argc, char** argv) {
  bool sendStall = false;
  bool isfirst = true;
  bool tocontinue = false;
  startflag = false;
  corenum = BAMBOO_GET_NUM_OF_CORE();
  // initialize runtime data structures
  initruntimedata();
  initCommunication();
#ifdef PMC_GC
  pmc_onceInit();
#endif
#ifdef PERFCOUNT
  if (BAMBOO_NUM_OF_CORE==STARTUPCORE)
    profile_init(_LOCAL_DRD_CNT,_LOCAL_WR_CNT, _REMOTE_DRD_CNT, _REMOTE_WR_CNT);
  else {
    int offcore=4*(BAMBOO_NUM_OF_CORE-1);
    profile_init(validevents[(offcore)%87], validevents[(offcore+1)%87], validevents[(offcore+2)%87], validevents[(offcore+3)%87]);
  }
#endif
  if (BAMBOO_NUM_OF_CORE==STARTUPCORE) {
    numconfirm=NUMCORES-1;
    for(int i=0;i<NUMCORES;i++) {
      if (i!=STARTUPCORE) {
	send_msg_1(i,REQNOTIFYSTART);
      }
    }
    while(numconfirm!=0)
      ;
    tprintf("START_EXECUTION\n");
    bamboo_start_time = BAMBOO_GET_EXE_TIME();
  } else {
    while(!startflag)
      ;
  }
#ifdef PERFCOUNT
  bme_performance_counter_start();
#endif

  CACHEADAPT_ENABLE_TIMER();

  initializeexithandler();

  // main process of the execution module
  if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1) {
#ifdef TASK
    // non-executing cores, only processing communications
    activetasks = NULL;
#endif
    fakeExecution();
  } else {
#ifdef TASK
    /* Create queue of active tasks */
    activetasks= genallocatehashtable((unsigned int (*)(void *)) &hashCodetpd,
				      (int (*)(void *,void *)) &comparetpd);
    
    /* Process task information */
    processtasks();
    
    if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
      /* Create startup object */
      createstartupobject(argc, argv);
    }
#endif
    
#ifdef PERFCOUNT
      profile_start(APP_REGION);
#endif
    if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
#ifdef TASK
      // run the initStaticAndGlobal method to initialize the static blocks and
      // global fields
      initStaticAndGlobal();
#elif defined MGC
      // run the main method in the specified mainclass
      mgc_main(argc, argv);
#endif // TASK
    }
    
    while(true) {
      GCCHECK(NULL);
#ifdef TASK
      // check if there are new active tasks can be executed
      executetasks();
      if(busystatus) {
        sendStall = false;
      }
#ifndef INTERRUPT
      while(receiveObject_I() != -1) {
      }
#endif
      // check if there are some pending objects,
      // if yes, enqueue them and executetasks again
      tocontinue = checkObjQueue();
#elif defined MGC
      tocontinue = trystartthread();
      if(tocontinue) {
        sendStall = false;
      }
#endif
      
      if(!tocontinue) {
        // check if stop
        if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
          if(isfirst) {
            isfirst = false;
          }
          checkCoreStatus();
        } else {
          if(!sendStall) {
#ifdef PROFILE
            if(!stall) {
#endif
	      if(isfirst) {
		// wait for some time
		int halt = 10000;
		while(halt--) {
		}
		isfirst = false;
	      } else {
		// send StallMsg to startup core
		// send stall msg
		send_msg_4(STARTUPCORE,TRANSTALL,BAMBOO_NUM_OF_CORE,self_numsendobjs,self_numreceiveobjs);
		sendStall = true;
		isfirst = true;
		busystatus = false;
	      }
#ifdef PROFILE
            }
#endif
          } else {
            isfirst = true;
            busystatus = false;
          }
        }
      }
    }
  }
}
 
#endif // MULTICORE
