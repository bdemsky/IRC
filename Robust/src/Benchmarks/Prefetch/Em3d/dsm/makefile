MAINCLASS=Em3d
SRC=${MAINCLASS}.java \
	BiGraph.java \
	Node.java \
	Barrier.java
FLAGS=-dsm -prefetch -excprefetch Em3d.main -excprefetch BiGraph.create -excprefetch Node.Node -excprefetch Node.fillTable -excprefetch Node.makeUniqueNeighbors -excprefetch Node.makeFromNodes -excprefetch Node.updateFromNodes -nooptimize -profile -debug -mainclass ${MAINCLASS} -o ${MAINCLASS}
FLAGS2=-dsm -nooptimize -profile -debug -mainclass ${MAINCLASS} -o ${MAINCLASS}NP

default:
	../../../../buildscript ${FLAGS2} ${SRC}
	../../../../buildscript ${FLAGS} ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
