MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}N.java
FLAGS=-dsm -prefetch -optimize -debug -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -mainclass ${MAINCLASS} -trueprob 0.7
FLAGS2=-dsm -optimize -debug -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -mainclass ${MAINCLASS}
default:
	../../../buildscript ${FLAGS2} -o ${MAINCLASS}NP ${SRC1}
	../../../buildscript ${FLAGS} -o ${MAINCLASS}P ${SRC1}

clean:
	rm *.bin
	rm -rf tmpbuilddirectory
