MAINCLASS=Em3d
SRC=${MAINCLASS}.java \
	BiGraph.java \
	Node.java \
	Barrier.java
SRC1=${MAINCLASS}Nold.java \
	BiGraphNold.java \
	Node.java \
	Barrier.java
SRC3=${MAINCLASS}2.java \
	${MAINCLASS}Wrap.java \
	BiGraph2.java \
	Node2.java \
	EVector.java 

FLAGS=-dsm -prefetch -optimize -excprefetch Em3d.main -excprefetch BiGraph.create -excprefetch Node.Node -excprefetch Node.fillTable -excprefetch Node.makeUniqueNeighbors -excprefetch Node.makeFromNodes -excprefetch Node.updateFromNodes -debug -mainclass ${MAINCLASS} -trueprob 0.91
FLAGS1=-dsm -prefetch -optimize -excprefetch Node.makeUniqueNeighbors -excprefetch EVector.ensureCapacity -excprefetch EVector.addElement -excprefetch BiGraph.makeFromNodes -excprefetch EVector.elementAt -excprefetch BiGraph.initializeNodes -excprefetch BiGraph.allocateNodes -mainclass ${MAINCLASS} -trueprob 0.91
FLAGS2=-dsm -optimize -mainclass ${MAINCLASS}

default:
#	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NP ${SRC}
#	../../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NNP ${SRC3}
	../../../../buildscript ${FLAGS1} -o ${MAINCLASS}N ${SRC3}
	cp ${MAINCLASS}NNP.bin ${MAINCLASS}1NP.bin
	cp ${MAINCLASS}N.bin ${MAINCLASS}1.bin
	cp ${MAINCLASS}NNP.bin ${MAINCLASS}2NP.bin
	cp ${MAINCLASS}N.bin ${MAINCLASS}2.bin
	cp ${MAINCLASS}NNP.bin ${MAINCLASS}3NP.bin
	cp ${MAINCLASS}N.bin ${MAINCLASS}3.bin
	cp ${MAINCLASS}NNP.bin ${MAINCLASS}4NP.bin
	cp ${MAINCLASS}N.bin ${MAINCLASS}4.bin

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
