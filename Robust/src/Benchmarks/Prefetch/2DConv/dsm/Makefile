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
FLAGSP=-dsm -dsmcaching -prefetch -optimize -excprefetch Image.initKernel -excprefetch Image.setValues -excprefetch Convolution.main -trueprob 0.90 -mainclass ${MAINCLASS}
FLAGSNPNC=-dsm -optimize -mainclass ${MAINCLASS}
FLAGSNPC=-dsm -optimize -dsmcaching -mainclass ${MAINCLASS}

default:
	../../../../buildscript ${FLAGS1} ${SRC4} -o ${MAINCLASS}withstatNPNC
	../../../../buildscript ${FLAGS2} ${SRC4} -o ${MAINCLASS}withstatNPC
	../../../../buildscript ${FLAGS} ${SRC4} -o ${MAINCLASS}withstatN
	../../../../buildscript ${FLAGSNPNC} ${SRC4} -o ${MAINCLASS}NPNC
	../../../../buildscript ${FLAGSNPC} ${SRC4} -o ${MAINCLASS}NPC
	../../../../buildscript ${FLAGSP} ${SRC4} -o ${MAINCLASS}N

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
