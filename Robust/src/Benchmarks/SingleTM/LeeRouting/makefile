MAINCLASS=LeeRouter
SRC=${MAINCLASS}.java \
	Frontier.java \
	Grid.java \
	GridCell.java \
	LeeThread.java \
	WorkQueue.java 
FLAGS=-singleTM -optimize -profile -mainclass ${MAINCLASS} -joptimize -debug -garbagestats -transstats
default:
	../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
