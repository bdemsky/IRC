MAINCLASS=fft2d
SRC=${MAINCLASS}N.java \
	fft1dN.java \
	MatrixN.java \
	../../../../ClassLibrary/JavaDSM/Barrier.java
FLAGS =-dsm -dsmcaching -transstats -rangeprefetch -optimize -trueprob 0.90 -mainclass ${MAINCLASS}
FLAGS2 =-dsm -dsmcaching -transstats -optimize -trueprob 0.90 -mainclass ${MAINCLASS}
FLAGS3 =-dsm -optimize -transstats -trueprob 0.90 -mainclass ${MAINCLASS}

default:
	../../../../buildscript ${FLAGS} ${SRC} -o ${MAINCLASS}RangeN
	../../../../buildscript ${FLAGS2} ${SRC} -o ${MAINCLASS}NPC
	../../../../buildscript ${FLAGS3} ${SRC} -o ${MAINCLASS}NPNC

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
