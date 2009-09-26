MAINCLASS=JGFSORBenchSizeC
MAINCLASS2=JGFSORBenchSizeD
SRC1=${MAINCLASS}.java \
JGFInstrumentor.java \
JGFTimer.java \
JGFSORBench.java \
SORRunner.java
SRC2=${MAINCLASS2}.java \
JGFInstrumentor.java \
JGFTimer.java \
JGFSORBench.java \
SORRunner.java


FLAGS2=-thread -optimize -mainclass ${MAINCLASS2} -o ${MAINCLASS2}
FLAGS=-thread -optimize -mainclass ${MAINCLASS} -o ${MAINCLASS}

default:
	../../../../buildscript ${FLAGS} ${SRC1}
	../../../../buildscript ${FLAGS2} ${SRC2}

clean:
	rm -rf tmpbuilddirectory/
	rm *.bin
