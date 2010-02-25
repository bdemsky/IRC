MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}.java 	 

FLAGS= -recovery -recoverystats -dsm -32bit -optimize -dsmtask -mainclass ${MAINCLASS}

default:
	../../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
