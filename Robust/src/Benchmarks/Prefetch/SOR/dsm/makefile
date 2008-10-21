#MAINCLASS=JGFSORBenchSizeA
#MAINCLASS=JGFSORBenchSizeC
MAINCLASS=JGFSORBenchSizeD
SRC=${MAINCLASS}.java \
	JGFSORBench.java \
	JGFInstrumentor.java \
	JGFTimer.java \
	SORRunner.java \
	SORWrap.java
FLAGS=-dsm -prefetch -dsmcaching -nooptimize -debug -excprefetch JGFSORBench.JGFSORBench -excprefetch JGFSORBenchSizeA.main -excprefetch JGFSORBench.RandomMatrix -excprefetch JGFSORBench.init_sync -excprefetch JGFSORBench.JGFkernel -mainclass ${MAINCLASS} -trueprob 0.72
FLAGS1=-dsm -dsmcaching -nooptimize -debug -mainclass ${MAINCLASS} 
FLAGS2=-dsm -optimize -mainclass ${MAINCLASS}

default:
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPNC ${SRC}
#	../../../../buildscript ${FLAGS1} -o ${MAINCLASS}NP  ${SRC}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}P  ${SRC}
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}1NPNC.bin
#	cp ${MAINCLASS}NP.bin ${MAINCLASS}1NP.bin
	cp ${MAINCLASS}P.bin ${MAINCLASS}1.bin
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}2NPNC.bin
#	cp ${MAINCLASS}NP.bin ${MAINCLASS}2NP.bin
	cp ${MAINCLASS}P.bin ${MAINCLASS}2.bin
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}3NPNC.bin
#	cp ${MAINCLASS}NP.bin ${MAINCLASS}3NP.bin
	cp ${MAINCLASS}P.bin ${MAINCLASS}3.bin
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}4NPNC.bin
#	cp ${MAINCLASS}NP.bin ${MAINCLASS}4NP.bin
	cp ${MAINCLASS}P.bin ${MAINCLASS}4.bin
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}5NPNC.bin
#	cp ${MAINCLASS}NP.bin ${MAINCLASS}5NP.bin
	cp ${MAINCLASS}P.bin ${MAINCLASS}5.bin
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}6NPNC.bin
#	cp ${MAINCLASS}NP.bin ${MAINCLASS}6NP.bin
	cp ${MAINCLASS}P.bin ${MAINCLASS}6.bin
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}7NPNC.bin
#	cp ${MAINCLASS}NP.bin ${MAINCLASS}7NP.bin
	cp ${MAINCLASS}P.bin ${MAINCLASS}7.bin
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}8NPNC.bin
#	cp ${MAINCLASS}NP.bin ${MAINCLASS}8NP.bin
	cp ${MAINCLASS}P.bin ${MAINCLASS}8.bin

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
