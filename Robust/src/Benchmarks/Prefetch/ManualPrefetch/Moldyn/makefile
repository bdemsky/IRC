MAINCLASS=JGFMolDynBenchSizeB
SRC=${MAINCLASS}.java \
	JGFInstrumentor.java \
	JGFTimer.java \
	JGFMolDynBench.java \
	DoubleWrapper.java \
	IntWrapper.java \
	MDWrap.java \
	../../../../ClassLibrary/JavaDSM/Barrier.java
FLAGS=-dsm -dsmcaching -rangeprefetch -optimize -mainclass ${MAINCLASS} -trueprob 0.75
FLAGS1=-dsm -dsmcaching -optimize -mainclass ${MAINCLASS} -trueprob 0.75
FLAGS2=-dsm -optimize -mainclass ${MAINCLASS} -trueprob 0.75

default:
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}RangeN  ${SRC}
	../../../../buildscript ${FLAGS1} -o ${MAINCLASS}NPC  ${SRC}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPNC  ${SRC}

clean:
	rm -rf tmpbuilddirectory/
	rm *.bin
