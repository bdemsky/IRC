MAINCLASS=FileSystem
SRC1=${MAINCLASS}.java 
FLAGS= -recovery -recoverystats -dsm -dsmcaching -optimize -mainclass ${MAINCLASS}
default:
	../../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
