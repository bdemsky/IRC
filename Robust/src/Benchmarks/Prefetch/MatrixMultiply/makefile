MAINCLASS=MatrixMultiply
SRC=${MAINCLASS}.java
FLAGS=-dsm -prefetch -optimize -profile -debug -mainclass ${MAINCLASS} -o ${MAINCLASS}
default:
	../../../buildscript ${FLAGS} ${SRC}
clean:
	rm *.bin
	rm -rf tmpbuilddirectory
