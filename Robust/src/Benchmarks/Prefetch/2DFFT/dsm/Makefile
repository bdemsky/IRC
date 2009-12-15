MAINCLASS=fft2d
SRC=${MAINCLASS}.java \
	fft1d.java \
	Matrix.java \
	../../../../ClassLibrary/JavaDSM/Barrier.java
SRC1=${MAINCLASS}rect.java \
	fft1d.java \
	Matrix1.java \
	../../../../ClassLibrary/JavaDSM/Barrier.java
SRC2=${MAINCLASS}N.java \
	fft1dN.java \
	MatrixN.java \
	../../../../ClassLibrary/JavaDSM/Barrier.java

FLAGS =-dsm -dsmcaching -transstats -builddir tmpbuilddirectory2 -prefetch -optimize -excprefetch fft2d.main -excprefetch fft2d.twiddle -excprefetch fft1d.factorize -excprefetch fft1d.printFactors -excprefetch Matrix.setValues -excprefetch Matrix.setZeros -excprefetch fft2d.transpose -trueprob 0.90 -mainclass ${MAINCLASS}
FLAGS1=-dsm -optimize -transstats -builddir tmpbuilddirectory1 -mainclass ${MAINCLASS}
FLAGS2=-dsm -optimize -transstats -dsmcaching -mainclass ${MAINCLASS}

FLAGSP =-dsm -dsmcaching -builddir tmpbuilddirectory2 -prefetch -optimize -excprefetch fft2d.main -excprefetch fft2d.twiddle -excprefetch fft1d.factorize -excprefetch fft1d.printFactors -excprefetch Matrix.setValues -excprefetch Matrix.setZeros -excprefetch fft2d.transpose -trueprob 0.90 -mainclass ${MAINCLASS}
FLAGSNPNC=-dsm -optimize -builddir tmpbuilddirectory1 -mainclass ${MAINCLASS}
FLAGSNPC=-dsm -optimize -dsmcaching -mainclass ${MAINCLASS}


default:
	../../../../buildscript ${FLAGS1} ${SRC} -o ${MAINCLASS}withstatNPNC
	../../../../buildscript ${FLAGS2} ${SRC} -o ${MAINCLASS}withstatNPC
	../../../../buildscript ${FLAGS} ${SRC} -o ${MAINCLASS}withstatN
	../../../../buildscript ${FLAGSNPNC} ${SRC} -o ${MAINCLASS}NPNC
	../../../../buildscript ${FLAGSNPC} ${SRC} -o ${MAINCLASS}NPC
	../../../../buildscript ${FLAGSP} ${SRC} -o ${MAINCLASS}N

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
