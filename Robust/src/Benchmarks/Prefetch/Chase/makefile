MAINCLASS=Chase
SRC1=${MAINCLASS}.java
FLAGS=-dsm -prefetch -dsmcaching -optimize -excprefetch Chase.main -excprefetch Chase.Chase -mainclass ${MAINCLASS} -trueprob 0.98
FLAGS2=-dsm -dsmcaching -optimize -mainclass ${MAINCLASS} -trueprob 0.99
FLAGS3=-dsm -optimize -mainclass ${MAINCLASS} -trueprob 0.99
#for testing no java option
#FLAGS=-dsm -prefetch -optimize -debug -excprefetch Chase.main -excprefetch Chase.Chase -mainclass ${MAINCLASS} -trueprob 0.99 -nojava
default:
	../../../buildscript ${FLAGS3} -o ${MAINCLASS}NPNC ${SRC1}
#	../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPC ${SRC1}
	../../../buildscript ${FLAGS} -o ${MAINCLASS}N ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
