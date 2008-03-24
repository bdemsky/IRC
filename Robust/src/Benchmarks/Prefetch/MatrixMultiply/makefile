MAINCLASS=MatrixMultiply
SRC=${MAINCLASS}.java
FLAGS=-dsm -prefetch -nooptimize -printflat -profile -debug -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -mainclass ${MAINCLASS} -o ${MAINCLASS}
default:
	../../../buildscript ${FLAGS} ${SRC}
clean:
	rm *.bin
	rm -rf tmpbuilddirectory
