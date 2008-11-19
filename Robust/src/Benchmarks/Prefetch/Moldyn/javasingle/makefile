MAINCLASS=JGFMolDynBenchSizeA
SRC=${MAINCLASS}.java \
JGFInstrumentor.java \
JGFTimer.java \
JGFMolDynBench.java \
Barrier.java

FLAGS2= -thread -optimize -mainclass ${MAINCLASS} -o ${MAINCLASS}NP

default:
	../../../../buildscript ${FLAGS2} ${SRC}

clean:
	rm -rf tmpbuilddirectory/
	rm *.bin
