MAINCLASS=JGFLUFactBenchSizeA
SRC=${MAINCLASS}.java \
JGFLUFactBench.java \
JGFInstrumentor.java \
JGFTimer.java \
LinpackRunner.java
FLAGS=-dsm -prefetch -dsmcaching -optimize -excprefetch JGFLUFactBench.JGFLUFactBench -excprefetch JGFLUFactBench.JGFkernel -excprefetch JGFLUFactBench.dmxpy -excprefetch JGFLUFactBench.JGFvalidate -excprefetch JGFLUFactBench.JGFinitialise -excprefetch JGFLUFactBench.matgen -excprefetch JGFLUFactBench.dgesl -mainclass ${MAINCLASS} -trueprob 0.90
FLAGS2=-dsm -optimize -mainclass ${MAINCLASS}

default:
#../../../../buildscript ${FLAGS2} ${SRC}
#	../../../../buildscript ${FLAGS} ${SRC}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPNC ${SRC}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}N  ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
