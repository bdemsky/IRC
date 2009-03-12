MAINCLASS=Convolution
SRC=${MAINCLASS}.java
SRC1=${MAINCLASS}SizeB.java
SRC2=${MAINCLASS}SizeC.java
FLAGS =-dsm -dsmcaching -prefetch -optimize -excprefetch Image.initKernel -excprefetch Image.setValues -excprefetch Convolution.main -trueprob 0.95 -mainclass ${MAINCLASS}
FLAGS1=-dsm -optimize -mainclass ${MAINCLASS}
FLAGS2=-dsm -optimize -dsmcaching -mainclass ${MAINCLASS}

default:
#	../../../../buildscript ${FLAGS1} ${SRC} -o ${MAINCLASS}NPNC
#	../../../../buildscript ${FLAGS} ${SRC} -o ${MAINCLASS}N
#	../../../../buildscript ${FLAGS1} ${SRC1} -o ${MAINCLASS}NPNC
#	../../../../buildscript ${FLAGS} ${SRC1} -o ${MAINCLASS}N
	../../../../buildscript ${FLAGS1} ${SRC2} -o ${MAINCLASS}NPNC
	../../../../buildscript ${FLAGS2} ${SRC2} -o ${MAINCLASS}NPC
#	../../../../buildscript ${FLAGS} ${SRC2} -o ${MAINCLASS}N

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
