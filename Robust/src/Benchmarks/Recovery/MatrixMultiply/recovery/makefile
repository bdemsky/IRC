MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}.java \

FLAGS= -recovery -recoverystats -prefetch -dsmcaching -dsm -32bit -optimize -dsmtask -mainclass ${MAINCLASS}
DSMFLAGS= -dsm -dsmtask -prefetch -dsmcaching -32bit -optimize -mainclass ${MAINCLASS}

default:
	../../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC1}
	../../../../buildscript ${DSMFLAGS} -o ${MAINCLASS}DSM ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
