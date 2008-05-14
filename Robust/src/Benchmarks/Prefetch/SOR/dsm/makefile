MAINCLASS=JGFSORBenchSizeA
SRC=${MAINCLASS}.java \
	JGFSORBench.java \
	JGFInstrumentor.java \
	JGFTimer.java \
	SORRunner.java
FLAGS=-dsm -prefetch -nooptimize -debug -profile -excprefetch JGFSORBench.RandomMatrix -excprefetch JGFSORBench.init_sync -excprefetch JGFSORBench.JGFkernel -mainclass ${MAINCLASS} -trueprob 0.88
FLAGS2=-dsm -nooptimize -debug -profile -mainclass ${MAINCLASS}

default:
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NP ${SRC}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}P  ${SRC}
	cp ${MAINCLASS}NP.bin ${MAINCLASS}1NP.bin
	cp ${MAINCLASS}P.bin ${MAINCLASS}1.bin
	cp ${MAINCLASS}NP.bin ${MAINCLASS}2NP.bin
	cp ${MAINCLASS}P.bin ${MAINCLASS}2.bin
	cp ${MAINCLASS}NP.bin ${MAINCLASS}3NP.bin
	cp ${MAINCLASS}P.bin ${MAINCLASS}3.bin
	cp ${MAINCLASS}NP.bin ${MAINCLASS}4NP.bin
	cp ${MAINCLASS}P.bin ${MAINCLASS}4.bin

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
