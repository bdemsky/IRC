MAINCLASS=KMeans
PROGRAM=test
BUILDSCRIPT=~/eclipse/workspaces/irvine_sep09/Robust/src/buildscript
SRC=${MAINCLASS}.java \
	Random.java \
	Cluster.java \
    Normal.java \
    Common.java \
    GlobalArgs.java 
#    \../../../ClassLibrary/JavaSTM/Barrier.java
    

#include ../common/Makefile.flags

#include ../common/Makefile.builds

#FLAGSSTATS=-mainclass ${MAINCLASS} -singleTM -optimize -debug -joptimize -32bit -abcclose -dcopts -transstats -arraypad -stmstats

USEMLP=  -mlp 8 2 -mlpdebug  # use to turn mlp on and off and make sure rest of build not broken
BSFLAGS=   -32bit -profile -nooptimize -debug -garbagestats -mainclass ${MAINCLASS} 
OWNERSHIP= -ownership -ownallocdepth 1 -enable-assertions  -methodeffects -flatirusermethods -ownwritedots final -ownaliasfile aliases.txt

default:
	../../../buildscript -nojava $(USEMLP) $(BSFLAGS) $(OWNERSHIP) -o $(PROGRAM)  ${SRC}

java:
	../../../buildscript $(USEMLP) $(BSFLAGS) $(OWNERSHIP) -o $(PROGRAM) ${SRC}

single:
	../../../buildscript $(BSFLAGS) -o $(PROGRAM) ${SRC}
	

prep:

clean:
	rm *.bin
	rm -fr tmpbuilddirectory
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  *.txt
	rm -f  aliases.txt
	rm -f  mlpReport*txt
	rm -f  results*txt
