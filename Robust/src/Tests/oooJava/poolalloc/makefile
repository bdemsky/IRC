PROGRAM=Test

SOURCE_FILES=test.java

BUILDSCRIPT=../../../buildscript

USEOOO= -ooojava 24 2  -ooodebug  
BSFLAGS= -64bit -mainclass $(PROGRAM) -heapsize-mb 1024 -garbagestats -noloop -nooptimize -coreprof -coreprof-checkoverflow -coreprof -coreprof-eventwords 1024*1024*128 -debug -ooodebug-disable-task-mem-pool
DISJOINT= -disjoint -disjoint-k 1 -enable-assertions #-disjoint-desire-determinism

all: ooo

remakec:
	$(BUILDSCRIPT) -nojava $(USEOOO) $(BSFLAGS) $(DISJOINT) -o $(PROGRAM)p $(SOURCE_FILES) -builddir par

single:
	$(BUILDSCRIPT) $(BSFLAGS) -thread -o $(PROGRAM)s -builddir sing $(SOURCE_FILES) 

ooo:
	$(BUILDSCRIPT) $(USEOOO) $(BSFLAGS) $(DISJOINT) -o $(PROGRAM)p -builddir par $(SOURCE_FILES) 

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
	rm -f  coreprof.dat
