MAINCLASS=JGFSORBenchSizeA
SRC=${MAINCLASS}.java \
JGFSORBench.java \
JGFInstrumentor.java \
JGFTimer.java \
SORRunner.java
FLAGS=-dsm -prefetch -nooptimize -debug -profile -excprefetch JGFSORBench.RandomMatrix -excprefetch JGFSORBench.init_sync -excprefetch JGFSORBench.JGFkernel -mainclass ${MAINCLASS} -o ${MAINCLASS} -trueprob 0.8
FLAGS2=-dsm -nooptimize -debug -profile -mainclass ${MAINCLASS} -o ${MAINCLASS}NP

default:
	../../../../buildscript ${FLAGS2} ${SRC}
	../../../../buildscript ${FLAGS} ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
