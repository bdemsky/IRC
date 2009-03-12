MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}N.java
FLAGS1=-dsm -dsmcaching -rangeprefetch -optimize -mainclass ${MAINCLASS} -trueprob 0.98
default:
	../../../../buildscript ${FLAGS1} -o ${MAINCLASS}RangeN ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
