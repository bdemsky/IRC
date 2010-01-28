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

FLAGS=-dsm -dsmcaching -rangeprefetch -optimize -mainclass ${MAINCLASS} -trueprob 0.90
FLAGS2=-dsm -dsmcaching -optimize -mainclass ${MAINCLASS} -trueprob 0.90
FLAGS3=-dsm -optimize -mainclass ${MAINCLASS} -trueprob 0.90

default:
	cpp ${MAINCLASS}.java > tmp1${MAINCLASS}.java
	./extractLines
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}RangeN ${SRC}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPC ${SRC}
	../../../../buildscript ${FLAGS3} -o ${MAINCLASS}NPNC ${SRC}

clean:
	rm tmp1RainForest.java
	rm tmpRainForest.java
	rm -rf tmpbuilddirectory
	rm *.bin
