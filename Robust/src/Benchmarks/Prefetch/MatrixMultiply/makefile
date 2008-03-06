MAINCLASS=MatrixMultiply
SRC=${MAINCLASS}.java
FLAGS=-dsm -prefetch -nooptimize -debug -mainclass ${MAINCLASS} -o ${MAINCLASS}
default:
	../../../buildscript ${FLAGS} ${SRC}
clean:
	rm *.bin
	rm -rf tmpbuilddirectory
