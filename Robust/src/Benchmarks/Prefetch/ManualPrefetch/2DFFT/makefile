MAINCLASS=fft2d
SRC=${MAINCLASS}.java \
	fft1d.java \
	Matrix.java \
	../../../../ClassLibrary/JavaDSM/Barrier.java
FLAGS =-dsm -dsmcaching -rangeprefetch -optimize -trueprob 0.90 -mainclass ${MAINCLASS}

default:
	../../../../buildscript ${FLAGS} ${SRC} -o ${MAINCLASS}RangeN

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
