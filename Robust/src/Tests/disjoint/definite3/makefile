PROGRAM=Test

SOURCE_FILES=test.java

BUILDSCRIPT=../../../buildscript

DISJOINT= -disjoint -disjoint-k 1 -enable-assertions -do-definite-reach-analysis -disjoint-count-graph-elements -disjoint-write-dots final

BSFLAGS= -justanalyze -mainclass $(PROGRAM) -heapsize-mb 1024 -noloop -joptimize -debug #-flatirusermethods


all:
	$(BUILDSCRIPT) -thread $(BSFLAGS) $(DISJOINT) -o $(PROGRAM)s -builddir sing $(SOURCE_FILES)

clean:
	rm -f  $(PROGRAM)s.bin
	rm -fr sing
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  *.txt
	rm -f  aliases.txt
	rm -f  mlpReport*txt
	rm -f  results*txt
	rm -f  coreprof.dat
