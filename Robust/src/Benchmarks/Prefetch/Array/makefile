MAINCLASS=Array2D
SRC1=${MAINCLASS}.java
SRC2=ArrayEx.java
FLAGS=-dsm -prefetch -dsmcaching -optimize -excprefetch Array2D.run -excprefetch Array2D.main -mainclass ${MAINCLASS} -trueprob 0.98
FLAGS1=-dsm -dsmcaching -optimize -mainclass ${MAINCLASS} -trueprob 0.98
FLAGS2=-dsm -optimize -mainclass ${MAINCLASS}
default:
	../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPNC ${SRC2}
#	../../../buildscript ${FLAGS1} -o ${MAINCLASS}NPC ${SRC2}
	../../../buildscript ${FLAGS} -o ${MAINCLASS}N ${SRC2}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
