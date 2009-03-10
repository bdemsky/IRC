MAINCLASS=Convolution
SRC2=${MAINCLASS}SizeC.java
FLAGS =-dsm -dsmcaching -rangeprefetch -optimize -trueprob 0.95 -mainclass ${MAINCLASS}

default:
	../../../../buildscript ${FLAGS} ${SRC2} -o ${MAINCLASS}RangeN

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
