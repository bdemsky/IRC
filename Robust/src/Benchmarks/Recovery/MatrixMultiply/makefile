MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}N.java 	 

FLAGS= -recovery -recoverystats -dsm -32bit -nooptimize -debug -dsmtask -mainclass ${MAINCLASS}

default:
	../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
