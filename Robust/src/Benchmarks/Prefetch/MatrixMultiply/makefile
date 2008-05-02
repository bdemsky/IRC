MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}N.java
FLAGS=-dsm -prefetch -optimize -profile -debug -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -mainclass ${MAINCLASS} -trueprob 0.8
FLAGS2=-dsm -optimize -profile -debug -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -mainclass ${MAINCLASS}
default:
	../../../buildscript ${FLAGS2} -o ${MAINCLASS}NNP ${SRC1}
	../../../buildscript ${FLAGS} -o ${MAINCLASS}N ${SRC1}

clean:
	rm *.bin
	rm -rf tmpbuilddirectory
