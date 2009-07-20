MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}N.java
SRC2=${MAINCLASS}Nrun.java
FLAGS=-dsm -dsmcaching -prefetch -optimize -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -mainclass ${MAINCLASS} -trueprob 0.98
FLAGS1=-dsm -optimize -dsmcaching -mainclass ${MAINCLASS}
FLAGS2=-dsm -optimize -mainclass ${MAINCLASS}
default:
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPNC ${SRC1}
	../../../../buildscript ${FLAGS1} -o ${MAINCLASS}NPC ${SRC1}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}N ${SRC1}
#	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPNC200 ${SRC2}
#	../../../../buildscript ${FLAGS} -o ${MAINCLASS}N200 ${SRC2}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
