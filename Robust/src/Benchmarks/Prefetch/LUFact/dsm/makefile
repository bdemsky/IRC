MAINCLASS=JGFLUFactBenchSizeA
SRC=${MAINCLASS}.java \
JGFLUFactBench.java \
JGFInstrumentor.java \
JGFTimer.java \
Barrier.java \
Linpack.java \
LinpackRunner.java
FLAGS=-dsm -prefetch -nooptimize -debug -profile -excprefetch JGFLUFactBench.JGFkernel -excprefetch JGFLUFactBench.dmxpy -excprefetch JGFLUFactBench.JGFvalidate -excprefetch JGFLUFactBench.JGFinitialise -excprefetch JGFLUFactBench.matgen -excprefetch JGFLUFactBench.dgesl -mainclass ${MAINCLASS} -o ${MAINCLASS} -trueprob 0.8
FLAGS2=-dsm -nooptimize -debug -profile -mainclass ${MAINCLASS} -o ${MAINCLASS}NP

default:
	../../../../buildscript ${FLAGS2} ${SRC}
	../../../../buildscript ${FLAGS} ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
