MAINCLASS=Convolution
SRC2=${MAINCLASS}Rect.java
FLAGS =-dsm -dsmcaching -transstats -rangeprefetch -optimize -trueprob 0.95 -mainclass ${MAINCLASS}
FLAGS1=-dsm -transstats -optimize -mainclass ${MAINCLASS}
FLAGS2=-dsm -optimize -transstats -dsmcaching -mainclass ${MAINCLASS}

default:
	../../../../buildscript ${FLAGS} ${SRC2} -o ${MAINCLASS}RangeN
#	../../../../buildscript ${FLAGS1} ${SRC2} -o ${MAINCLASS}NPNC
#	../../../../buildscript ${FLAGS2} ${SRC2} -o ${MAINCLASS}NPC

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
