MAINCLASS=JGFLUFactBenchSizeA
SRC=${MAINCLASS}.java \
JGFLUFactBench.java \
JGFInstrumentor.java \
JGFTimer.java \
TournamentBarrier.java \
Linpack.java \
LinpackRunner.java
FLAGS=-dsm -prefetch -optimize -debug -profile -excprefetch JGFLUFactBench.JGFkernel -excprefetch JGFLUFactBench.dmxpy -excprefetch JGFLUFactBench.JGFvalidate -excprefetch JGFLUFactBench.JGFinitialise -excprefetch JGFLUFactBench.matgen -excprefetch JGFLUFactBench.dgesl -mainclass ${MAINCLASS} -o ${MAINCLASS} -trueprob 0.8
FLAGS2=-dsm -optimize -debug -profile -mainclass ${MAINCLASS} -o ${MAINCLASS}NP

default:
	../../../../buildscript ${FLAGS2} ${SRC}
	../../../../buildscript ${FLAGS} ${SRC}

clean:
	rm -rf tmpbuildirectory
	rm *.bin
