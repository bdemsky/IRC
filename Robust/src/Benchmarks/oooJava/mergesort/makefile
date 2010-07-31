PROGRAM=MergeSort4

SOURCE_FILES=BMergeSort4.java

BUILDSCRIPT=../../../buildscript

USEOOO= -ooojava 64 2  -ooodebug  
BSFLAGS= -64bit -mainclass $(PROGRAM) -garbagestats -joptimize -noloop -optimize
DISJOINT= -disjoint -disjoint-k 1 -enable-assertions 

default:
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
