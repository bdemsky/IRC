MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}N.java
SRC2=${MAINCLASS}Nrun.java
FLAGS=-dsm -prefetch -optimize -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -mainclass ${MAINCLASS} -trueprob 0.90
FLAGS2=-dsm -optimize -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -mainclass ${MAINCLASS}
default:
	../../../buildscript ${FLAGS2} -o ${MAINCLASS}NNP ${SRC1}
	../../../buildscript ${FLAGS} -o ${MAINCLASS}N ${SRC1}
#	../../../buildscript ${FLAGS2} -o ${MAINCLASS}NNP ${SRC2}
#	../../../buildscript ${FLAGS} -o ${MAINCLASS}N ${SRC2}
	cp ${MAINCLASS}NNP.bin ${MAINCLASS}1NP.bin
	cp ${MAINCLASS}N.bin ${MAINCLASS}1.bin
	cp ${MAINCLASS}NNP.bin ${MAINCLASS}2NP.bin
	cp ${MAINCLASS}N.bin ${MAINCLASS}2.bin
	cp ${MAINCLASS}NNP.bin ${MAINCLASS}3NP.bin
	cp ${MAINCLASS}N.bin ${MAINCLASS}3.bin
	cp ${MAINCLASS}NNP.bin ${MAINCLASS}4NP.bin
	cp ${MAINCLASS}N.bin ${MAINCLASS}4.bin

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
