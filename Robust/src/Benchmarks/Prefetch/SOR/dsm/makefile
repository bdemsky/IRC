#MAINCLASS=JGFSORBenchSizeA
#MAINCLASS=JGFSORBenchSizeC
MAINCLASS=JGFSORBenchSizeC
SRC=${MAINCLASS}.java \
	JGFSORBench.java \
	JGFInstrumentor.java \
	JGFTimer.java \
	SORRunner.java \
	SORWrap.java
FLAGS=-dsm -prefetch -dsmcaching -nooptimize -debug -excprefetch JGFSORBench.JGFSORBench -excprefetch JGFSORBenchSizeA.main -excprefetch JGFSORBench.RandomMatrix -excprefetch JGFSORBench.init_sync -excprefetch JGFSORBench.JGFkernel -mainclass ${MAINCLASS} -trueprob 0.72
FLAGS1=-dsm -dsmcaching -nooptimize -debug -mainclass ${MAINCLASS} 
FLAGS2=-dsm -optimize -mainclass ${MAINCLASS}

default:
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPNC ${SRC}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}N  ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
