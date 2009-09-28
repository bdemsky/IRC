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
	EVector.java \
    ../../../../ClassLibrary/JavaDSM/Barrier.java

FLAGS=-dsm -prefetch -optimize -excprefetch Em3d.main -excprefetch BiGraph.create -excprefetch Node.Node -excprefetch Node.fillTable -excprefetch Node.makeUniqueNeighbors -excprefetch Node.makeFromNodes -excprefetch Node.updateFromNodes -debug -mainclass ${MAINCLASS} -trueprob 0.85
FLAGS1=-dsm -prefetch -transstats -dsmcaching -optimize -excprefetch Node.makeUniqueNeighbors -excprefetch EVector.ensureCapacity -excprefetch EVector.addElement -excprefetch BiGraph.makeFromNodes -excprefetch EVector.elementAt -excprefetch BiGraph.initializeNodes -excprefetch BiGraph.allocateNodes -mainclass ${MAINCLASS} -trueprob 0.85
FLAGS2=-dsm -dsmcaching -optimize -mainclass ${MAINCLASS} -trueprob 0.85
FLAGS3=-dsm -optimize -mainclass ${MAINCLASS} -trueprob 0.85

default:
	../../../../buildscript ${FLAGS3} -o ${MAINCLASS}NPNC ${SRC3}
	../../../../buildscript ${FLAGS1} -o ${MAINCLASS}N ${SRC3}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
