MAINCLASS=MatrixMultiply
SRC=${MAINCLASS}.java
FLAGS=-dsm -prefetch -optimize -printflat -profile -debug -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -mainclass ${MAINCLASS} -o ${MAINCLASS} -trueprob 0.5
FLAGS2=-dsm -optimize -printflat -profile -debug -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -mainclass ${MAINCLASS} -o ${MAINCLASS}NP
default:
	../../../buildscript ${FLAGS2} ${SRC}
	../../../buildscript ${FLAGS} ${SRC}

clean:
	rm *.bin
	rm -rf tmpbuilddirectory
