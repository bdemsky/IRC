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

default:
	cpp ${MAINCLASS}.java > tmp1${MAINCLASS}.java
	./extractLines
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}RangeN ${SRC}

clean:
	rm tmp1RainForest.java
	rm tmpRainForest.java
	rm -rf tmpbuilddirectory
	rm *.bin
