MAINCLASS=fft2d
SRC=${MAINCLASS}.java \
	fft1d.java \
	Matrix.java
FLAGS =-dsm -dsmcaching -prefetch -optimize -excprefetch fft2d.main -excprefetch fft2d.twiddle -excprefetch fft1d.factorize -excprefetch fft1d.printFactors -excprefetch Matrix.setValues -excprefetch Matrix.setZeros -trueprob 0.90 -mainclass ${MAINCLASS}
FLAGS1=-dsm -optimize -mainclass ${MAINCLASS}

default:
	../../../../buildscript ${FLAGS1} ${SRC} -o ${MAINCLASS}NPNC
	../../../../buildscript ${FLAGS} ${SRC} -o ${MAINCLASS}N
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}1NPNC.bin
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}2NPNC.bin
	cp ${MAINCLASS}N.bin ${MAINCLASS}2.bin
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}3NPNC.bin
	cp ${MAINCLASS}N.bin ${MAINCLASS}3.bin
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}4NPNC.bin
	cp ${MAINCLASS}N.bin ${MAINCLASS}4.bin
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}5NPNC.bin
	cp ${MAINCLASS}N.bin ${MAINCLASS}5.bin
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}6NPNC.bin
	cp ${MAINCLASS}N.bin ${MAINCLASS}6.bin
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}7NPNC.bin
	cp ${MAINCLASS}N.bin ${MAINCLASS}7.bin
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}8NPNC.bin
	cp ${MAINCLASS}N.bin ${MAINCLASS}8.bin

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
