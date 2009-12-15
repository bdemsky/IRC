MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}N.java 	 
SRC2=MatrixMultiplyD3.java \
     ../../../../ClassLibrary/JavaDSM/Barrier.java
FLAGS=-dsm -transstats -dsmcaching -prefetch -optimize -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -mainclass ${MAINCLASS} -trueprob 0.98
FLAGS1=-dsm -optimize -transstats -dsmcaching -mainclass ${MAINCLASS}
FLAGS2=-dsm -optimize -transstats -mainclass ${MAINCLASS}

FLAGSP=-dsm -dsmcaching -prefetch -optimize -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -mainclass ${MAINCLASS} -trueprob 0.98
FLAGSNPC=-dsm -optimize -dsmcaching -mainclass ${MAINCLASS}
FLAGSNPNC=-dsm -optimize -mainclass ${MAINCLASS}

default:
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}withstatNPNC ${SRC2}
	../../../../buildscript ${FLAGS1} -o ${MAINCLASS}withstatNPC ${SRC2}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}withstatN ${SRC2}
	../../../../buildscript ${FLAGSNPNC} -o ${MAINCLASS}NPNC ${SRC2}
	../../../../buildscript ${FLAGSNPC} -o ${MAINCLASS}NPC ${SRC2}
	../../../../buildscript ${FLAGSP} -o ${MAINCLASS}N ${SRC2}
#	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPNC200 ${SRC2}
#	../../../../buildscript ${FLAGS} -o ${MAINCLASS}N200 ${SRC2}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
