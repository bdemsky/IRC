MAINCLASS=FileSystem
SRC1=${MAINCLASS}.java 
FLAGS= -recoverystats -dsm -dsmtask -recovery -nooptimize -mainclass ${MAINCLASS}
default:
	../../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
