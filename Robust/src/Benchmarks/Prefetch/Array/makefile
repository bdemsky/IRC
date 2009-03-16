MAINCLASS=Array
SRC1=${MAINCLASS}.java
FLAGS=-dsm -prefetch -dsmcaching -optimize -excprefetch Array.Array -mainclass ${MAINCLASS} -trueprob 0.98
FLAGS1=-dsm -dsmcaching -optimize -mainclass ${MAINCLASS} -trueprob 0.98
FLAGS2=-dsm -optimize -mainclass ${MAINCLASS}
default:
	../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPNC ${SRC1}
	../../../buildscript ${FLAGS1} -o ${MAINCLASS}NPC ${SRC1}
#	../../../buildscript ${FLAGS} -o ${MAINCLASS}P ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
