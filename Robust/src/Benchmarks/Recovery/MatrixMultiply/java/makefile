MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}.java \
     Work.java \
     Task.java \
     GlobalQueue.java

FLAGS= -optimize -thread -mainclass ${MAINCLASS}
default:
	../../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
