MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}N.java
SRC2=${MAINCLASS}Nrun.java
FLAGS=-dsm -dsmcaching -prefetch -optimize -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -mainclass ${MAINCLASS} -trueprob 0.90
FLAGS1=-dsm -optimize -dsmcaching -mainclass ${MAINCLASS}
FLAGS2=-dsm -optimize -mainclass ${MAINCLASS}
default:
	../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPNC ${SRC1}
	../../../buildscript ${FLAGS1} -o ${MAINCLASS}NNP ${SRC1}
	../../../buildscript ${FLAGS} -o ${MAINCLASS}N ${SRC1}
	../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPNC200 ${SRC2}
	../../../buildscript ${FLAGS1} -o ${MAINCLASS}NNP200 ${SRC2}
	../../../buildscript ${FLAGS} -o ${MAINCLASS}N200 ${SRC2}
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}1NPNC.bin
	cp ${MAINCLASS}NNP.bin ${MAINCLASS}1NP.bin
	cp ${MAINCLASS}N.bin ${MAINCLASS}1.bin
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}2NPNC.bin
	cp ${MAINCLASS}NNP.bin ${MAINCLASS}2NP.bin
	cp ${MAINCLASS}N.bin ${MAINCLASS}2.bin
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}3NPNC.bin
	cp ${MAINCLASS}NNP.bin ${MAINCLASS}3NP.bin
	cp ${MAINCLASS}N.bin ${MAINCLASS}3.bin
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}4NPNC.bin
	cp ${MAINCLASS}NNP.bin ${MAINCLASS}4NP.bin
	cp ${MAINCLASS}N.bin ${MAINCLASS}4.bin
	cp ${MAINCLASS}NPNC200.bin ../MM200/${MAINCLASS}1NPNC.bin
	cp ${MAINCLASS}NNP200.bin ../MM200/${MAINCLASS}1NP.bin
	cp ${MAINCLASS}N200.bin ../MM200/${MAINCLASS}1.bin
	cp ${MAINCLASS}NPNC200.bin ../MM200/${MAINCLASS}2NPNC.bin
	cp ${MAINCLASS}NNP200.bin ../MM200/${MAINCLASS}2NP.bin
	cp ${MAINCLASS}N200.bin ../MM200/${MAINCLASS}2.bin
	cp ${MAINCLASS}NPNC200.bin ../MM200/${MAINCLASS}3NPNC.bin
	cp ${MAINCLASS}NNP200.bin ../MM200/${MAINCLASS}3NP.bin
	cp ${MAINCLASS}N200.bin ../MM200/${MAINCLASS}3.bin
	cp ${MAINCLASS}NPNC200.bin ../MM200/${MAINCLASS}4NPNC.bin
	cp ${MAINCLASS}NNP200.bin ../MM200/${MAINCLASS}4NP.bin
	cp ${MAINCLASS}N200.bin ../MM200/${MAINCLASS}4.bin

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
