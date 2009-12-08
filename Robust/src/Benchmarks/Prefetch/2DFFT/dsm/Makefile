MAINCLASS=fft2d
SRC=${MAINCLASS}.java \
	fft1d.java \
	Matrix.java \
	../../../../ClassLibrary/JavaDSM/Barrier.java
SRC1=${MAINCLASS}rect.java \
	fft1d.java \
	Matrix1.java \
	../../../../ClassLibrary/JavaDSM/Barrier.java
FLAGS =-dsm -dsmcaching -transstats -prefetch -optimize -excprefetch fft2d.main -excprefetch fft2d.twiddle -excprefetch fft1d.factorize -excprefetch fft1d.printFactors -excprefetch Matrix.setValues -excprefetch Matrix.setZeros -excprefetch fft2d.transpose -trueprob 0.95 -mainclass ${MAINCLASS}
FLAGS1=-dsm -optimize -transstats -mainclass ${MAINCLASS}
FLAGS2=-dsm -optimize -transstats -dsmcaching -mainclass ${MAINCLASS}

default:
#../../../../buildscript ${FLAGS1} ${SRC} -o ${MAINCLASS}NPNC
#	../../../../buildscript ${FLAGS2} ${SRC} -o ${MAINCLASS}NPC
#	../../../../buildscript ${FLAGS} ${SRC} -o ${MAINCLASS}N
	../../../../buildscript ${FLAGS1} ${SRC1} -o ${MAINCLASS}NPNC
#	../../../../buildscript ${FLAGS2} ${SRC} -o ${MAINCLASS}NPC
	../../../../buildscript ${FLAGS} ${SRC1} -o ${MAINCLASS}N

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
