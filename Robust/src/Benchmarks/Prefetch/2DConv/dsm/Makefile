MAINCLASS=Convolution
SRC=${MAINCLASS}.java
FLAGS =-dsm -dsmcaching -prefetch -optimize -excprefetch Image.initKernel -excprefetch Image.setValues -excprefetch Convolution.main -trueprob 0.95 -mainclass ${MAINCLASS}
FLAGS1=-dsm -optimize -mainclass ${MAINCLASS}

default:
	../../../../buildscript ${FLAGS1} ${SRC} -o ${MAINCLASS}NPNC
	../../../../buildscript ${FLAGS} ${SRC} -o ${MAINCLASS}N

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
