MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}N.java
SRC2=${MAINCLASS}Nrun.java
SRC3=${MAINCLASS}D.java \
	../../../../ClassLibrary/JavaDSM/Barrier.java
SRC4=${MAINCLASS}D2.java \
	../../../../ClassLibrary/JavaDSM/Barrier.java
SRC5=${MAINCLASS}D3.java \
	../../../../ClassLibrary/JavaDSM/Barrier.java
FLAGS=-dsm -dsmcaching -optimize -prefetch -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -mainclass ${MAINCLASS} -trueprob 0.98
FLAGS1=-dsm -optimize -dsmcaching -mainclass ${MAINCLASS}
FLAGS2=-dsm -optimize -mainclass ${MAINCLASS}
default:
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPNC ${SRC3}
#	../../../../buildscript ${FLAGS1} -o ${MAINCLASS}NPC ${SRC1}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}N ${SRC3}
#	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPNC200 ${SRC2}
#	../../../../buildscript ${FLAGS} -o ${MAINCLASS}N200 ${SRC2}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
