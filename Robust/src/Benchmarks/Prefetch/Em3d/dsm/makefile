MAINCLASS=Em3d
SRC=${MAINCLASS}.java \
	BiGraph.java \
	Node.java \
	Barrier.java
FLAGS=-dsm -prefetch -optimize -profile -debug -mainclass ${MAINCLASS} -o ${MAINCLASS}
FLAGS2=-dsm -optimize -printflat -profile -debug -mainclass ${MAINCLASS} -o ${MAINCLASS}NP

default:
	../../../../buildscript ${FLAGS2} ${SRC}
	../../../../buildscript ${FLAGS} ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
