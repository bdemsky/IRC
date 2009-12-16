MAINCLASS=JGFMolDynBenchSizeB
SRC=${MAINCLASS}.java \
	JGFInstrumentor.java \
	JGFTimer.java \
	JGFMolDynBench.java \
	DoubleWrapper.java \
	IntWrapper.java \
	MDWrap.java \
	../../../../ClassLibrary/JavaDSM/Barrier.java
FLAGS=-dsm -dsmcaching -transstats -prefetch -optimize -excprefetch particle.force -excprefetch particle.domove -excprefetch particle.mkekin -excprefetch TournamentBarrier.DoBarrier -excprefetch JGFMolDynBench.JGFvalidate -excprefetch JGFMolDynBench.JGFapplication -excprefetch JGFMolDynBench.JGFinitialise -excprefetch mdRunner.init -excprefetch mdRunner.doinit -excprefetch mdRunner.doinit2 -mainclass ${MAINCLASS} -trueprob 0.75
FLAGS2=-dsm -transstats -optimize -mainclass ${MAINCLASS}
FLAGS1=-dsm -dsmcaching -transstats -optimize -mainclass ${MAINCLASS} -trueprob 0.75

FLAGSP=-dsm -dsmcaching -prefetch -optimize -excprefetch particle.force -excprefetch particle.domove -excprefetch particle.mkekin -excprefetch TournamentBarrier.DoBarrier -excprefetch JGFMolDynBench.JGFvalidate -excprefetch JGFMolDynBench.JGFapplication -excprefetch JGFMolDynBench.JGFinitialise -excprefetch mdRunner.init -excprefetch mdRunner.doinit -excprefetch mdRunner.doinit2 -mainclass ${MAINCLASS} -trueprob 0.75
FLAGSNPNC=-dsm -optimize -mainclass ${MAINCLASS}
FLAGSNPC=-dsm -dsmcaching -optimize -mainclass ${MAINCLASS} -trueprob 0.75



default:
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}withstatNPNC ${SRC}
	../../../../buildscript ${FLAGS1} -o ${MAINCLASS}withstatNPC ${SRC}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}withstatN  ${SRC}
	../../../../buildscript ${FLAGSNPNC} -o ${MAINCLASS}NPNC ${SRC}
	../../../../buildscript ${FLAGSNPC} -o ${MAINCLASS}NPC ${SRC}
	../../../../buildscript ${FLAGSP} -o ${MAINCLASS}N  ${SRC}

clean:
	rm -rf tmpbuilddirectory/
	rm *.bin
