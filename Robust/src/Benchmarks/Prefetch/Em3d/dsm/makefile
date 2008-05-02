MAINCLASS=Em3d
SRC=${MAINCLASS}.java \
	BiGraph.java \
	Node.java \
	Barrier.java
SRC1=${MAINCLASS}N.java \
	BiGraphN.java \
	Node.java \
	Barrier.java

FLAGS=-dsm -prefetch -excprefetch Em3d.main -excprefetch BiGraph.create -excprefetch Node.Node -excprefetch Node.fillTable -excprefetch Node.makeUniqueNeighbors -excprefetch Node.makeFromNodes -excprefetch Node.updateFromNodes -nooptimize -profile -debug -mainclass ${MAINCLASS}
FLAGS2=-dsm -nooptimize -profile -debug -mainclass ${MAINCLASS}

default:
#	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NP ${SRC}
#	../../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NNP ${SRC1}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}N ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
