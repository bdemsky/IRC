MAINCLASS=JGFSORBenchSizeA
SRC=${MAINCLASS}.java  JGFInstrumentor.java JGFTimer.java JGFSORBench.java SOR.java Random.java
FLAGS=-dsm -prefetch -nooptimize -debug -mainclass ${MAINCLASS} -o ${MAINCLASS}
default:
	../../../buildscript ${FLAGS} ${SRC}
