MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}N.java
FLAGS=-dsm -dsmcaching -prefetch -optimize -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -mainclass ${MAINCLASS} -trueprob 0.98
FLAGS1=-dsm -dsmcaching -rangeprefetch -optimize -mainclass ${MAINCLASS} -trueprob 0.98
FLAGS2=-dsm -optimize -mainclass ${MAINCLASS}
default:
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPNC ${SRC1}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}N ${SRC1}
	../../../../buildscript ${FLAGS1} -o ${MAINCLASS}RangePN ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
