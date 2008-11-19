MAINCLASS=JGFSORBenchSizeA
SRC=${MAINCLASS}.java \
JGFInstrumentor.java \
JGFTimer.java \
JGFSORBench.java \
SORRunner.java

FLAGS2= -thread -optimize -mainclass ${MAINCLASS} -o ${MAINCLASS}NP

default:
	../../../../buildscript ${FLAGS2} ${SRC}

clean:
	rm -rf tmpbuilddirectory/
	rm *.bin
