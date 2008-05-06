MAINCLASS=JGFSORBenchSizeA
SRC=${MAINCLASS}.java \
	JGFSORBench.java \
	JGFInstrumentor.java \
	JGFTimer.java \
	SORRunner.java
FLAGS=-dsm -prefetch -nooptimize -debug -profile -excprefetch JGFSORBench.RandomMatrix -excprefetch JGFSORBench.init_sync -excprefetch JGFSORBench.JGFkernel -mainclass ${MAINCLASS} -trueprob 0.80
FLAGS2=-dsm -nooptimize -debug -profile -mainclass ${MAINCLASS}

default:
#	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NP ${SRC}
#	../../../../buildscript ${FLAGS} -o ${MAINCLASS}  ${SRC}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}1NP ${SRC}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}1  ${SRC}
	cp ${MAINCLASS}1NP.bin ${MAINCLASS}2NP.bin
	cp ${MAINCLASS}1.bin ${MAINCLASS}2.bin
	cp ${MAINCLASS}1NP.bin ${MAINCLASS}4NP.bin
	cp ${MAINCLASS}1.bin ${MAINCLASS}4.bin

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
