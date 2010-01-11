MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}D3.java
FLAGS1=-dsm -transstats -dsmcaching -rangeprefetch -optimize -mainclass ${MAINCLASS} -trueprob 0.98
FLAGS2=-dsm -transstats -optimize -mainclass ${MAINCLASS}
FLAGS3=-dsm -optimize -transstats -dsmcaching -mainclass ${MAINCLASS}
default:
	../../../../buildscript ${FLAGS1} -o ${MAINCLASS}RangeN ${SRC1}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPNC ${SRC1}
	../../../../buildscript ${FLAGS3} -o ${MAINCLASS}NPC ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
