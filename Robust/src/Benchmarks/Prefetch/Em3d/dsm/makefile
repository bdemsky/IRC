MAINCLASS=Em3d
SRC=${MAINCLASS}.java \
	BiGraph.java \
	Node.java \
	Barrier.java
SRC1=${MAINCLASS}1.java \
	BiGraph.java \
	Node.java \
	Barrier.java
SRC2=${MAINCLASS}2.java \
	BiGraph.java \
	Node.java \
	Barrier.java
SRC4=${MAINCLASS}4.java \
	BiGraph.java \
	Node.java \
	Barrier.java
FLAGS=-dsm -prefetch -excprefetch Em3d.main -excprefetch BiGraph.create -excprefetch Node.Node -excprefetch Node.fillTable -excprefetch Node.makeUniqueNeighbors -excprefetch Node.makeFromNodes -excprefetch Node.updateFromNodes -nooptimize -profile -debug -mainclass ${MAINCLASS}
FLAGS2=-dsm -nooptimize -profile -debug -mainclass ${MAINCLASS}

default:
#	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NP ${SRC}
#	../../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}1NP ${SRC1}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}1 ${SRC1}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}2NP ${SRC2}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}2 ${SRC2}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}4NP ${SRC4}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}4 ${SRC4}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
