MAINCLASS=Convolution
SRC=${MAINCLASS}.java
SRC1=${MAINCLASS}SizeB.java
SRC2=${MAINCLASS}SizeC.java
SRC3=${MAINCLASS}S.java \
     ../../../../ClassLibrary/JavaDSM/Barrier.java
SRC4=${MAINCLASS}Rect.java
FLAGS =-dsm -dsmcaching -prefetch -transstats -optimize -excprefetch Image.initKernel -excprefetch Image.setValues -excprefetch Convolution.main -trueprob 0.90 -mainclass ${MAINCLASS}
FLAGS1=-dsm -transstats -optimize -mainclass ${MAINCLASS}
FLAGS2=-dsm -optimize -transstats -dsmcaching -mainclass ${MAINCLASS}

default:
#	../../../../buildscript ${FLAGS1} ${SRC} -o ${MAINCLASS}NPNC
#	../../../../buildscript ${FLAGS} ${SRC} -o ${MAINCLASS}N
#	../../../../buildscript ${FLAGS1} ${SRC1} -o ${MAINCLASS}NPNC
#	../../../../buildscript ${FLAGS} ${SRC1} -o ${MAINCLASS}N
#	../../../../buildscript ${FLAGS1} ${SRC2} -o ${MAINCLASS}NPNC
#	../../../../buildscript ${FLAGS2} ${SRC2} -o ${MAINCLASS}NPC
#	../../../../buildscript ${FLAGS} ${SRC2} -o ${MAINCLASS}N
#	../../../../buildscript ${FLAGS1} ${SRC3} -o ${MAINCLASS}NPNC
#	../../../../buildscript ${FLAGS2} ${SRC3} -o ${MAINCLASS}NPC
#	../../../../buildscript ${FLAGS} ${SRC3} -o ${MAINCLASS}N
	../../../../buildscript ${FLAGS1} ${SRC4} -o ${MAINCLASS}NPNC
#	../../../../buildscript ${FLAGS2} ${SRC4} -o ${MAINCLASS}NPC
	../../../../buildscript ${FLAGS} ${SRC4} -o ${MAINCLASS}N

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
