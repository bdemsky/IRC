MAINCLASS=KMeans
PROGRAM=test
BUILDSCRIPT=../../../buildscript
SOURCE_FILES=${MAINCLASS}.java \
	Random.java \
	Cluster.java \
    Normal.java \
    Common.java \
    GlobalArgs.java 

USEOOO= -ooojava 24 2 -ooodebug 
BSFLAGS= -garbagestats -64bit -mainclass $(MAINCLASS) -joptimize -noloop -optimize
DISJOINT= -disjoint -disjoint-k 1 -enable-assertions

default:
	$(BUILDSCRIPT) -nojava $(USEOOO) $(BSFLAGS) $(DISJOINT) -o $(PROGRAM)p $(SOURCE_FILES) -builddir par

single:
	$(BUILDSCRIPT) $(BSFLAGS) -thread -o $(PROGRAM)s -builddir sing $(SOURCE_FILES) 

ooo:
	$(BUILDSCRIPT) $(USEOOO) $(BSFLAGS) $(DISJOINT) -o $(PROGRAM)p -builddir par $(SOURCE_FILES) 


clean:
	rm *.bin
	rm -fr par sing
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  *.txt
	rm -f  aliases.txt
	rm -f  mlpReport*txt
	rm -f  results*txt
