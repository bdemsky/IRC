MAINCLASS=Array
SRC1=${MAINCLASS}.java
FLAGS=-dsm -prefetch -dsmcaching -optimize -excprefetch Array.Array -mainclass ${MAINCLASS} -trueprob 0.98
FLAGS1=-dsm -dsmcaching -optimize -mainclass ${MAINCLASS} -trueprob 0.98
FLAGS2=-dsm -optimize -mainclass ${MAINCLASS}
default:
	../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPNC ${SRC1}
	../../../buildscript ${FLAGS1} -o ${MAINCLASS}NP ${SRC1}
	../../../buildscript ${FLAGS} -o ${MAINCLASS}P ${SRC1}
	cp ${MAINCLASS}NPNC.bin ${MAINCLASS}1NPNC.bin
	cp ${MAINCLASS}NP.bin ${MAINCLASS}1NP.bin
	cp ${MAINCLASS}P.bin ${MAINCLASS}1.bin

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
