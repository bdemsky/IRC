MAINCLASS=JGFMolDynBenchSizeA
SRC=${MAINCLASS}.java \
JGFInstrumentor.java \
JGFTimer.java \
JGFMolDynBench.java \
DoubleWrapper.java \
IntWrapper.java \
MDWrap.java
FLAGS=-dsm -dsmcaching -prefetch -optimize -excprefetch particle.force -excprefetch particle.domove -excprefetch particle.mkekin -excprefetch TournamentBarrier.DoBarrier -excprefetch JGFMolDynBench.JGFvalidate -excprefetch JGFMolDynBench.JGFapplication -excprefetch JGFMolDynBench.JGFinitialise -excprefetch mdRunner.init -mainclass ${MAINCLASS} -o ${MAINCLASS} -trueprob 0.75
FLAGS2=-dsm -optimize -mainclass ${MAINCLASS} -o ${MAINCLASS}NP

default:
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPNC ${SRC}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}P  ${SRC}
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}1NPNC.bin
	cp ${MAINCLASS}P.bin ${MAINCLASS}1.bin
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}2NPNC.bin
	cp ${MAINCLASS}P.bin ${MAINCLASS}2.bin
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}3NPNC.bin
	cp ${MAINCLASS}P.bin ${MAINCLASS}3.bin
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}4NPNC.bin
	cp ${MAINCLASS}P.bin ${MAINCLASS}4.bin

clean:
	rm -rf tmpbuilddirectory/
	rm *.bin
