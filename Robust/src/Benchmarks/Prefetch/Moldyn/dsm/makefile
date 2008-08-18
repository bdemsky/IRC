MAINCLASS=JGFMolDynBenchSizeA
SRC=${MAINCLASS}.java \
JGFInstrumentor.java \
JGFTimer.java \
JGFMolDynBench.java
FLAGS=-dsm -dsmcaching -prefetch -excprefetch particle.force -excprefetch particle.domove -excprefetch particle.mkekin -excprefetch TournamentBarrier.DoBarrier -excprefetch JGFMolDynBench.JGFvalidate -excprefetch JGFMolDynBench.JGFapplication -optimize -mainclass ${MAINCLASS} -o ${MAINCLASS} -trueprob 0.8
FLAGS2=-dsm -optimize -mainclass ${MAINCLASS} -o ${MAINCLASS}NP

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
	rm -rf tmpbuilddirectory/
	rm *.bin
