#power
PROGRAM=test
MAINCLASS=Power
SOURCE_FILES=${MAINCLASS}.java

BUILDSCRIPT=../../../buildscript

USEOOO= -ooojava 8 2  -ooodebug  #-joptimize
BSFLAGS= -32bit -nooptimize  -debug -garbagestats  -mainclass $(MAINCLASS)
DISJOINT= -disjoint -disjoint-k 1 -enable-assertions 

default:
	$(BUILDSCRIPT) -nojava $(USEOOO) $(BSFLAGS) $(DISJOINT) -o $(PROGRAM)p $(SOURCE_FILES) -builddir par

single:
	$(BUILDSCRIPT) $(BSFLAGS) -o $(PROGRAM)s -builddir sing $(SOURCE_FILES) 

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
