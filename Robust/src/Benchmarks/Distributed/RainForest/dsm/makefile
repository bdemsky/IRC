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

FLAGS1=-dsm -optimize -mainclass ${MAINCLASS}
FLAGS2=-dsm -dsmcaching -optimize -mainclass ${MAINCLASS}
FLAGS3=-dsm -dsmcaching -prefetch -optimize -mainclass ${MAINCLASS} -trueprob 0.90

default:
	cpp ${MAINCLASS}.java > tmp1${MAINCLASS}.java
	./extractLines
	../../../../buildscript ${FLAGS1} -o ${MAINCLASS}NPNC ${SRC}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPC ${SRC1}
	../../../../buildscript ${FLAGS3} -o ${MAINCLASS}N ${SRC}

clean:
	rm tmp1RainForest.java
	rm tmpRainForest.java
	rm -rf tmpbuilddirectory
	rm *.bin
