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
FLAGS1=-dsm -dsmcaching -optimize -mainclass ${MAINCLASS} -o ${MAINCLASS}NP -trueprob 0.75

default:
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPNC ${SRC}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}N  ${SRC}

clean:
	rm -rf tmpbuilddirectory/
	rm *.bin
