#ifdef MULTICORE

#include "runtime.h"
#include "multicoreruntime.h"
#include "runtime_arch.h"
#include "GenericHashtable.h"
#include "structdefs.h"
#include "methodheaders.h"
#include "mem.h"
#ifndef RAW
#include <stdio.h>
#include <stdlib.h>
#endif

#ifndef INLINE
#define INLINE    inline __attribute__((always_inline))
#endif // #ifndef INLINE

extern int classsize[];
extern int typearray[];
extern int typearray2[];
extern int* supertypes[];

#ifdef TASK
extern struct genhashtable * activetasks;
#endif
#ifdef MULTICORE_GC
#ifdef SMEMM
extern unsigned int gcmem_mixed_threshold;
extern unsigned int gcmem_mixed_usedmem;
#endif // SMEMM
#endif // MULTICORE_GC

int debugtask=0;
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
double CALL01(___Double______nativeparsedouble____L___String___,struct ___String___ * ___str___) {
  int length=VAR(___str___)->___count___;
  int maxlength=(length>60) ? 60 : length;
  char str[maxlength+1];
  struct ArrayObject * chararray=VAR(___str___)->___value___;
  int i;
  int offset=VAR(___str___)->___offset___;
  for(i=0; i<maxlength; i++) {
    str[i]=((short *)(((char *)&chararray->___length___)+sizeof(int)))[i+offset];
  }
  str[i]=0;
  double d=0.0; //atof(str); TODO Unimplemented nativeparsedoulbe
  return d;
}
#endif

#ifdef D___Double______nativeparsedouble_____AR_B_I_I 
double CALL23(___Double______nativeparsedouble_____AR_B_I_I, int start, int length,int start,int length,struct ArrayObject * ___str___) {
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

typedef union jvalue
{
  bool z;
  char    c;
  short   s;
  int     i;
  long long    j;
  float   f;
  double  d;
} jvalue;

#ifdef D___Double______doubleToRawLongBits____D 
long long CALL11(___Double______doubleToRawLongBits____D, double ___value___, double ___value___) {
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
double CALL11(___Double______longBitsToDouble____J, long long ___bits___, long long ___bits___) {
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
int CALL12(___String______convertdoubletochar____D__AR_C, double ___val___, double ___val___, struct ArrayObject * ___chararray___) {
  int length=VAR(___chararray___)->___length___;
  char str[length];
  int i;
  int num=snprintf(str, length, "%f",___val___);
  if (num>=length)
    num=length-1;
  for(i=0; i<length; i++) {
    ((short *)(((char *)&VAR(___chararray___)->___length___)+sizeof(int)))[i]=(short)str[i];
  }
  return num;
}
#else
int CALL12(___String______convertdoubletochar____D__AR_C, double ___val___, double ___val___, struct ArrayObject ___chararray___) {
  return 0;
}
#endif

#ifdef D___System______deepArrayCopy____L___Object____L___Object___
void deepArrayCopy(struct ___Object___ * dst, struct ___Object___ * src) {
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
    memcpy(((char *)&aodst->___length___)+sizeof(int) , ((char *)&aosrc->___length___)+sizeof(int), size);
  } else {
    //objects
    int i;
    for(i=0;i<srclength;i++) {
      struct ___Object___ * ptr=((struct ___Object___**)(((char*) &aosrc->___length___)+sizeof(int)))[i];
      int ptrtype=((int *)ptr)[0];
      if (ptrtype>=NUMCLASSES) {
	struct ___Object___ * dstptr=((struct ___Object___**)(((char*) &aodst->___length___)+sizeof(int)))[i];
	deepArrayCopy(dstptr,ptr);
      } else {
	//hit an object
	((struct ___Object___ **)(((char*) &aodst->___length___)+sizeof(int)))[i]=ptr;
      }
    }
  }
}

void CALL02(___System______deepArrayCopy____L___Object____L___Object___, struct ___Object___ * ___dst___, struct ___Object___ * ___src___) {
  deepArrayCopy(VAR(___dst___), VAR(___src___));
}
#endif

#ifdef D___System______arraycopy____L___Object____I_L___Object____I_I
void arraycopy(struct ___Object___ *src, int srcPos, struct ___Object___ *dst, int destPos, int length) {
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
    memcpy(((char *)&aodst->___length___)+sizeof(int)+destPos*elementsize, ((char *)&aosrc->___length___)+sizeof(int)+srcPos*elementsize, size);
  } else {
    //objects
    int i;
    for(i=0;i<length;i++) {
      struct ___Object___ * ptr=((struct ___Object___**)(((char*) &aosrc->___length___)+sizeof(int)))[i+srcPos];
      int ptrtype=((int *)ptr)[0];
      //hit an object
      ((struct ___Object___ **)(((char*) &aodst->___length___)+sizeof(int)))[i+destPos]=ptr;
    }
  }
}

void CALL35(___System______arraycopy____L___Object____I_L___Object____I_I, int ___srcPos___, int ___destPos___, int ___length___, struct ___Object___ * ___src___, int ___srcPos___, struct ___Object___ * ___dst___, int  ___destPos___, int ___length___) {
  arraycopy(VAR(___src___), ___srcPos___, VAR(___dst___), ___destPos___, ___length___);
}
#endif

void CALL11(___System______exit____I,int ___status___, int ___status___) {
  BAMBOO_EXIT(___status___);
}

#ifdef D___Vector______removeElement_____AR_L___Object____I_I
void CALL23(___Vector______removeElement_____AR_L___Object____I_I, int ___index___, int ___size___, struct ArrayObject * ___array___, int ___index___, int ___size___) {
  char* offset=((char *)(&VAR(___array___)->___length___))+sizeof(unsigned int)+sizeof(void *)*___index___;
  memmove(offset, offset+sizeof(void *),(___size___-___index___-1)*sizeof(void *));
}
#endif

void CALL11(___System______printI____I,int ___status___, int ___status___) {
  BAMBOO_DEBUGPRINT(0x1111);
  BAMBOO_DEBUGPRINT_REG(___status___);
}

long long CALL00(___System______currentTimeMillis____) {
  // not supported in MULTICORE version
  return -1;
}

void CALL01(___System______printString____L___String___,struct ___String___ * ___s___) {
#ifdef MGC
#ifdef TILERA_BME
  struct ArrayObject * chararray=VAR(___s___)->___value___;
  int i;
  int offset=VAR(___s___)->___offset___;
  tprintf("");
  for(i=0; i<VAR(___s___)->___count___; i++) {
	short sc=
	  ((short *)(((char *)&chararray->___length___)+sizeof(int)))[i+offset];
    printf("%c", sc);
  }
#endif // TILERA_BME
#endif // MGC
}

/* Object allocation function */

#ifdef MULTICORE_GC
void * allocate_new(void * ptr, int type) {
  struct ___Object___ * v=
	(struct ___Object___*)FREEMALLOC((struct garbagelist*) ptr,classsize[type]);
  v->type=type;
#ifdef TASK
  v->version = 0;
  v->lock = NULL;
  v->lockcount = 0;
#endif
  initlock(v);
#ifdef GC_PROFILE
  extern unsigned int gc_num_obj;
  gc_num_obj++;
#endif
  return v;
}

/* Array allocation function */

struct ArrayObject * allocate_newarray(void * ptr, int type, int length) {
  struct ArrayObject * v=(struct ArrayObject *)
	FREEMALLOC((struct garbagelist*)ptr,
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
  initlock(v);
#ifdef GC_PROFILE
  extern unsigned int gc_num_obj;
  gc_num_obj++;
#endif
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

struct ArrayObject * allocate_newarray(int type, int length) {
  struct ArrayObject * v=
	FREEMALLOC(sizeof(struct ArrayObject)+length*classsize[type]);
  v->type=type;
#ifdef TASK
  v->version = 0;
  v->lock = NULL;
#endif
  v->___length___=length;
  initlock(v);
  return v;
}
#endif


/* Converts C character arrays into Java strings */
#ifdef MULTICORE_GC
struct ___String___ * NewString(void * ptr, const char *str,int length) {
#else
struct ___String___ * NewString(const char *str,int length) {
#endif
  int i;
#ifdef MULTICORE_GC
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

void failedboundschk() {
#ifndef TASK
  printf("Array out of bounds\n");
#ifdef THREADS
  threadexit();
#elif defined MGC
  BAMBOO_EXIT(0xa002);
#else
  exit(-1);
#endif
#else
#ifndef MULTICORE
  printf("Array out of bounds\n");
  longjmp(error_handler,2);
#else
  BAMBOO_EXIT(0xa002);
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

INLINE void initruntimedata() {
  int i;
  // initialize the arrays
  if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
    // startup core to initialize corestatus[]
    for(i = 0; i < NUMCORESACTIVE; ++i) {
      corestatus[i] = 1;
      numsendobjs[i] = 0;
      numreceiveobjs[i] = 0;
#ifdef MULTICORE_GC
      gccorestatus[i] = 1;
      gcnumsendobjs[0][i] = gcnumsendobjs[1][i] = 0;
      gcnumreceiveobjs[0][i] = gcnumreceiveobjs[1][i] = 0;
#endif
    } // for(i = 0; i < NUMCORESACTIVE; ++i)
#ifdef MULTICORE_GC
    for(i = 0; i < NUMCORES4GC; ++i) {
      gcloads[i] = 0;
      gcrequiredmems[i] = 0;
      gcstopblock[i] = 0;
      gcfilledblocks[i] = 0;
    } // for(i = 0; i < NUMCORES4GC; ++i)
#ifdef GC_PROFILE
    gc_infoIndex = 0;
    gc_infoOverflow = false;
	gc_num_livespace = 0;
	gc_num_freespace = 0;
#endif
#endif
    numconfirm = 0;
    waitconfirm = false;
  }

  busystatus = true;
  self_numsendobjs = 0;
  self_numreceiveobjs = 0;

  for(i = 0; i < BAMBOO_MSG_BUF_LENGTH; ++i) {
    msgdata[i] = -1;
  }
  msgdataindex = 0;
  msgdatalast = 0;
  msglength = BAMBOO_MSG_BUF_LENGTH;
  msgdatafull = false;
  for(i = 0; i < BAMBOO_OUT_BUF_LENGTH; ++i) {
    outmsgdata[i] = -1;
  }
  outmsgindex = 0;
  outmsglast = 0;
  outmsgleft = 0;
  isMsgHanging = false;

  smemflag = true;
  bamboo_cur_msp = NULL;
  bamboo_smem_size = 0;

#ifdef MULTICORE_GC
  bamboo_smem_zero_top = NULL;
  gcflag = false;
  gcprocessing = false;
  gcphase = FINISHPHASE;
  gcprecheck = true;
  gccurr_heaptop = 0;
  gcself_numsendobjs = 0;
  gcself_numreceiveobjs = 0;
  gcmarkedptrbound = 0;
#ifdef LOCALHASHTBL_TEST
  gcpointertbl = allocateRuntimeHash_I(20);
#else
  gcpointertbl = mgchashCreate_I(2000, 0.75);
#endif
  gcforwardobjtbl = allocateMGCHash_I(20, 3);
  gcobj2map = 0;
  gcmappedobj = 0;
  gcnumlobjs = 0;
  gcheaptop = 0;
  gctopcore = 0;
  gctopblock = 0;
  gcmovestartaddr = 0;
  gctomove = false;
  gcmovepending = 0;
  gcblock2fill = 0;
  if(BAMBOO_NUM_OF_CORE < NUMCORES4GC) {
	int t_size = ((BAMBOO_RMSP_SIZE)-sizeof(mgcsharedhashtbl_t)*2
		-128*sizeof(size_t))/sizeof(mgcsharedhashlistnode_t)-2;
	int kk = 0;
	unsigned int tmp_k = 1 << (sizeof(int)*8 -1);
	while(((t_size & tmp_k) == 0) && (kk < sizeof(int)*8)) {
	  t_size = t_size << 1;
	  kk++;
	}
	t_size = tmp_k >> kk;
	gcsharedptbl = mgcsharedhashCreate_I(t_size,0.30);
  } else {
	gcsharedptbl = NULL;
  }
  BAMBOO_MEMSET_WH(gcrpointertbls, 0, 
	  sizeof(mgcsharedhashtbl_t *)*NUMCORES4GC);
#ifdef SMEMM
  gcmem_mixed_threshold = (unsigned int)((BAMBOO_SHARED_MEM_SIZE
		-bamboo_reserved_smem*BAMBOO_SMEM_SIZE)*0.8);
  gcmem_mixed_usedmem = 0;
#endif
#ifdef GC_PROFILE
  gc_num_obj = 0;
  gc_num_liveobj = 0;
  gc_num_forwardobj = 0;
  gc_num_profiles = NUMCORESACTIVE - 1;
#endif
#ifdef GC_FLUSH_DTLB
  gc_num_flush_dtlb = 0;
#endif
  gc_localheap_s = false;
#ifdef GC_CACHE_ADAPT
  gccachestage = false;
#endif // GC_CACHE_ADAPT
#endif // MULTICORE_GC
#ifndef INTERRUPT
  reside = false;
#endif

#ifdef MGC
  initializethreads();
  bamboo_current_thread = NULL;
#endif // MGC

#ifdef TASK
  inittaskdata();
#endif
}

INLINE void disruntimedata() {
#ifdef MULTICORE_GC
#ifdef LOCALHASHTBL_TEST
  freeRuntimeHash(gcpointertbl);
#else
  mgchashDelete(gcpointertbl);
#endif
  freeMGCHash(gcforwardobjtbl);
#endif // MULTICORE_GC
#ifdef TASK
  distaskdata()
#endif // TASK
  BAMBOO_LOCAL_MEM_CLOSE();
  BAMBOO_SHARE_MEM_CLOSE();
}

INLINE void checkCoreStatus() {
  bool allStall = false;
  int i = 0;
  int sumsendobj = 0;
  if((!waitconfirm) ||
     (waitconfirm && (numconfirm == 0))) {
    BAMBOO_DEBUGPRINT(0xee04);
    BAMBOO_DEBUGPRINT_REG(waitconfirm);
    BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
    BAMBOO_DEBUGPRINT(0xf001);
    corestatus[BAMBOO_NUM_OF_CORE] = 0;
    numsendobjs[BAMBOO_NUM_OF_CORE] = self_numsendobjs;
    numreceiveobjs[BAMBOO_NUM_OF_CORE] = self_numreceiveobjs;
    // check the status of all cores
    allStall = true;
    BAMBOO_DEBUGPRINT_REG(NUMCORESACTIVE);
    for(i = 0; i < NUMCORESACTIVE; ++i) {
      BAMBOO_DEBUGPRINT(0xe000 + corestatus[i]);
      if(corestatus[i] != 0) {
		allStall = false;
		break;
      }
    }  // for(i = 0; i < NUMCORESACTIVE; ++i)
    if(allStall) {
      // check if the sum of send objs and receive obj are the same
      // yes->check if the info is the latest; no->go on executing
      sumsendobj = 0;
      for(i = 0; i < NUMCORESACTIVE; ++i) {
		sumsendobj += numsendobjs[i];
		BAMBOO_DEBUGPRINT(0xf000 + numsendobjs[i]);
      }  // for(i = 0; i < NUMCORESACTIVE; ++i)
      for(i = 0; i < NUMCORESACTIVE; ++i) {
		sumsendobj -= numreceiveobjs[i];
		BAMBOO_DEBUGPRINT(0xf000 + numreceiveobjs[i]);
      }  // for(i = 0; i < NUMCORESACTIVE; ++i)
      if(0 == sumsendobj) {
		if(!waitconfirm) {
		  // the first time found all cores stall
		  // send out status confirm msg to all other cores
		  // reset the corestatus array too
		  BAMBOO_DEBUGPRINT(0xee05);
		  corestatus[BAMBOO_NUM_OF_CORE] = 1;
		  waitconfirm = true;
		  numconfirm = NUMCORESACTIVE - 1;
		  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
		  for(i = 1; i < NUMCORESACTIVE; ++i) {
			corestatus[i] = 1;
			// send status confirm msg to core i
			send_msg_1(i, STATUSCONFIRM, false);
		  }   // for(i = 1; i < NUMCORESACTIVE; ++i)
		  return;
		} else {
		  // all the core status info are the latest
		  // terminate; for profiling mode, send request to all
		  // other cores to pour out profiling data
		  BAMBOO_DEBUGPRINT(0xee06);

#ifdef USEIO
		  totalexetime = BAMBOO_GET_EXE_TIME() - bamboo_start_time;
#else

		  BAMBOO_PRINT(BAMBOO_GET_EXE_TIME() - bamboo_start_time);
		  //BAMBOO_DEBUGPRINT_REG(total_num_t6); // TODO for test
#ifdef GC_FLUSH_DTLB
		  BAMBOO_PRINT_REG(gc_num_flush_dtlb);
#endif
#ifndef BAMBOO_MEMPROF
		  BAMBOO_PRINT(0xbbbbbbbb);
#endif
#endif
		  // profile mode, send msgs to other cores to request pouring
		  // out progiling data
#ifdef PROFILE
		  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
		  BAMBOO_DEBUGPRINT(0xf000);
		  for(i = 1; i < NUMCORESACTIVE; ++i) {
			// send profile request msg to core i
			send_msg_2(i, PROFILEOUTPUT, totalexetime, false);
		  } // for(i = 1; i < NUMCORESACTIVE; ++i)
#ifndef RT_TEST
		  // pour profiling data on startup core
		  outputProfileData();
#endif
		  while(true) {
			BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
			BAMBOO_DEBUGPRINT(0xf001);
			profilestatus[BAMBOO_NUM_OF_CORE] = 0;
			// check the status of all cores
			allStall = true;
			BAMBOO_DEBUGPRINT_REG(NUMCORESACTIVE);
			for(i = 0; i < NUMCORESACTIVE; ++i) {
			  BAMBOO_DEBUGPRINT(0xe000 + profilestatus[i]);
			  if(profilestatus[i] != 0) {
				allStall = false;
				break;
			  }
			}  // for(i = 0; i < NUMCORESACTIVE; ++i)
			if(!allStall) {
			  int halt = 100;
			  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
			  BAMBOO_DEBUGPRINT(0xf000);
			  while(halt--) {
			  }
			} else {
			  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
			  break;
			}  // if(!allStall)
		  }  // while(true)
#endif

		  // gc_profile mode, output gc prfiling data
#ifdef MULTICORE_GC
#ifdef GC_CACHE_ADAPT
		  bamboo_mask_timer_intr(); // disable the TILE_TIMER interrupt
#endif // GC_CACHE_ADAPT
#ifdef GC_PROFILE
		  gc_outputProfileData();
#endif // #ifdef GC_PROFILE
#endif // #ifdef MULTICORE_GC
		  disruntimedata();
		  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
		  terminate();  // All done.
		}  // if(!waitconfirm)
      } else {
		// still some objects on the fly on the network
		// reset the waitconfirm and numconfirm
		BAMBOO_DEBUGPRINT(0xee07);
		waitconfirm = false;
		numconfirm = 0;
	  }  //  if(0 == sumsendobj)
    } else {
      // not all cores are stall, keep on waiting
      BAMBOO_DEBUGPRINT(0xee08);
      waitconfirm = false;
      numconfirm = 0;
    }  //  if(allStall)
    BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
    BAMBOO_DEBUGPRINT(0xf000);
  }  // if((!waitconfirm) ||
}

// main function for each core
inline void run(int argc, char** argv) {
  int i = 0;
  bool sendStall = false;
  bool isfirst = true;
  bool tocontinue = false;

  corenum = BAMBOO_GET_NUM_OF_CORE();
  BAMBOO_DEBUGPRINT(0xeeee);
  BAMBOO_DEBUGPRINT_REG(corenum);
  BAMBOO_DEBUGPRINT(STARTUPCORE);

  // initialize runtime data structures
  initruntimedata();

  // other architecture related initialization
  initialization();
  initCommunication();

#ifdef GC_CACHE_ADAPT
// enable the timer interrupt
#ifdef GC_CACHE_SAMPLING
  bamboo_tile_timer_set_next_event(GC_TILE_TIMER_EVENT_SETTING); // TODO
  bamboo_unmask_timer_intr();
  bamboo_dtlb_sampling_process();
#endif // GC_CACHE_SAMPLING
#endif // GC_CACHE_ADAPT

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
    activetasks=
      genallocatehashtable((unsigned int (*)(void *)) &hashCodetpd,
                           (int (*)(void *,void *)) &comparetpd);

    /* Process task information */
    processtasks();

    if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
      /* Create startup object */
      createstartupobject(argc, argv);
    }

    BAMBOO_DEBUGPRINT(0xee00);
#endif

#ifdef MGC
	if(STARTUPCORE == BAMBOO_NUM_OF_CORE) {
	  // run the main method in the specified mainclass
	  mgc_main(argc, argv);
	}
#endif

    while(true) {

#ifdef MULTICORE_GC
      // check if need to do GC
      if(gcflag) {
		gc(NULL);
	  }
#endif // MULTICORE_GC

#ifdef TASK
      // check if there are new active tasks can be executed
      executetasks();
      if(busystatus) {
		sendStall = false;
      }

#ifndef INTERRUPT
      while(receiveObject() != -1) {
      }
#endif

      BAMBOO_DEBUGPRINT(0xee01);

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
			BAMBOO_DEBUGPRINT(0xee03);
			isfirst = false;
		  }
		  checkCoreStatus();
		} else {
		  if(!sendStall) {
			BAMBOO_DEBUGPRINT(0xee09);
#ifdef PROFILE
			if(!stall) {
#endif
			if(isfirst) {
			  // wait for some time
			  int halt = 10000;
			  BAMBOO_DEBUGPRINT(0xee0a);
			  while(halt--) {
			  }
			  isfirst = false;
			} else {
			  // send StallMsg to startup core
			  BAMBOO_DEBUGPRINT(0xee0b);
			  // send stall msg
			  send_msg_4(STARTUPCORE, TRANSTALL, BAMBOO_NUM_OF_CORE,
						 self_numsendobjs, self_numreceiveobjs, false);
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
			BAMBOO_DEBUGPRINT(0xee0c);
		  }   // if(!sendStall)
		}   // if(STARTUPCORE == BAMBOO_NUM_OF_CORE)
      }  // if(!tocontinue)
    }  // while(true)
  } // if(BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1)

} // run()

INLINE int checkMsgLength_I(int size) {
#ifndef CLOSE_PRINT
  BAMBOO_DEBUGPRINT(0xcccc);
#endif
  int type = msgdata[msgdataindex];
  switch(type) {
  case STATUSCONFIRM:
  case TERMINATE:
#ifdef MULTICORE_GC
  case GCSTARTPRE:
  case GCSTARTINIT:
  case GCSTART:
  case GCSTARTMAPINFO:
  case GCSTARTFLUSH:
  case GCFINISH:
  case GCMARKCONFIRM:
  case GCLOBJREQUEST:
#ifdef GC_CACHE_ADAPT
  case GCSTARTPREF:
#endif // GC_CACHE_ADAPT
#endif // MULTICORE_GC
  {
	msglength = 1;
	break;
  }

#ifdef TASK
  case PROFILEOUTPUT:
  case PROFILEFINISH:
#endif
#ifdef MULTICORE_GC
  case GCSTARTCOMPACT:
  case GCMARKEDOBJ:
  case GCFINISHINIT:
  case GCFINISHMAPINFO:
  case GCFINISHFLUSH:
#ifdef GC_CACHE_ADAPT
  case GCFINISHPREF:
#endif // GC_CACHE_ADAPT
#endif // MULTICORE_GC
  {
	msglength = 2;
	break;
  }

  case MEMREQUEST:
  case MEMRESPONSE:
#ifdef MULTICORE_GC
  case GCMAPREQUEST:
  case GCMAPINFO:
  case GCMAPTBL:
  case GCLOBJMAPPING:
#endif
  {
	msglength = 3;
	break;
  }

  case TRANSTALL:
#ifdef TASK
  case LOCKGROUNT:
  case LOCKDENY:
  case LOCKRELEASE:
  case REDIRECTGROUNT:
  case REDIRECTDENY:
  case REDIRECTRELEASE:
#endif
#ifdef MULTICORE_GC
  case GCFINISHPRE:
  case GCFINISHMARK:
  case GCMOVESTART:
#ifdef GC_PROFILE
  case GCPROFILES:
#endif
#endif
  {
	msglength = 4;
	break;
  }

#ifdef TASK
  case LOCKREQUEST:
#endif
  case STATUSREPORT:
#ifdef MULTICORE_GC
  case GCFINISHCOMPACT:
  case GCMARKREPORT:
#endif
  {
	msglength = 5;
	break;
  }

#ifdef TASK
  case REDIRECTLOCK:
  {
    msglength = 6;
    break;
  }
#endif

#ifdef TASK
  case TRANSOBJ:   // nonfixed size
#endif
#ifdef MULTICORE_GC
  case GCLOBJINFO:
#endif
  {  // nonfixed size
	if(size > 1) {
	  msglength = msgdata[(msgdataindex+1)&(BAMBOO_MSG_BUF_MASK)];
	} else {
	  return -1;
	}
	break;
  }

  default:
  {
    BAMBOO_DEBUGPRINT_REG(type);
	BAMBOO_DEBUGPRINT_REG(size);
    BAMBOO_DEBUGPRINT_REG(msgdataindex);
	BAMBOO_DEBUGPRINT_REG(msgdatalast);
	BAMBOO_DEBUGPRINT_REG(msgdatafull);
    int i = 6;
    while(i-- > 0) {
      BAMBOO_DEBUGPRINT(msgdata[msgdataindex+i]);
    }
    BAMBOO_EXIT(0xe004);
    break;
  }
  }
#ifndef CLOSE_PRINT
  BAMBOO_DEBUGPRINT_REG(msgdata[msgdataindex]);
  BAMBOO_DEBUGPRINT(0xffff);
#endif
  return msglength;
}

INLINE void processmsg_transtall_I() {
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // non startup core can not receive stall msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(msgdata[msgdataindex] /*[1]*/);
#endif
    BAMBOO_EXIT(0xe006);
  }
  int num_core = msgdata[msgdataindex]; //[1]
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex]; //[2];
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex]; //[3];
  MSG_INDEXINC_I();
  if(num_core < NUMCORESACTIVE) {
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT(0xe881);
#endif
    corestatus[num_core] = 0;
    numsendobjs[num_core] = data2; //[2];
    numreceiveobjs[num_core] = data3; //[3];
  }
}

INLINE void processmsg_statusconfirm_I() {
  if((BAMBOO_NUM_OF_CORE == STARTUPCORE)
     || (BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1)) {
    // wrong core to receive such msg
    BAMBOO_EXIT(0xe011);
  } else {
    // send response msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT(0xe887);
#endif
    // cache the msg first
    if(BAMBOO_CHECK_SEND_MODE()) {
	  cache_msg_5(STARTUPCORE, STATUSREPORT,
				  busystatus ? 1 : 0, BAMBOO_NUM_OF_CORE,
				  self_numsendobjs, self_numreceiveobjs);
    } else {
	  send_msg_5(STARTUPCORE, STATUSREPORT, busystatus?1:0,
				 BAMBOO_NUM_OF_CORE, self_numsendobjs,
				 self_numreceiveobjs, true);
    }
  }
}

INLINE void processmsg_statusreport_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data4 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // receive a status confirm info
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // wrong core to receive such msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data2);
#endif
    BAMBOO_EXIT(0xe012);
  } else {
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT(0xe888);
#endif
    if(waitconfirm) {
      numconfirm--;
    }
    corestatus[data2] = data1;
    numsendobjs[data2] = data3;
    numreceiveobjs[data2] = data4;
  }
}

INLINE void processmsg_terminate_I() {
#ifndef CLOSE_PRINT
  BAMBOO_DEBUGPRINT(0xe889);
#endif
  disruntimedata();
#ifdef MULTICORE_GC
#ifdef GC_CACHE_ADAPT
  bamboo_mask_timer_intr(); // disable the TILE_TIMER interrupt
#endif // GC_CACHE_ADAPT
#endif // MULTICORE_GC
  BAMBOO_EXIT_APP(0);
}

INLINE void processmsg_memrequest_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // receive a shared memory request msg
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // wrong core to receive such msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data2);
#endif
    BAMBOO_EXIT(0xe013);
  } else {
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT(0xe88a);
#endif
    int allocsize = 0;
    void * mem = NULL;
#ifdef MULTICORE_GC
    if(gcprocessing) {
      // is currently doing gc, dump this msg
      if(INITPHASE == gcphase) {
		// if still in the initphase of gc, send a startinit msg again,
		// cache the msg first
		if(BAMBOO_CHECK_SEND_MODE()) {
		  cache_msg_1(data2, GCSTARTINIT);
		} else {
		  send_msg_1(data2, GCSTARTINIT, true);
		}
      }
    } else {
#endif
    mem = smemalloc_I(data2, data1, &allocsize);
    if(mem != NULL) {
      // send the start_va to request core, cache the msg first
      if(BAMBOO_CHECK_SEND_MODE()) {
		cache_msg_3(data2, MEMRESPONSE, mem, allocsize);
      } else {
		send_msg_3(data2, MEMRESPONSE, mem, allocsize, true);
	  }
    } //else 
	  // if mem == NULL, the gcflag of the startup core has been set
	  // and all the other cores have been informed to start gc
#ifdef MULTICORE_GC
  }
#endif
  }
}

INLINE void processmsg_memresponse_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // receive a shared memory response msg
#ifndef CLOSE_PRINT
  BAMBOO_DEBUGPRINT(0xe88b);
#endif
#ifdef MULTICORE_GC
  // if is currently doing gc, dump this msg
  if(!gcprocessing) {
#endif
  if(data2 == 0) {
    bamboo_smem_size = 0;
    bamboo_cur_msp = 0;
#ifdef MULTICORE_GC
	bamboo_smem_zero_top = 0;
#endif
  } else {
#ifdef MULTICORE_GC
    // fill header to store the size of this mem block
    BAMBOO_MEMSET_WH(data1, '\0', BAMBOO_CACHE_LINE_SIZE); 
    (*((int*)data1)) = data2;
    bamboo_smem_size = data2 - BAMBOO_CACHE_LINE_SIZE;
    bamboo_cur_msp = data1 + BAMBOO_CACHE_LINE_SIZE;
	bamboo_smem_zero_top = bamboo_cur_msp;
#else
    bamboo_smem_size = data2;
    bamboo_cur_msp =(void*)(data1);
#endif
  }
  smemflag = true;
#ifdef MULTICORE_GC
}
#endif
}

#ifdef MULTICORE_GC
INLINE void processmsg_gcstartpre_I() {
  if(gcprocessing) {
	// already stall for gc
	// send a update pregc information msg to the master core
	if(BAMBOO_CHECK_SEND_MODE()) {
	  cache_msg_4(STARTUPCORE, GCFINISHPRE, BAMBOO_NUM_OF_CORE, 
		  self_numsendobjs, self_numreceiveobjs);
	} else {
	  send_msg_4(STARTUPCORE, GCFINISHPRE, BAMBOO_NUM_OF_CORE, 
		  self_numsendobjs, self_numreceiveobjs, true);
	}
  } else {
	// the first time to be informed to start gc
	gcflag = true;
	if(!smemflag) {
	  // is waiting for response of mem request
	  // let it return NULL and start gc
	  bamboo_smem_size = 0;
	  bamboo_cur_msp = NULL;
	  smemflag = true;
	  bamboo_smem_zero_top = NULL;
	}
  }
}

INLINE void processmsg_gcstartinit_I() {
  gcphase = INITPHASE;
}

INLINE void processmsg_gcstart_I() {
#ifndef CLOSE_PRINT
  BAMBOO_DEBUGPRINT(0xe88c);
#endif
  // set the GC flag
  gcphase = MARKPHASE;
}

INLINE void processmsg_gcstartcompact_I() {
  gcblock2fill = msgdata[msgdataindex];
  MSG_INDEXINC_I();  //msgdata[1];
  gcphase = COMPACTPHASE;
}

INLINE void processmsg_gcstartmapinfo_I() {
  gcphase = MAPPHASE;
}

INLINE void processmsg_gcstartflush_I() {
  gcphase = FLUSHPHASE;
}

INLINE void processmsg_gcfinishpre_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // received a init phase finish msg
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // non startup core can not receive this msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data1);
#endif
    BAMBOO_EXIT(0xe014);
  }
  // All cores should do init GC
  if(!gcprecheck) {
	gcprecheck = true;
  }
  gccorestatus[data1] = 0;
  gcnumsendobjs[0][data1] = data2;
  gcnumreceiveobjs[0][data1] = data3;
}

INLINE void processmsg_gcfinishinit_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // received a init phase finish msg
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // non startup core can not receive this msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data1);
#endif
    BAMBOO_EXIT(0xe015);
  }
#ifndef CLOSE_PRINT
  BAMBOO_DEBUGPRINT(0xe88c);
  BAMBOO_DEBUGPRINT_REG(data1);
#endif
  // All cores should do init GC
  if(data1 < NUMCORESACTIVE) {
    gccorestatus[data1] = 0;
  }
}

INLINE void processmsg_gcfinishmark_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // received a mark phase finish msg
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // non startup core can not receive this msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data1);
#endif
    BAMBOO_EXIT(0xe016);
  }
  // all cores should do mark
  if(data1 < NUMCORESACTIVE) {
    gccorestatus[data1] = 0;
	int entry_index = 0;
	if(waitconfirm)  {
	  // phase 2
	  entry_index = (gcnumsrobjs_index == 0) ? 1 : 0;
	} else {
	  // phase 1
	  entry_index = gcnumsrobjs_index;
	}
    gcnumsendobjs[entry_index][data1] = data2;
    gcnumreceiveobjs[entry_index][data1] = data3;
  }
}

INLINE void processmsg_gcfinishcompact_I() {
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // non startup core can not receive this msg
    // return -1
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(msgdata[msgdataindex] /*[1]*/);
#endif
    BAMBOO_EXIT(0xe017);
  }
  int cnum = msgdata[msgdataindex];
  MSG_INDEXINC_I();       //msgdata[1];
  int filledblocks = msgdata[msgdataindex];
  MSG_INDEXINC_I();       //msgdata[2];
  int heaptop = msgdata[msgdataindex];
  MSG_INDEXINC_I();       //msgdata[3];
  int data4 = msgdata[msgdataindex];
  MSG_INDEXINC_I();       //msgdata[4];
  // only gc cores need to do compact
  if(cnum < NUMCORES4GC) {
    if(COMPACTPHASE == gcphase) {
      gcfilledblocks[cnum] = filledblocks;
      gcloads[cnum] = heaptop;
    }
    if(data4 > 0) {
      // ask for more mem
      int startaddr = 0;
      int tomove = 0;
      int dstcore = 0;
      if(gcfindSpareMem_I(&startaddr, &tomove, &dstcore, data4, cnum)) {
		// cache the msg first
		if(BAMBOO_CHECK_SEND_MODE()) {
		  cache_msg_4(cnum, GCMOVESTART, dstcore, startaddr, tomove);
		} else {
		  send_msg_4(cnum, GCMOVESTART, dstcore, startaddr, tomove, true);
		}
      }
    } else {
      gccorestatus[cnum] = 0;
    }  // if(data4>0)
  }  // if(cnum < NUMCORES4GC)
}

INLINE void processmsg_gcfinishmapinfo_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // received a map phase finish msg
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // non startup core can not receive this msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data1);
#endif
    BAMBOO_EXIT(0xe018);
  }
  // all cores should do flush
  if(data1 < NUMCORES4GC) {
    gccorestatus[data1] = 0;
  }
}


INLINE void processmsg_gcfinishflush_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // received a flush phase finish msg
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // non startup core can not receive this msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data1);
#endif
    BAMBOO_EXIT(0xe019);
  }
  // all cores should do flush
  if(data1 < NUMCORESACTIVE) {
    gccorestatus[data1] = 0;
  }
}

INLINE void processmsg_gcmarkconfirm_I() {
  if((BAMBOO_NUM_OF_CORE == STARTUPCORE)
     || (BAMBOO_NUM_OF_CORE > NUMCORESACTIVE - 1)) {
    // wrong core to receive such msg
    BAMBOO_EXIT(0xe01a);
  } else {
    // send response msg, cahce the msg first
    if(BAMBOO_CHECK_SEND_MODE()) {
	  cache_msg_5(STARTUPCORE, GCMARKREPORT, BAMBOO_NUM_OF_CORE,
				  gcbusystatus, gcself_numsendobjs,
				  gcself_numreceiveobjs);
    } else {
	  send_msg_5(STARTUPCORE, GCMARKREPORT, BAMBOO_NUM_OF_CORE,
				 gcbusystatus, gcself_numsendobjs,
				 gcself_numreceiveobjs, true);
    }
  }
}

INLINE void processmsg_gcmarkreport_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data4 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // received a marked phase finish confirm response msg
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // wrong core to receive such msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data2);
#endif
    BAMBOO_EXIT(0xe01b);
  } else {
	int entry_index = 0;
    if(waitconfirm) {
	  // phse 2
      numconfirm--;
	  entry_index = (gcnumsrobjs_index == 0) ? 1 : 0;
    } else {
	  // can never reach here
	  // phase 1
	  entry_index = gcnumsrobjs_index;
	}
    gccorestatus[data1] = data2;
    gcnumsendobjs[entry_index][data1] = data3;
    gcnumreceiveobjs[entry_index][data1] = data4;
  }
}

INLINE void processmsg_gcmarkedobj_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // received a markedObj msg
  if(((int *)data1)[BAMBOOMARKBIT] == INIT) {
    // this is the first time that this object is discovered,
    // set the flag as DISCOVERED
    ((int *)data1)[BAMBOOMARKBIT] = DISCOVERED;
    gc_enqueue_I(data1);
  } 
  // set the remote flag
  ((int *)data1)[BAMBOOMARKBIT] |= REMOTEM;
  gcself_numreceiveobjs++;
  gcbusystatus = true;
}

INLINE void processmsg_gcmovestart_I() {
  gctomove = true;
  gcdstcore = msgdata[msgdataindex];
  MSG_INDEXINC_I();       //msgdata[1];
  gcmovestartaddr = msgdata[msgdataindex];
  MSG_INDEXINC_I();       //msgdata[2];
  gcblock2fill = msgdata[msgdataindex];
  MSG_INDEXINC_I();       //msgdata[3];
}

INLINE void processmsg_gcmaprequest_I() {
  void * dstptr = NULL;
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
#ifdef LOCALHASHTBL_TEST
  RuntimeHashget(gcpointertbl, data1, &dstptr);
#else
  dstptr = mgchashSearch(gcpointertbl, data1);
#endif
  if(NULL == dstptr) {
    // no such pointer in this core, something is wrong
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data1);
    BAMBOO_DEBUGPRINT_REG(data2);
#endif
    BAMBOO_EXIT(0xe01c);
  } else {
    // send back the mapping info, cache the msg first
    if(BAMBOO_CHECK_SEND_MODE()) {
	  cache_msg_3(data2, GCMAPINFO, data1, (int)dstptr);
    } else {
	  send_msg_3(data2, GCMAPINFO, data1, (int)dstptr, true);
    }
  }
}

INLINE void processmsg_gcmapinfo_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  gcmappedobj = msgdata[msgdataindex];  // [2]
  MSG_INDEXINC_I();
#ifdef LOCALHASHTBL_TEST
  RuntimeHashadd_I(gcpointertbl, data1, gcmappedobj);
#else
  mgchashInsert_I(gcpointertbl, data1, gcmappedobj);
#endif
  if(data1 == gcobj2map) {
	gcismapped = true;
  }
}

INLINE void processmsg_gcmaptbl_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  gcrpointertbls[data2] = (mgcsharedhashtbl_t *)data1; 
}

INLINE void processmsg_gclobjinfo_I() {
  numconfirm--;

  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  if(BAMBOO_NUM_OF_CORE > NUMCORES4GC - 1) {
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data2);
#endif
    BAMBOO_EXIT(0xe01d);
  }
  // store the mark result info
  int cnum = data2;
  gcloads[cnum] = msgdata[msgdataindex];
  MSG_INDEXINC_I();       // msgdata[3];
  int data4 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  if(gcheaptop < data4) {
    gcheaptop = data4;
  }
  // large obj info here
  for(int k = 5; k < data1; k+=2) {
    int lobj = msgdata[msgdataindex];
    MSG_INDEXINC_I();   //msgdata[k++];
    int length = msgdata[msgdataindex];
    MSG_INDEXINC_I();   //msgdata[k++];
    gc_lobjenqueue_I(lobj, length, cnum);
    gcnumlobjs++;
  }  // for(int k = 5; k < msgdata[1];)
}

INLINE void processmsg_gclobjmapping_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
#ifdef LOCALHASHTBL_TEST
  RuntimeHashadd_I(gcpointertbl, data1, data2);
#else
  mgchashInsert_I(gcpointertbl, data1, data2);
#endif
  mgcsharedhashInsert_I(gcsharedptbl, data1, data2);
}

#ifdef GC_PROFILE
INLINE void processmsg_gcprofiles_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data2 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  int data3 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  gc_num_obj += data1;
  gc_num_liveobj += data2;
  gc_num_forwardobj += data3;
  gc_num_profiles--;
}
#endif // GC_PROFILE

#ifdef GC_CACHE_ADAPT
INLINE void processmsg_gcstartpref_I() {
  gcphase = PREFINISHPHASE;
}

INLINE void processmsg_gcfinishpref_I() {
  int data1 = msgdata[msgdataindex];
  MSG_INDEXINC_I();
  // received a flush phase finish msg
  if(BAMBOO_NUM_OF_CORE != STARTUPCORE) {
    // non startup core can not receive this msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT_REG(data1);
#endif
    BAMBOO_EXIT(0xe01e);
  }
  // all cores should do flush
  if(data1 < NUMCORESACTIVE) {
    gccorestatus[data1] = 0;
  }
}
#endif // GC_CACHE_ADAPT
#endif // #ifdef MULTICORE_GC

// receive object transferred from other cores
// or the terminate message from other cores
// Should be invoked in critical sections!!
// NOTICE: following format is for threadsimulate version only
//         RAW version please see previous description
// format: type + object
// type: -1--stall msg
//      !-1--object
// return value: 0--received an object
//               1--received nothing
//               2--received a Stall Msg
//               3--received a lock Msg
//               RAW version: -1 -- received nothing
//                            otherwise -- received msg type
int receiveObject(int send_port_pending) {
#ifdef TASK
#ifdef PROFILE_INTERRUPT
  if(!interruptInfoOverflow) {
    InterruptInfo* intInfo = RUNMALLOC_I(sizeof(struct interrupt_info));
    interruptInfoArray[interruptInfoIndex] = intInfo;
    intInfo->startTime = BAMBOO_GET_EXE_TIME();
    intInfo->endTime = -1;
  }
#endif // PROFILE_INTERRUPT
#endif // TASK
msg:
  // get the incoming msgs
  if(receiveMsg(send_port_pending) == -1) {
    return -1;
  }
processmsg:
  // processing received msgs
  int size = 0;
  MSG_REMAINSIZE_I(&size);
  if((size == 0) || (checkMsgLength_I(size) == -1)) {
    // not a whole msg
    // have new coming msg
    if((BAMBOO_MSG_AVAIL() != 0) && !msgdatafull) {
      goto msg;
    } else {
      return -1;
    }
  }

  if(msglength <= size) {
    // have some whole msg
    MSGTYPE type;
    type = msgdata[msgdataindex]; //[0]
    MSG_INDEXINC_I();
    msgdatafull = false;
    switch(type) {
#ifdef TASK
    case TRANSOBJ: {
      // receive a object transfer msg
      processmsg_transobj_I();
      break;
    }   // case TRANSOBJ
#endif // TASK

    case TRANSTALL: {
      // receive a stall msg
      processmsg_transtall_I();
      break;
    }   // case TRANSTALL

#ifdef TASK
// GC version have no lock msgs
#ifndef MULTICORE_GC
    case LOCKREQUEST: {
      // receive lock request msg, handle it right now
      processmsg_lockrequest_I();
      break;
    }   // case LOCKREQUEST

    case LOCKGROUNT: {
      // receive lock grount msg
      processmsg_lockgrount_I();
      break;
    }   // case LOCKGROUNT

    case LOCKDENY: {
      // receive lock deny msg
      processmsg_lockdeny_I();
      break;
    }   // case LOCKDENY

    case LOCKRELEASE: {
      processmsg_lockrelease_I();
      break;
    }   // case LOCKRELEASE
#endif // #ifndef MULTICORE_GC

#ifdef PROFILE
    case PROFILEOUTPUT: {
      // receive an output profile data request msg
      processmsg_profileoutput_I();
      break;
    }   // case PROFILEOUTPUT

    case PROFILEFINISH: {
      // receive a profile output finish msg
      processmsg_profilefinish_I();
      break;
    }   // case PROFILEFINISH
#endif // #ifdef PROFILE

// GC version has no lock msgs
#ifndef MULTICORE_GC
    case REDIRECTLOCK: {
      // receive a redirect lock request msg, handle it right now
      processmsg_redirectlock_I();
      break;
    }   // case REDIRECTLOCK

    case REDIRECTGROUNT: {
      // receive a lock grant msg with redirect info
      processmsg_redirectgrount_I();
      break;
    }   // case REDIRECTGROUNT

    case REDIRECTDENY: {
      // receive a lock deny msg with redirect info
      processmsg_redirectdeny_I();
      break;
    }   // case REDIRECTDENY

    case REDIRECTRELEASE: {
      // receive a lock release msg with redirect info
      processmsg_redirectrelease_I();
      break;
    }   // case REDIRECTRELEASE
#endif // #ifndef MULTICORE_GC
#endif // TASK

    case STATUSCONFIRM: {
      // receive a status confirm info
      processmsg_statusconfirm_I();
      break;
    }   // case STATUSCONFIRM

    case STATUSREPORT: {
      processmsg_statusreport_I();
      break;
    }   // case STATUSREPORT

    case TERMINATE: {
      // receive a terminate msg
      processmsg_terminate_I();
      break;
    }   // case TERMINATE

    case MEMREQUEST: {
      processmsg_memrequest_I();
      break;
    }   // case MEMREQUEST

    case MEMRESPONSE: {
      processmsg_memresponse_I();
      break;
    }   // case MEMRESPONSE

#ifdef MULTICORE_GC
    // GC msgs
    case GCSTARTPRE: {
      processmsg_gcstartpre_I();
      break;
    }   // case GCSTARTPRE
	
	case GCSTARTINIT: {
      processmsg_gcstartinit_I();
      break;
    }   // case GCSTARTINIT

    case GCSTART: {
      // receive a start GC msg
      processmsg_gcstart_I();
      break;
    }   // case GCSTART

    case GCSTARTCOMPACT: {
      // a compact phase start msg
      processmsg_gcstartcompact_I();
      break;
    }   // case GCSTARTCOMPACT

	case GCSTARTMAPINFO: {
      // received a flush phase start msg
      processmsg_gcstartmapinfo_I();
      break;
    }   // case GCSTARTFLUSH

    case GCSTARTFLUSH: {
      // received a flush phase start msg
      processmsg_gcstartflush_I();
      break;
    }   // case GCSTARTFLUSH

    case GCFINISHPRE: {
      processmsg_gcfinishpre_I();
      break;
    }   // case GCFINISHPRE
	
	case GCFINISHINIT: {
      processmsg_gcfinishinit_I();
      break;
    }   // case GCFINISHINIT

    case GCFINISHMARK: {
      processmsg_gcfinishmark_I();
      break;
    }   // case GCFINISHMARK

    case GCFINISHCOMPACT: {
      // received a compact phase finish msg
      processmsg_gcfinishcompact_I();
      break;
    }   // case GCFINISHCOMPACT

	case GCFINISHMAPINFO: {
      processmsg_gcfinishmapinfo_I();
      break;
    }   // case GCFINISHMAPINFO

    case GCFINISHFLUSH: {
      processmsg_gcfinishflush_I();
      break;
    }   // case GCFINISHFLUSH

    case GCFINISH: {
      // received a GC finish msg
      gcphase = FINISHPHASE;
      break;
    }   // case GCFINISH

    case GCMARKCONFIRM: {
      // received a marked phase finish confirm request msg
      // all cores should do mark
      processmsg_gcmarkconfirm_I();
      break;
    }   // case GCMARKCONFIRM

    case GCMARKREPORT: {
      processmsg_gcmarkreport_I();
      break;
    }   // case GCMARKREPORT

    case GCMARKEDOBJ: {
      processmsg_gcmarkedobj_I();
      break;
    }   // case GCMARKEDOBJ

    case GCMOVESTART: {
      // received a start moving objs msg
      processmsg_gcmovestart_I();
      break;
    }   // case GCMOVESTART

    case GCMAPREQUEST: {
      // received a mapping info request msg
      processmsg_gcmaprequest_I();
      break;
    }   // case GCMAPREQUEST

    case GCMAPINFO: {
      // received a mapping info response msg
      processmsg_gcmapinfo_I();
      break;
    }   // case GCMAPINFO

    case GCMAPTBL: {
      // received a mapping tbl response msg
      processmsg_gcmaptbl_I();
      break;
    }   // case GCMAPTBL
	
	case GCLOBJREQUEST: {
      // received a large objs info request msg
      transferMarkResults_I();
      break;
    }   // case GCLOBJREQUEST

    case GCLOBJINFO: {
      // received a large objs info response msg
      processmsg_gclobjinfo_I();
      break;
    }   // case GCLOBJINFO

    case GCLOBJMAPPING: {
      // received a large obj mapping info msg
      processmsg_gclobjmapping_I();
      break;
    }  // case GCLOBJMAPPING

#ifdef GC_PROFILE
	case GCPROFILES: {
      // received a gcprofiles msg
      processmsg_gcprofiles_I();
      break;
    }
#endif // GC_PROFILE

#ifdef GC_CACHE_ADAPT
	case GCSTARTPREF: {
      // received a gcstartpref msg
      processmsg_gcstartpref_I();
      break;
    }

	case GCFINISHPREF: {
      // received a gcfinishpref msg
      processmsg_gcfinishpref_I();
      break;
    }
#endif // GC_CACHE_ADAPT
#endif // #ifdef MULTICORE_GC

    default:
      break;
    }  // switch(type)
    msglength = BAMBOO_MSG_BUF_LENGTH;

    if((msgdataindex != msgdatalast) || (msgdatafull)) {
      // still have available msg
      goto processmsg;
    }
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT(0xe88d);
#endif

    // have new coming msg
    if(BAMBOO_MSG_AVAIL() != 0) {
      goto msg;
    } // TODO

#ifdef TASK
#ifdef PROFILE_INTERRUPT
  if(!interruptInfoOverflow) {
    interruptInfoArray[interruptInfoIndex]->endTime=BAMBOO_GET_EXE_TIME();
    interruptInfoIndex++;
    if(interruptInfoIndex == INTERRUPTINFOLENGTH) {
      interruptInfoOverflow = true;
    }
  }
#endif
#endif // TASK
    return (int)type;
  } else {
    // not a whole msg
#ifndef CLOSE_PRINT
    BAMBOO_DEBUGPRINT(0xe88e);
#endif
    return -2;
  }
}

#endif // MULTICORE
