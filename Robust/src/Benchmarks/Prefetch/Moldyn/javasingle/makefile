MAINCLASS=JGFMolDynBenchSizeA
SRC=${MAINCLASS}.java \
JGFInstrumentor.java \
JGFTimer.java \
JGFMolDynBench.java \
DoubleWrapper.java \
IntWrapper.java \
MDWrap.java

FLAGS2= -thread -optimize -mainclass ${MAINCLASS} -o ${MAINCLASS}

default:
	../../../../buildscript ${FLAGS2} ${SRC}

clean:
	rm -rf tmpbuilddirectory/
	rm *.bin
