MAINCLASS=JGFCryptBenchSizeA
SRC=${MAINCLASS}.java \
jgfutil/JGFInstrumentor.java \
jgfutil/JGFTimer.java \
crypt/IDEATest.java \
crypt/JGFCryptBench.java
FLAGS=-dsm -prefetch -nooptimize -debug -mainclass ${MAINCLASS} -o ${MAINCLASS}

default:
	../../../../buildscript ${FLAGS} ${SRC}

clean:
	rm *.bin
	rm -rf tmpbuildirectory

