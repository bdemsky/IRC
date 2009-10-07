MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}N.java 	 
FLAGS=-dsm -dsmcaching -prefetch -optimize -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -mainclass ${MAINCLASS} -trueprob 0.98
FLAGS1=-dsm -optimize -dsmcaching -mainclass ${MAINCLASS}
FLAGS2=-dsm -optimize -mainclass ${MAINCLASS}
FLAGS3= -dsm -32bit -nooptimize -recovery -debug -mainclass ${MAINCLASS}
default:
	../../../../buildscript ${FLAGS3} -o ${MAINCLASS} ${SRC1} 

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
