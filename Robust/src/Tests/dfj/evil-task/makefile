PROGRAM=Test

SOURCE_FILES=test.java

BUILDSCRIPT=../../../buildscript

COREPROFOVERFLOW= #-coreprof-checkoverflow
USECOREPROF= #-coreprof $(COREPROFOVERFLOW) \
	-coreprof-eventwords 1024*1024*128 \
	-coreprof-enable cpe_main \
	-coreprof-enable cpe_runmalloc \
	-coreprof-enable cpe_runfree \
	-coreprof-enable cpe_count_poolalloc \
	-coreprof-enable cpe_count_poolreuse \
	-coreprof-enable cpe_workschedgrab \
	-coreprof-enable cpe_taskdispatch \
	-coreprof-enable cpe_taskexecute \
	-coreprof-enable cpe_taskretire
#	-coreprof-enable cpe_taskstallvar \
#	-coreprof-enable cpe_taskstallmem


DISJOINT= -disjoint -disjoint-k 1 -enable-assertions #-disjoint-desire-determinism

USEOOO= -ooojava  8 2               -ooodebug -squeue
USERCR= -ooojava  7 2 -rcr -pointer -ooodebug -squeue 

BSFLAGS= -64bit -mainclass $(PROGRAM) -heapsize-mb 1024 -garbagestats -noloop -joptimize -debug #-ooodebug-disable-task-mem-pool -justanalyze


all: ooo


single:
	$(BUILDSCRIPT) $(BSFLAGS) -thread -o $(PROGRAM)s -builddir sing $(SOURCE_FILES) 


ooo: $(PROGRAM)p.bin

$(PROGRAM)p.bin: $(SOURCE_FILES) makefile
	$(BUILDSCRIPT) $(BSFLAGS) $(USECOREPROF) $(USEOOO) $(DISJOINT) -o $(PROGRAM)p -builddir par  $(SOURCE_FILES) 

rcr: $(PROGRAM)r.bin

$(PROGRAM)r.bin: $(SOURCE_FILES) makefile
	$(BUILDSCRIPT) $(BSFLAGS) $(USECOREPROF) $(USERCR) $(DISJOINT) -o $(PROGRAM)r -builddir rcr  $(SOURCE_FILES) 


clean:
	rm -f  $(PROGRAM)p.bin $(PROGRAM)r.bin $(PROGRAM)s.bin
	rm -fr par rcr sing
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  *.txt
	rm -f  aliases.txt
	rm -f  mlpReport*txt
	rm -f  results*txt
	rm -f  coreprof.dat
