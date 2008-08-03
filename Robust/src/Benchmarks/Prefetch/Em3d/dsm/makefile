MAINCLASS=Em3d
SRC=${MAINCLASS}.java \
	BiGraph.java \
	Node.java \
	Barrier.java
SRC1=${MAINCLASS}Nold.java \
	BiGraphNold.java \
	Node.java \
	Barrier.java

FLAGS=-dsm -prefetch -optimize -transstats -excprefetch Em3d.main -excprefetch BiGraph.create -excprefetch Node.Node -excprefetch Node.fillTable -excprefetch Node.makeUniqueNeighbors -excprefetch Node.makeFromNodes -excprefetch Node.updateFromNodes -nooptimize -debug -mainclass ${MAINCLASS} -trueprob 0.91
FLAGS2=-dsm -optimize -transstats -mainclass ${MAINCLASS}

default:
#	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NP ${SRC}
#	../../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NNP ${SRC1}
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}N ${SRC1}
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
