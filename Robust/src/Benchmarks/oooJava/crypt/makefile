PROGRAM=JGFCryptBench

SOURCE_FILES=JGFCryptBench.java

BUILDSCRIPT=../../../buildscript


COREPROFOVERFLOW= #-coreprof-checkoverflow
USECOREPROF= -coreprof $(COREPROFOVERFLOW) \
	-coreprof-eventwords 1024*1024*128 \
	-coreprof-enable cpe_main \
	-coreprof-enable cpe_taskdispatch
#	-coreprof-enable cpe_runmalloc \
#	-coreprof-enable cpe_runfree \
#	-coreprof-enable cpe_count_poolalloc \
#	-coreprof-enable cpe_count_poolreuse \
#	-coreprof-enable cpe_workschedgrab \
#	-coreprof-enable cpe_taskexecute \
#	-coreprof-enable cpe_taskretire \
#	-coreprof-enable cpe_taskstallvar \
#	-coreprof-enable cpe_taskstallmem


USEOOO= -ooojava 24 2  -ooodebug -ooodebug-disable-task-mem-pool 
BSFLAGS= -64bit -mainclass $(PROGRAM)  -heapsize-mb 1024 -garbagestats -debug -joptimize -noloop -optimize 

DRELEASEMODE=-disjoint-release-mode -disjoint-alias-file aliases.txt tabbed
DISJOINT= -disjoint -disjoint-k 1 -enable-assertions $(DRELEASEMODE) #-disjoint-desire-determinism

default:
	$(BUILDSCRIPT) -nojava $(BSFLAGS) $(USECOREPROF) $(USEOOO) $(DISJOINT) -o $(PROGRAM)p -builddir par  $(SOURCE_FILES)

single:
	$(BUILDSCRIPT) -thread $(BSFLAGS) $(USECOREPROF)                       -o $(PROGRAM)s -builddir sing $(SOURCE_FILES) 

ooo:
	$(BUILDSCRIPT)         $(BSFLAGS) $(USECOREPROF) $(USEOOO) $(DISJOINT) -o $(PROGRAM)p -builddir par  $(SOURCE_FILES) 

clean:
	rm -f  $(PROGRAM)p.bin $(PROGRAM)s.bin
	rm -fr par sing
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  *.txt
	rm -f  aliases.txt
	rm -f  mlpReport*txt
	rm -f  results*txt
	rm -f coreprof.dat
	rm -f trace.out
