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

FLAGS1=-dsm -transstats -optimize -mainclass ${MAINCLASS} -32bit
FLAGS2=-dsm -transstats -dsmcaching -optimize -mainclass ${MAINCLASS} -32bit
FLAGS3=-dsm -dsmcaching -transstats -prefetch -optimize -mainclass ${MAINCLASS} -excprefetch BarrierServer.updateAge -excprefetch RainForest.main -excprefetch GameMap.hasRock -excprefetch GameMap.hasTree -trueprob 0.90 -32bit

FLAGSNPNC=-dsm -optimize -mainclass ${MAINCLASS} -32bit
FLAGSNPC=-dsm -dsmcaching -optimize -mainclass ${MAINCLASS} -32bit
FLAGSP=-dsm -dsmcaching -prefetch -optimize -mainclass ${MAINCLASS} -excprefetch BarrierServer.updateAge -excprefetch RainForest.main -excprefetch GameMap.hasRock -excprefetch GameMap.hasTree -trueprob 0.90 -32bit


default:
	cpp ${MAINCLASS}.java > tmp1${MAINCLASS}.java
	./extractLines
	../../../../buildscript ${FLAGS1} -o ${MAINCLASS}withstatNPNC ${SRC}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}withstatNPC ${SRC}
	../../../../buildscript ${FLAGS3} -o ${MAINCLASS}withstatN ${SRC}
	../../../../buildscript ${FLAGSNPNC} -o ${MAINCLASS}NPNC ${SRC}
	../../../../buildscript ${FLAGSNPC} -o ${MAINCLASS}NPC ${SRC}
	../../../../buildscript ${FLAGSP} -o ${MAINCLASS}N ${SRC}

clean:
	rm tmp1RainForest.java
	rm tmpRainForest.java
	rm -rf tmpbuilddirectory
	rm *.bin
