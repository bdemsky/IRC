#MAINCLASS=JGFSORBenchSizeA
#MAINCLASS=JGFSORBenchSizeC
MAINCLASS1=JGFSORBenchSizeC
MAINCLASS=JGFSORBenchSizeD
SRC=${MAINCLASS}.java \
	JGFSORBench.java \
	JGFInstrumentor.java \
	JGFTimer.java \
	SORRunner.java \
	SORWrap.java \
    ../../../../ClassLibrary/JavaDSM/Barrier.java
SRC1=${MAINCLASS1}.java \
	JGFSORBench.java \
	JGFInstrumentor.java \
	JGFTimer.java \
	SORRunner.java \
	SORWrap.java \
    ../../../../ClassLibrary/JavaDSM/Barrier.java

FLAGS=-dsm -prefetch -transstats -dsmcaching -optimize -excprefetch JGFSORBench.JGFSORBench -excprefetch JGFSORBenchSizeA.main -excprefetch JGFSORBench.RandomMatrix -excprefetch JGFSORBench.init_sync -excprefetch JGFSORBench.JGFkernel -trueprob 0.72
FLAGS1=-dsm -dsmcaching -optimize -debug -mainclass ${MAINCLASS} 
FLAGS2=-dsm -optimize

default:
	../../../../buildscript ${FLAGS2} -mainclass ${MAINCLASS} -o ${MAINCLASS}NPNC ${SRC}
	../../../../buildscript ${FLAGS} -mainclass ${MAINCLASS} -o ${MAINCLASS}N  ${SRC}
	../../../../buildscript ${FLAGS2} -mainclass ${MAINCLASS1} -o ${MAINCLASS1}NPNC ${SRC1}
	../../../../buildscript ${FLAGS} -mainclass ${MAINCLASS1} -o ${MAINCLASS1}N  ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
