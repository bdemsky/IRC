MAINCLASS=Em3d
SRC=${MAINCLASS}.java \
	BiGraph.java \
	Node.java \
	Barrier.java
SRC1=${MAINCLASS}N.java \
	BiGraphN.java \
	Node.java \
	Barrier.java

FLAGS=-dsm -prefetch -excprefetch Em3d.main -excprefetch BiGraph.create -excprefetch Node.Node -excprefetch Node.fillTable -excprefetch Node.makeUniqueNeighbors -excprefetch Node.makeFromNodes -excprefetch Node.updateFromNodes -nooptimize -debug -mainclass ${MAINCLASS}
FLAGS2=-dsm -nooptimize -debug -mainclass ${MAINCLASS}

default:
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NP ${SRC1}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}P ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
