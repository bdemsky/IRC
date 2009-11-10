MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}N.java 	 

FLAGS= -recovery -dsm -32bit -nooptimize -recovery -debug -dsmtask -mainclass ${MAINCLASS}

default:
	../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
