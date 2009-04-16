MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}N.java 
FLAGS1=-singleTM -nooptimize -mainclass ${MAINCLASS} -joptimize -dcopts -debug
default:
	../../../buildscript ${FLAGS1} -o ${MAINCLASS}RangeN ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
