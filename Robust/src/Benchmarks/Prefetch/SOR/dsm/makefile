MAINCLASS=JGFSORBenchSizeA
SRC=${MAINCLASS}.java \
	JGFSORBench.java \
	JGFInstrumentor.java \
	JGFTimer.java \
	SORRunner.java
FLAGS=-dsm -prefetch -nooptimize -debug -profile -excprefetch JGFSORBench.RandomMatrix -excprefetch JGFSORBench.init_sync -excprefetch JGFSORBench.JGFkernel -mainclass ${MAINCLASS} -trueprob 0.8
FLAGS2=-dsm -nooptimize -debug -profile -mainclass ${MAINCLASS}

default:
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NP ${SRC}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}  ${SRC}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}1NP ${SRC}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}1  ${SRC}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}2NP ${SRC}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}2  ${SRC}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}4NP ${SRC}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}4  ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
