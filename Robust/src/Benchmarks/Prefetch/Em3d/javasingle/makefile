MAINCLASS=Em3d
SRC=${MAINCLASS}2.java \
	${MAINCLASS}Wrap.java \
	BiGraph2.java \
	Node2.java \
	EVector.java 
default:
	../../../../buildscript -optimize -thread -mainclass ${MAINCLASS} ${SRC} -o ${MAINCLASS}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
