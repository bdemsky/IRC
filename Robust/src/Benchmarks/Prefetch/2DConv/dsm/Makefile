MAINCLASS=Convolution
SRC=${MAINCLASS}.java
SRC1=${MAINCLASS}SizeB.java
FLAGS =-dsm -dsmcaching -prefetch -optimize -excprefetch Image.initKernel -excprefetch Image.setValues -excprefetch Convolution.main -trueprob 0.90 -mainclass ${MAINCLASS}
FLAGS1=-dsm -optimize -mainclass ${MAINCLASS}

default:
#	../../../../buildscript ${FLAGS1} ${SRC} -o ${MAINCLASS}NPNC
#	../../../../buildscript ${FLAGS} ${SRC} -o ${MAINCLASS}N
	../../../../buildscript ${FLAGS1} ${SRC1} -o ${MAINCLASS}NPNC
	../../../../buildscript ${FLAGS} ${SRC1} -o ${MAINCLASS}N

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
