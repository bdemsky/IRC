MAINCLASS=JGFLUFactBenchSizeA
SRC=${MAINCLASS}.java \
JGFLUFactBench.java \
JGFInstrumentor.java \
JGFTimer.java \
Barrier.java \
LinpackRunner.java
FLAGS=-dsm -prefetch -nooptimize -debug -profile -excprefetch JGFLUFactBench.JGFkernel -excprefetch JGFLUFactBench.dmxpy -excprefetch JGFLUFactBench.JGFvalidate -excprefetch JGFLUFactBench.JGFinitialise -excprefetch JGFLUFactBench.matgen -excprefetch JGFLUFactBench.dgesl -mainclass ${MAINCLASS} -trueprob 0.95
FLAGS2=-dsm -nooptimize -debug -profile -mainclass ${MAINCLASS}

default:
#../../../../buildscript ${FLAGS2} ${SRC}
#	../../../../buildscript ${FLAGS} ${SRC}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}1NP ${SRC}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}1  ${SRC}
	cp ${MAINCLASS}1NP.bin ${MAINCLASS}2NP.bin
	cp ${MAINCLASS}1.bin ${MAINCLASS}2.bin
	cp ${MAINCLASS}1NP.bin ${MAINCLASS}3NP.bin
	cp ${MAINCLASS}1.bin ${MAINCLASS}3.bin
	cp ${MAINCLASS}1NP.bin ${MAINCLASS}4NP.bin
	cp ${MAINCLASS}1.bin ${MAINCLASS}4.bin

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
