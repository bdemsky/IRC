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
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}1NPNC ${SRC}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}1  ${SRC}
	cp ${MAINCLASS}1NPNC.bin ${MAINCLASS}2NPNC.bin
	cp ${MAINCLASS}1.bin ${MAINCLASS}2.bin
	cp ${MAINCLASS}1NPNC.bin ${MAINCLASS}3NPNC.bin
	cp ${MAINCLASS}1.bin ${MAINCLASS}3.bin
	cp ${MAINCLASS}1NPNC.bin ${MAINCLASS}4NPNC.bin
	cp ${MAINCLASS}1.bin ${MAINCLASS}4.bin

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
