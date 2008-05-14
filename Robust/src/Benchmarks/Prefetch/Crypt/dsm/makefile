MAINCLASS=JGFCryptBenchSizeA
SRC=${MAINCLASS}.java \
jgfutil/JGFInstrumentor.java \
jgfutil/JGFTimer.java \
crypt/JGFCryptBench.java
FLAGS=-dsm -prefetch -excprefetch JGFCryptBench.buildTestData -excprefetch JGFCryptBench.JGFvalidate -excprefetch JGFCryptBench.calcDecryptKey -excprefetch JGFCryptBench.freeTestData -excprefetch JGFCryptBench.calcEncryptKey -excprefetch JGFCryptBenchSizeA.main -excprefetch JGFCryptBench.JGFinitialise -excprefetch JGFCryptBench.JGFvalidate -optimize -debug -profile -mainclass ${MAINCLASS} -o ${MAINCLASS} -trueprob 0.8
FLAGS2=-dsm -optimize -debug -profile -mainclass ${MAINCLASS} -o ${MAINCLASS}NP

default:
	../../../../buildscript ${FLAGS2} ${SRC}
	../../../../buildscript ${FLAGS} ${SRC}

clean:
	rm *.bin
	rm -rf tmpbuildirectory

