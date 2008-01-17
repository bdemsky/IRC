MAINCLASS=JGFCryptBenchSizeA
SRC=${MAINCLASS}.java \
jgfutil/JGFInstrumentor.java \
jgfutil/JGFTimer.java \
crypt/IDEATest.java \
crypt/JGFCryptBench.java
FLAGS=-dsm -thread -mainclass ${MAINCLASS} -o ${MAINCLASS}

default:
	../../../../buildscript ${FLAGS} ${SRC}

