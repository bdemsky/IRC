PROGRAM=MergeSort4

SOURCE_FILES=BMergeSort4.java

BUILDSCRIPT=../../../buildscript

COREPROFOVERFLOW= #-coreprof-checkoverflow
USECOREPROF= #-coreprof $(COREPROFOVERFLOW) \
	-coreprof-eventwords 1024*1024*128 \
	-coreprof-enable cpe_main \
	-coreprof-enable cpe_taskexecute \
	-coreprof-enable cpe_taskdispatch \
	-coreprof-enable cpe_rcr_traverse
#	-coreprof-enable cpe_runmalloc \
#	-coreprof-enable cpe_taskretire \
	-coreprof-enable cpe_workschedsubmit \
	-coreprof-enable cpe_workschedgrab \
	-coreprof-enable cpe_poolalloc \
	-coreprof-enable cpe_preparememq \
	-coreprof-enable cpe_runfree \
	-coreprof-enable cpe_count_poolalloc \
	-coreprof-enable cpe_count_poolreuse \
	-coreprof-enable cpe_taskstallvar \
	-coreprof-enable cpe_taskstallmem


USEOOO= -ooojava 64 2 -ooodebug -squeue #-ooodebug-disable-task-mem-pool
USERCR= -ooojava 64 2 -rcr -ooodebug -squeue
BSFLAGS= -64bit -mainclass $(PROGRAM)  -heapsize-mb 8000 -garbagestats -joptimize -noloop -optimize -debug #-nooptimize #src-after-pp #-debug

DRELEASEMODE=-disjoint-release-mode -disjoint-dvisit-stack-callees-on-top -disjoint-alias-file aliases.txt tabbed
DISJOINT= -disjoint -disjoint-k 1 -enable-assertions $(DRELEASEMODE) #-disjoint-desire-determinism

default:
	$(BUILDSCRIPT) -nojava $(BSFLAGS) $(USECOREPROF) $(USEOOO) $(DISJOINT) -o $(PROGRAM)p -builddir par  $(SOURCE_FILES)

rcr-remake-c:
	$(BUILDSCRIPT) -nojava $(BSFLAGS) $(USECOREPROF) $(USERCR) $(DISJOINT) -o $(PROGRAM)r -builddir rcr  $(SOURCE_FILES) 


single: $(PROGRAM)s.bin

$(PROGRAM)s.bin: $(SOURCE_FILES) ../master-makefile
	$(BUILDSCRIPT) -thread $(BSFLAGS) $(USECOREPROF)                       -o $(PROGRAM)s -builddir sing $(SOURCE_FILES) 

ooo: $(PROGRAM)p.bin

$(PROGRAM)p.bin: $(SOURCE_FILES) ../master-makefile
	$(BUILDSCRIPT)         $(BSFLAGS) $(USECOREPROF) $(USEOOO) $(DISJOINT) -o $(PROGRAM)p -builddir par  $(SOURCE_FILES) 

rcr: $(PROGRAM)r.bin

$(PROGRAM)r.bin: $(SOURCE_FILES) ../master-makefile
	$(BUILDSCRIPT)         $(BSFLAGS) $(USECOREPROF) $(USERCR) $(DISJOINT) -o $(PROGRAM)r -builddir rcr  $(SOURCE_FILES) 

clean:
	rm -f  $(PROGRAM)p.bin $(PROGRAM)s.bin $(PROGRAM)r.bin
	rm -fr par sing rcr
	rm -f tmp.c
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  *.txt
	rm -f  aliases.txt
	rm -f  mlpReport*txt
	rm -f  results*txt
	rm -f coreprof.dat
	rm -f trace.out
