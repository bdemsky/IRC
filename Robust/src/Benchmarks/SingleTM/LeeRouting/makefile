MAINCLASS=LeeRouter
SRC=${MAINCLASS}.java \
	Frontier.java \
	Grid.java \
	GridCell.java \
	LeeThread.java \
	WorkQueue.java 
FLAGS=-mainclass ${MAINCLASS} -joptimize -debug -singleTM -optimize -dcopts -transstats -abcclose -stmdebug -stmstats
default:
	../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
