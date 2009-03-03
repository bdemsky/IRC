MAINCLASS=RainForest
SRC=tmp${MAINCLASS}.java \
	Player.java \
	TreeType.java \
	GameMap.java \
	RockType.java \
	Barrier.java \
	Goal.java \
	Path.java \
	Node.java \
	AStarPathFinder.java 

FLAGS1=-dsm -optimize -transstats -mainclass ${MAINCLASS}
FLAGS2=-dsm -dsmcaching -optimize -mainclass ${MAINCLASS}
FLAGS3=-dsm -dsmcaching -prefetch -optimize -mainclass ${MAINCLASS} -trueprob 0.90
FLAGS4=-dsm -dsmcaching -rangeprefetch -optimize -mainclass ${MAINCLASS} -trueprob 0.90

default:
	cpp ${MAINCLASS}.java > tmp1${MAINCLASS}.java
	./extractLines
	../../../../buildscript ${FLAGS1} -o ${MAINCLASS}NPNC ${SRC}
	../../../../buildscript ${FLAGS3} -o ${MAINCLASS}N ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
