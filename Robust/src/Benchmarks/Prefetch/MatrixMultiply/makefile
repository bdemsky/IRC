MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}1.java
SRC2=${MAINCLASS}2.java
SRC4=${MAINCLASS}4.java
FLAGS=-dsm -prefetch -optimize -profile -debug -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -mainclass ${MAINCLASS} -trueprob 0.8
FLAGS2=-dsm -optimize -profile -debug -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -mainclass ${MAINCLASS}
default:
	../../../buildscript ${FLAGS2} -o ${MAINCLASS}1NP ${SRC1}
	../../../buildscript ${FLAGS} -o ${MAINCLASS}1 ${SRC1}
	../../../buildscript ${FLAGS2} -o ${MAINCLASS}2NP ${SRC2}
	../../../buildscript ${FLAGS} -o ${MAINCLASS}2 ${SRC2}
	../../../buildscript ${FLAGS2} -o ${MAINCLASS}4NP ${SRC4}
	../../../buildscript ${FLAGS} -o ${MAINCLASS}4 ${SRC4}

clean:
	rm *.bin
	rm -rf tmpbuilddirectory
