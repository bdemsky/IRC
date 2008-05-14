MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}N.java
SRC2=${MAINCLASS}Nrun.java
FLAGS=-dsm -prefetch -optimize -debug -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -mainclass ${MAINCLASS} -trueprob 0.90
FLAGS2=-dsm -optimize -debug -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -mainclass ${MAINCLASS}
default:
	../../../buildscript ${FLAGS2} -o ${MAINCLASS}NNP ${SRC1}
	../../../buildscript ${FLAGS} -o ${MAINCLASS}N ${SRC1}
	../../../buildscript ${FLAGS2} -o ${MAINCLASS}1NP ${SRC2}
	../../../buildscript ${FLAGS} -o ${MAINCLASS}1 ${SRC2}
	cp ${MAINCLASS}1NP.bin ${MAINCLASS}2NP.bin
	cp ${MAINCLASS}1.bin ${MAINCLASS}2.bin
	cp ${MAINCLASS}1NP.bin ${MAINCLASS}3NP.bin
	cp ${MAINCLASS}1.bin ${MAINCLASS}3.bin
	cp ${MAINCLASS}1NP.bin ${MAINCLASS}4NP.bin
	cp ${MAINCLASS}1.bin ${MAINCLASS}4.bin

clean:
	rm *.bin
	rm -rf tmpbuilddirectory
