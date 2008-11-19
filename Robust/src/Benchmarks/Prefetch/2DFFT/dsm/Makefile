MAINCLASS=fft2d
SRC=${MAINCLASS}.java \
	fft1d.java \
	Matrix.java
FLAGS =-dsm -dsmcaching -prefetch -optimize -excprefetch fft2d.main -excprefetch fft2d.twiddle -excprefetch fft1d.factorize -excprefetch fft1d.printFactors -excprefetch Matrix.setValues -excprefetch Matrix.setZeros -excprefetch fft2d.transpose -trueprob 0.90 -mainclass ${MAINCLASS}
FLAGS1=-dsm -optimize -mainclass ${MAINCLASS}

default:
	../../../../buildscript ${FLAGS1} ${SRC} -o ${MAINCLASS}NPNC
	../../../../buildscript ${FLAGS} ${SRC} -o ${MAINCLASS}N

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
