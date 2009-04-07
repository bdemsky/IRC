MAINCLASS=LeeRouter
SRC=${MAINCLASS}.java \
	Frontier.java \
	Grid.java \
	GridCell.java \
	LeeThread.java \
	WorkQueue.java 
FLAGS=-singleTM -nooptimize -debug -mainclass ${MAINCLASS}
default:
	../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
