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
#	../../../../buildscript ${FLAGS1} -o ${MAINCLASS}NP ${SRC}
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
	rm -rf tmpbuilddirectory/
	rm *.bin
