MAINCLASS=JGFMolDynBenchSizeA
SRC=${MAINCLASS}.java \
JGFInstrumentor.java \
JGFTimer.java \
JGFMolDynBench.java \
TournamentBarrier.java
FLAGS=-dsm -prefetch -optimize -debug -profile -mainclass ${MAINCLASS} -o ${MAINCLASS} -trueprob 0.5
FLAGS2=-dsm -optimize -debug -profile -mainclass ${MAINCLASS} -o ${MAINCLASS}NP

default:
	../../../../buildscript ${FLAGS2} ${SRC}
	../../../../buildscript ${FLAGS} ${SRC}

clean:
	rm -rf tmpbuildirectory
	rm *.bin
