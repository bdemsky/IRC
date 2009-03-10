MAINCLASS=JGFMolDynBenchSizeB
SRC=${MAINCLASS}.java \
	JGFInstrumentor.java \
	JGFTimer.java \
	JGFMolDynBench.java \
	DoubleWrapper.java \
	IntWrapper.java \
	MDWrap.java \
	../../../../ClassLibrary/JavaDSM/Barrier.java
FLAGS=-dsm -dsmcaching -prefetch -optimize -excprefetch particle.force -excprefetch particle.domove -excprefetch particle.mkekin -excprefetch TournamentBarrier.DoBarrier -excprefetch JGFMolDynBench.JGFvalidate -excprefetch JGFMolDynBench.JGFapplication -excprefetch JGFMolDynBench.JGFinitialise -excprefetch mdRunner.init -mainclass ${MAINCLASS} -trueprob 0.75
FLAGS2=-dsm -optimize -mainclass ${MAINCLASS}
FLAGS1=-dsm -dsmcaching -optimize -mainclass ${MAINCLASS} -trueprob 0.75

default:
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPNC ${SRC}
	../../../../buildscript ${FLAGS1} -o ${MAINCLASS}NPC ${SRC}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}N  ${SRC}

clean:
	rm -rf tmpbuilddirectory/
	rm *.bin
