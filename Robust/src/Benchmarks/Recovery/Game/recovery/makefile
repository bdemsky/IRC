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
    
FLAGS1=-dsm -dsmcaching -recoverystats -recovery -transstats -optimize -mainclass ${MAINCLASS}
DSMFLAGS=-dsm -dsmcaching -transstats -optimize -mainclass ${MAINCLASS}

default:
	cpp ${MAINCLASS}.java > tmp1${MAINCLASS}.java
	./extractLines
	../../../../buildscript ${FLAGS1} -o Game ${SRC}
#	../../../../buildscript ${DSMFLAGS} -o GameDSM ${SRC}

clean:
	rm tmp1RainForest.java
	rm tmpRainForest.java
	rm -rf tmpbuilddirectory
	rm *.bin
